package com.caioalexnes.sikey

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.DSAPublicKey
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey

/**
 * Lê assinatura e arquivos de um app instalado ou de um .apk solto.
 *
 * Duas coisas diferentes recebem o nome "hash do app", e o app mostra as duas
 * porque elas respondem a perguntas diferentes:
 *
 *  - hash do CERTIFICADO: identifica QUEM assinou. Não muda quando o app é
 *    atualizado. É o número que o Play Console, o Firebase e o assetlinks.json
 *    pedem, e o único que serve para dizer "este APK veio de quem eu espero".
 *  - hash do ARQUIVO .apk: identifica AQUELE arquivo, byte a byte. Muda a cada
 *    versão. Serve para conferir um download contra o valor publicado.
 *
 * Tudo aqui é síncrono e lê arquivo: chamar só fora da thread principal.
 */
object Inspector {

    /** Só o necessário para desenhar a lista; nada de ler o APK. */
    data class ListEntry(
        val label: String,
        val packageName: String,
        val isSystem: Boolean,
    )

    fun listInstalled(context: Context): List<ListEntry> {
        val pm = context.packageManager
        return installedApplications(pm)
            .map { info ->
                ListEntry(
                    label = info.loadLabel(pm).toString(),
                    packageName = info.packageName,
                    isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                        (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    @Suppress("DEPRECATION")
    private fun installedApplications(pm: PackageManager): List<ApplicationInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
        } else {
            pm.getInstalledApplications(0)
        }

    @Suppress("DEPRECATION")
    private fun packageInfo(pm: PackageManager, packageName: String): PackageInfo {
        val flags = signatureFlags()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            pm.getPackageInfo(packageName, flags)
        }
    }

    @Suppress("DEPRECATION")
    private fun signatureFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }

    // ------------------------------------------------------------------
    // App instalado
    // ------------------------------------------------------------------

    fun inspectInstalled(context: Context, packageName: String): Inspection {
        val pm = context.packageManager
        val info = packageInfo(pm, packageName)
        val appInfo = info.applicationInfo

        val files = buildList {
            appInfo?.sourceDir?.let { add(hashApkPath(it)) }
            appInfo?.splitSourceDirs?.filterNotNull()?.forEach { add(hashApkPath(it)) }
        }

        return Inspection(
            label = appInfo?.loadLabel(pm)?.toString() ?: packageName,
            packageName = packageName,
            versionName = info.versionName,
            versionCode = versionCodeOf(info),
            icon = appInfo?.loadIcon(pm),
            certs = certsOf(info),
            files = files,
            warnings = emptyList(),
        )
    }

    // ------------------------------------------------------------------
    // Arquivo .apk
    // ------------------------------------------------------------------

    /**
     * O PackageManager só sabe abrir um APK por CAMINHO, e um content:// não
     * tem caminho. Por isso o arquivo é copiado para o cache antes de ser lido,
     * e apagado no fim — a cópia existe só durante a inspeção.
     */
    fun inspectApkFile(context: Context, uri: Uri): Inspection {
        val displayName = queryDisplayName(context, uri) ?: uri.lastPathSegment ?: "arquivo.apk"
        val direct = uri.takeIf { it.scheme == "file" }?.path?.let(::File)?.takeIf { it.canRead() }
        val temp = if (direct == null) File.createTempFile("inspect", ".apk", context.cacheDir) else null
        val target = direct ?: temp!!

        try {
            if (temp != null) {
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw IOException(context.getString(R.string.error_open_stream))
                input.use { source ->
                    temp.outputStream().use { sink -> source.copyTo(sink, 64 * 1024) }
                }
            }

            val fileInfo = ApkFileInfo(
                name = displayName,
                path = direct?.absolutePath ?: uri.toString(),
                size = target.length(),
                hashes = Hashing.ofStream(FileInputStream(target)),
                error = null,
            )

            val info = archiveInfo(context.packageManager, target.absolutePath)
                ?: return Inspection(
                    label = displayName,
                    packageName = "",
                    versionName = null,
                    versionCode = 0L,
                    icon = null,
                    certs = emptyList(),
                    files = listOf(fileInfo),
                    warnings = listOf(context.getString(R.string.warning_not_apk)),
                )

            // getPackageArchiveInfo não preenche estes dois campos, e sem eles
            // loadIcon e loadLabel não conseguem abrir os recursos do arquivo.
            val appInfo = info.applicationInfo?.apply {
                sourceDir = target.absolutePath
                publicSourceDir = target.absolutePath
            }

            return Inspection(
                label = appInfo?.loadLabel(context.packageManager)?.toString() ?: displayName,
                packageName = info.packageName,
                versionName = info.versionName,
                versionCode = versionCodeOf(info),
                icon = runCatching { appInfo?.loadIcon(context.packageManager) }.getOrNull(),
                certs = certsOf(info),
                files = listOf(fileInfo),
                warnings = emptyList(),
            )
        } finally {
            temp?.delete()
        }
    }

    @Suppress("DEPRECATION")
    private fun archiveInfo(pm: PackageManager, path: String): PackageInfo? {
        val flags = signatureFlags()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(path, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            pm.getPackageArchiveInfo(path, flags)
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? =
        runCatching {
            context.contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()

    @Suppress("DEPRECATION")
    private fun versionCodeOf(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
        else info.versionCode.toLong()

    private fun hashApkPath(path: String): ApkFileInfo {
        val file = File(path)
        return try {
            ApkFileInfo(
                name = file.name,
                path = path,
                size = file.length(),
                hashes = Hashing.ofStream(FileInputStream(file)),
                error = null,
            )
        } catch (e: Exception) {
            // Ler o base.apk de OUTRO app depende da versão do Android. Quando
            // não dá, os hashes do certificado continuam valendo: eles vêm do
            // PackageManager, não do arquivo.
            ApkFileInfo(
                name = file.name,
                path = path,
                size = file.length(),
                hashes = null,
                error = e.message ?: e.javaClass.simpleName,
            )
        }
    }

    // ------------------------------------------------------------------
    // Certificados
    // ------------------------------------------------------------------

    @Suppress("DEPRECATION")
    private fun certsOf(info: PackageInfo): List<CertInfo> {
        val signingInfo =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.signingInfo else null

        val current: List<Signature>
        val all: List<Signature>
        if (signingInfo != null) {
            current = signingInfo.apkContentsSigners?.filterNotNull().orEmpty()
            all = if (signingInfo.hasMultipleSigners()) {
                current
            } else {
                // O histórico existe quando a chave foi rotacionada (esquema de
                // assinatura v3). A ordem dele NÃO é garantida pela documentação,
                // então quem é o atual se decide comparando com apkContentsSigners,
                // nunca por posição na lista.
                signingInfo.signingCertificateHistory?.filterNotNull()
                    ?.takeIf { it.isNotEmpty() } ?: current
            }
        } else {
            current = info.signatures?.filterNotNull().orEmpty()
            all = current
        }

        val currentDigests = current.map { sha256(it.toByteArray()) }.toSet()
        val signerCount = current.size

        // Atuais primeiro, rotacionados no fim: é onde o leitor espera achar
        // "o que já foi" numa lista.
        val ordered = all.sortedBy { if (sha256(it.toByteArray()) in currentDigests) 0 else 1 }

        var signerIndex = 0
        var previousIndex = 0
        return ordered.map { signature ->
            val isPrevious = sha256(signature.toByteArray()) !in currentDigests
            if (isPrevious) previousIndex++ else signerIndex++
            describe(
                signature = signature,
                signerIndex = signerIndex,
                signerCount = signerCount,
                isPrevious = isPrevious,
                previousIndex = previousIndex,
            )
        }
    }

    private fun sha256(bytes: ByteArray): String =
        Hashing.hex(MessageDigest.getInstance("SHA-256").digest(bytes))

    private fun describe(
        signature: Signature,
        signerIndex: Int,
        signerCount: Int,
        isPrevious: Boolean,
        previousIndex: Int,
    ): CertInfo {
        val der = signature.toByteArray()
        val cert = runCatching {
            CertificateFactory.getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(der)) as X509Certificate
        }.getOrNull()

        return CertInfo(
            signerIndex = signerIndex,
            signerCount = signerCount,
            isPrevious = isPrevious,
            previousIndex = previousIndex,
            subject = cert?.subjectX500Principal?.name.orEmpty(),
            issuer = cert?.issuerX500Principal?.name.orEmpty(),
            validFrom = cert?.notBefore,
            validUntil = cert?.notAfter,
            serial = cert?.serialNumber?.toString(16)?.uppercase().orEmpty(),
            keyDescription = cert?.let(::describeKey).orEmpty(),
            // O hash do certificado é o do DER inteiro — exatamente o que
            // apksigner e keytool imprimem. Não é hash só da chave pública.
            hashes = Hashing.of(der),
        )
    }

    private fun describeKey(cert: X509Certificate): String {
        val key = cert.publicKey
        val bits = when (key) {
            is RSAPublicKey -> key.modulus.bitLength()
            is ECPublicKey -> key.params?.order?.bitLength()
            is DSAPublicKey -> key.params?.p?.bitLength()
            else -> null
        }
        return buildString {
            append(key.algorithm ?: "?")
            if (bits != null) append(" $bits bits")
            cert.sigAlgName?.takeIf { it.isNotEmpty() }?.let { append(", $it") }
        }
    }
}
