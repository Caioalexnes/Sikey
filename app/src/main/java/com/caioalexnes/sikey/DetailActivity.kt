package com.caioalexnes.sikey

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.Formatter
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.caioalexnes.sikey.databinding.ActivityDetailBinding
import com.caioalexnes.sikey.databinding.ItemHashBinding
import com.caioalexnes.sikey.databinding.ItemInfoBinding
import com.caioalexnes.sikey.databinding.ItemSubtitleBinding
import com.caioalexnes.sikey.databinding.SectionCardBinding
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors

class DetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailBinding
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val dateFormat: DateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM)

    private var inspection: Inspection? = null
    private var hashIndex: Map<String, String> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.scrollContent.applySystemInsets(baseHorizontalDp = 16, baseBottomDp = 16)

        binding.expectedInput.doAfterTextChanged { text -> compare(text?.toString().orEmpty()) }

        val packageName = intent.getStringExtra(EXTRA_PACKAGE)
        val uri = intent.data

        when {
            packageName != null -> inspect { Inspector.inspectInstalled(this, packageName) }
            uri != null -> inspect { Inspector.inspectApkFile(this, uri) }
            else -> showError(getString(R.string.error_package_not_found, "?"))
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_detail, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_copy_all -> {
            report()?.let { copy(getString(R.string.copy_all), it) }
            true
        }

        R.id.action_share -> {
            report()?.let { text ->
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                startActivity(Intent.createChooser(send, getString(R.string.share)))
            }
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    private fun inspect(block: () -> Inspection) {
        binding.progress.visibility = View.VISIBLE
        worker.execute {
            val result = runCatching(block)
            main.post {
                if (isFinishing || isDestroyed) return@post
                binding.progress.visibility = View.GONE
                result
                    .onSuccess { render(it) }
                    .onFailure { showError(it.message ?: it.javaClass.simpleName) }
            }
        }
    }

    private fun showError(message: String) {
        binding.errorText.text = message
        binding.errorText.visibility = View.VISIBLE
    }

    // ------------------------------------------------------------------
    // Desenho
    // ------------------------------------------------------------------

    private fun render(result: Inspection) {
        inspection = result
        hashIndex = result.hashIndex(::certLabel, ApkFileInfo::name)

        binding.toolbar.title = result.label
        binding.appLabel.text = result.label
        binding.appPackage.text = result.packageName.ifEmpty { result.files.firstOrNull()?.name.orEmpty() }
        binding.appVersion.text =
            result.versionName?.let { getString(R.string.version_format, it, result.versionCode) }.orEmpty()
        binding.appVersion.visibility = if (binding.appVersion.text.isEmpty()) View.GONE else View.VISIBLE
        result.icon?.let { binding.appIcon.setImageDrawable(it) }

        result.warnings.forEach { showError(it) }

        renderCertificates(result)
        renderFiles(result)
    }

    private fun renderCertificates(result: Inspection) {
        val section = addSection(getString(R.string.section_signing_cert))
        if (result.certs.isEmpty()) {
            addInfo(section, "", getString(R.string.no_certificates))
            return
        }
        result.certs.forEach { cert ->
            addSubtitle(section, certLabel(cert))
            cert.hashes.forEach { (alg, value) ->
                // Certificado sai no formato com dois-pontos, que é o que o
                // Play Console, o keytool e o assetlinks.json usam.
                addHash(section, alg.label, Hashing.colonHex(value), Hashing.colonHex(value))
            }
            if (cert.subject.isNotEmpty()) addInfo(section, getString(R.string.cert_subject), cert.subject)
            if (cert.issuer.isNotEmpty()) addInfo(section, getString(R.string.cert_issuer), cert.issuer)
            validity(cert.validFrom, cert.validUntil)?.let {
                addInfo(section, getString(R.string.cert_validity), it)
            }
            if (cert.keyDescription.isNotEmpty()) {
                addInfo(section, getString(R.string.cert_key), cert.keyDescription)
            }
            if (cert.serial.isNotEmpty()) addInfo(section, getString(R.string.cert_serial), cert.serial)
        }
    }

    private fun renderFiles(result: Inspection) {
        val section = addSection(getString(R.string.section_apk_file))
        if (result.files.isEmpty()) {
            addInfo(section, "", getString(R.string.no_apk_files))
            return
        }
        result.files.forEach { file ->
            addSubtitle(section, file.name)
            // Hash de arquivo sai em hexadecimal puro e minúsculo, que é o
            // formato de sha256sum e de todo site que publica checksum.
            file.hashes?.forEach { (alg, value) -> addHash(section, alg.label, value, value) }
            file.error?.let { addInfo(section, "", it) }
            addInfo(section, getString(R.string.file_size), Formatter.formatShortFileSize(this, file.size))
            addInfo(section, getString(R.string.file_path), file.path)
        }
    }

    private fun validity(from: Date?, until: Date?): String? {
        if (from == null || until == null) return null
        return getString(R.string.cert_validity_range, dateFormat.format(from), dateFormat.format(until))
    }

    private fun certLabel(cert: CertInfo): String = when {
        cert.isPrevious -> getString(R.string.cert_previous, cert.previousIndex)
        cert.signerCount > 1 -> getString(R.string.signer_n, cert.signerIndex)
        else -> getString(R.string.cert_current)
    }

    private fun addSection(title: String): ViewGroup {
        val card = SectionCardBinding.inflate(layoutInflater, binding.sections, false)
        card.sectionTitle.text = title
        binding.sections.addView(card.root)
        return card.sectionContent
    }

    private fun addSubtitle(parent: ViewGroup, text: String) {
        val view = ItemSubtitleBinding.inflate(layoutInflater, parent, false)
        view.root.text = text
        parent.addView(view.root)
    }

    private fun addInfo(parent: ViewGroup, label: String, value: String) {
        val view = ItemInfoBinding.inflate(layoutInflater, parent, false)
        view.infoLabel.text = label
        view.infoLabel.visibility = if (label.isEmpty()) View.GONE else View.VISIBLE
        view.infoValue.text = value
        parent.addView(view.root)
    }

    private fun addHash(parent: ViewGroup, label: String, shown: String, toCopy: String) {
        val view = ItemHashBinding.inflate(layoutInflater, parent, false)
        view.hashLabel.text = label
        view.hashValue.text = shown
        view.copyButton.setOnClickListener { copy(label, toCopy) }
        parent.addView(view.root)
    }

    // ------------------------------------------------------------------
    // Comparação
    // ------------------------------------------------------------------

    /**
     * O ponto do app: colar o valor esperado e ver se ele existe aqui.
     *
     * A comparação é feita na forma canônica, então tanto faz colar com
     * dois-pontos, com espaços ou em maiúsculas — que é como o valor chega
     * quando vem de um site, de um e-mail ou do Play Console.
     */
    private fun compare(input: String) {
        val normalized = Hashing.normalize(input)
        if (normalized.isEmpty()) {
            binding.compareResult.visibility = View.GONE
            return
        }

        val match = hashIndex[normalized]
        binding.compareResult.visibility = View.VISIBLE
        if (match != null) {
            binding.compareResult.text = getString(R.string.compare_match, match)
            binding.compareResult.setTextColor(getColor(R.color.hash_match))
        } else {
            binding.compareResult.text = getString(R.string.compare_no_match)
            binding.compareResult.setTextColor(getColor(R.color.hash_mismatch))
        }
    }

    // ------------------------------------------------------------------
    // Copiar e compartilhar
    // ------------------------------------------------------------------

    private fun copy(label: String, value: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
        // Android 13+ mostra a própria confirmação de cópia; um Toast nosso
        // seria um segundo aviso dizendo a mesma coisa.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, getString(R.string.copied, label), Toast.LENGTH_SHORT).show()
        }
    }

    private fun report(): String? {
        val result = inspection ?: return null
        return buildString {
            appendLine(result.label)
            if (result.packageName.isNotEmpty()) {
                appendLine(getString(R.string.report_package, result.packageName))
            }
            result.versionName?.let { appendLine(getString(R.string.version_format, it, result.versionCode)) }

            if (result.certs.isNotEmpty()) {
                appendLine()
                appendLine(getString(R.string.section_signing_cert))
                result.certs.forEach { cert ->
                    appendLine("  ${certLabel(cert)}")
                    cert.hashes.forEach { (alg, value) ->
                        appendLine("    ${alg.label}: ${Hashing.colonHex(value)}")
                    }
                }
            }

            if (result.files.isNotEmpty()) {
                appendLine()
                appendLine(getString(R.string.section_apk_file))
                result.files.forEach { file ->
                    appendLine("  ${file.name}")
                    file.hashes?.forEach { (alg, value) -> appendLine("    ${alg.label}: $value") }
                    file.error?.let { appendLine("    $it") }
                }
            }

            appendLine()
            append(getString(R.string.report_footer))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        worker.shutdownNow()
    }

    companion object {
        private const val EXTRA_PACKAGE = "package"

        fun intentForPackage(context: Context, packageName: String): Intent =
            Intent(context, DetailActivity::class.java).putExtra(EXTRA_PACKAGE, packageName)

        fun intentForFile(context: Context, uri: Uri): Intent =
            Intent(context, DetailActivity::class.java)
                .setData(uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
