package com.caioalexnes.sikey

import android.graphics.drawable.Drawable
import java.util.Date

/** Um certificado X.509 que assinou o pacote, já com os hashes calculados. */
data class CertInfo(
    /** 1-based. Só é diferente de 1 quando o APK tem vários assinantes. */
    val signerIndex: Int,
    val signerCount: Int,
    /**
     * false quando este é o certificado com que o APK está assinado HOJE.
     * true quando é um certificado antigo que o app comprova ter usado antes
     * (rotação de chave do esquema de assinatura v3).
     */
    val isPrevious: Boolean,
    val previousIndex: Int,
    val subject: String,
    val issuer: String,
    val validFrom: Date?,
    val validUntil: Date?,
    val serial: String,
    val keyDescription: String,
    val hashes: Hashes,
)

/** Um arquivo do pacote: o base.apk e, se houver, cada split. */
data class ApkFileInfo(
    val name: String,
    val path: String,
    val size: Long,
    val hashes: Hashes?,
    /** Preenchido quando o arquivo existe mas não pôde ser lido. */
    val error: String?,
)

/** Tudo que a tela de detalhes mostra sobre um app ou um arquivo APK. */
data class Inspection(
    val label: String,
    val packageName: String,
    val versionName: String?,
    val versionCode: Long,
    val icon: Drawable?,
    val certs: List<CertInfo>,
    val files: List<ApkFileInfo>,
    /** Problemas que não impedem mostrar o resto. */
    val warnings: List<String>,
) {
    /**
     * Todo hash desta inspeção, em forma canônica, para a caixa de comparação.
     * O valor do mapa é o rótulo que aparece quando bate.
     *
     * Os rótulos vêm de fora porque este arquivo não tem Context e as telas
     * são traduzidas.
     */
    fun hashIndex(
        certLabel: (CertInfo) -> String,
        fileLabel: (ApkFileInfo) -> String,
    ): Map<String, String> {
        val index = LinkedHashMap<String, String>()
        certs.forEach { cert ->
            cert.hashes.forEach { (alg, value) ->
                index.putIfAbsent(value, "${certLabel(cert)} · ${alg.label}")
            }
        }
        files.forEach { file ->
            file.hashes?.forEach { (alg, value) ->
                index.putIfAbsent(value, "${fileLabel(file)} · ${alg.label}")
            }
        }
        return index
    }
}
