package com.caioalexnes.sikey

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.caioalexnes.sikey.databinding.ActivityMainBinding
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: AppListAdapter

    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /** Lista completa, como veio do sistema. O filtro nunca altera isto. */
    private var allApps: List<Inspector.ListEntry> = emptyList()
    private var showSystemApps = false
    private var query = ""

    private val openApk = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { openDetail(DetailActivity.intentForFile(this, it)) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        showSystemApps = prefs().getBoolean(KEY_SHOW_SYSTEM, false)

        adapter = AppListAdapter { entry ->
            openDetail(DetailActivity.intentForPackage(this, entry.packageName))
        }
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter
        binding.recycler.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
        binding.recycler.applySystemInsets(baseBottomDp = 16)

        binding.searchInput.doAfterTextChanged { text ->
            query = text?.toString().orEmpty()
            applyFilter()
        }

        loadApps()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        menu.findItem(R.id.action_show_system).isChecked = showSystemApps
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_show_system -> {
            showSystemApps = !showSystemApps
            item.isChecked = showSystemApps
            prefs().edit().putBoolean(KEY_SHOW_SYSTEM, showSystemApps).apply()
            applyFilter()
            true
        }

        R.id.action_refresh -> {
            loadApps()
            true
        }

        R.id.action_open_apk -> {
            pickApkFile()
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    private fun pickApkFile() {
        // Nem todo gerenciador de arquivos declara o mime de APK, então o
        // filtro aceita qualquer coisa e a validação fica na tela de detalhes.
        val types = arrayOf("application/vnd.android.package-archive", "application/octet-stream", "*/*")
        try {
            openApk.launch(types)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.error_no_file_picker, Toast.LENGTH_LONG).show()
        }
    }

    private fun openDetail(intent: Intent) = startActivity(intent)

    private fun loadApps() {
        binding.progress.visibility = View.VISIBLE
        binding.emptyText.visibility = View.GONE
        worker.execute {
            val apps = runCatching { Inspector.listInstalled(this) }.getOrDefault(emptyList())
            main.post {
                if (isFinishing || isDestroyed) return@post
                allApps = apps
                binding.progress.visibility = View.GONE
                applyFilter()
            }
        }
    }

    private fun applyFilter() {
        val needle = query.trim().lowercase(Locale.getDefault())
        val filtered = allApps.filter { entry ->
            (showSystemApps || !entry.isSystem) &&
                (
                    needle.isEmpty() ||
                        entry.label.lowercase(Locale.getDefault()).contains(needle) ||
                        entry.packageName.lowercase(Locale.getDefault()).contains(needle)
                    )
        }
        adapter.submit(filtered)
        binding.countText.text = resources.getQuantityString(R.plurals.apps_count, filtered.size, filtered.size)
        binding.emptyText.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun prefs() = getSharedPreferences("sikey", Context.MODE_PRIVATE)

    override fun onDestroy() {
        super.onDestroy()
        worker.shutdownNow()
    }

    private companion object {
        const val KEY_SHOW_SYSTEM = "show_system_apps"
    }
}
