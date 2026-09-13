package com.caioalexnes.sikey

import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.caioalexnes.sikey.databinding.ItemAppBinding
import java.util.concurrent.Executors

/**
 * Carrega ícone de app fora da thread principal.
 *
 * loadIcon abre o APK do outro app e decodifica um PNG. Fazer isso durante o
 * bind trava a rolagem já com poucas dezenas de apps, e um aparelho comum tem
 * centenas.
 */
private object IconLoader {
    private val cache = LruCache<String, Drawable>(300)
    private val executor = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())

    fun load(view: ImageView, packageName: String) {
        // A tag é o que impede o ícone de aparecer na linha errada: quando o
        // carregamento termina, a view já pode ter sido reciclada para outro app.
        view.tag = packageName

        cache.get(packageName)?.let {
            view.setImageDrawable(it)
            return
        }

        view.setImageDrawable(null)
        val pm = view.context.applicationContext.packageManager
        executor.execute {
            val icon = runCatching { pm.getApplicationIcon(packageName) }.getOrNull() ?: return@execute
            main.post {
                cache.put(packageName, icon)
                if (view.tag == packageName) view.setImageDrawable(icon)
            }
        }
    }
}

class AppListAdapter(
    private val onClick: (Inspector.ListEntry) -> Unit,
) : RecyclerView.Adapter<AppListAdapter.ViewHolder>() {

    private var items: List<Inspector.ListEntry> = emptyList()

    fun submit(newItems: List<Inspector.ListEntry>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.appLabel.text = item.label
        holder.binding.appPackage.text = item.packageName
        holder.binding.root.setOnClickListener { onClick(item) }
        IconLoader.load(holder.binding.appIcon, item.packageName)
    }

    class ViewHolder(val binding: ItemAppBinding) : RecyclerView.ViewHolder(binding.root)
}
