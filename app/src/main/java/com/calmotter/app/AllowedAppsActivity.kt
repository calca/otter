package com.calmotter.app

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lista di app extra consentite durante una pausa.
 *
 * Raggiungibile solo dopo verifica password in MainActivity.
 * Le modifiche vengono salvate immediatamente al toggle di ogni checkbox,
 * senza bisogno di un pulsante Salva esplicito.
 *
 * Il caricamento delle app (icone incluse) avviene su un thread IO per
 * non bloccare la UI — su telefoni con molte app può richiedere qualche
 * secondo.
 */
class AllowedAppsActivity : BaseActivity() {

    override val themeVariant = ThemeVariant.WITH_ACTION_BAR

    private lateinit var allowedAppsManager: AllowedAppsManager
    private lateinit var adapter: AppsAdapter
    private var allApps: List<AppItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_allowed_apps)

        supportActionBar?.apply {
            title = getString(R.string.allowed_apps_title)
            setDisplayHomeAsUpEnabled(true)
        }

        allowedAppsManager = AllowedAppsManager(applicationContext)

        val recyclerView = findViewById<RecyclerView>(R.id.appsList)
        val spinner     = findViewById<ProgressBar>(R.id.loadingSpinner)
        val searchField = findViewById<TextInputEditText>(R.id.searchField)

        adapter = AppsAdapter { item, isChecked ->
            // Salvataggio immediato ad ogni toggle
            val current = allowedAppsManager.getAllowedPackages().toMutableSet()
            if (isChecked) current.add(item.packageName) else current.remove(item.packageName)
            allowedAppsManager.setAllowedPackages(current)
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        recyclerView.itemAnimator = null // evita animazioni ridondanti durante il filtro

        searchField.addTextChangedListener { text ->
            val query = text?.toString()?.trim() ?: ""
            adapter.filter(allApps, query)
        }

        // Caricamento asincrono su thread IO
        lifecycleScope.launch {
            val allowed = allowedAppsManager.getAllowedPackages()
            val apps = withContext(Dispatchers.IO) { loadApps(allowed) }

            allApps = apps
            adapter.filter(apps, "")

            spinner.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }

    // ── Caricamento app ───────────────────────────────────────────────────

    private fun loadApps(allowed: Set<String>): List<AppItem> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager
            .queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .map { it.activityInfo }
            .filter { it.packageName != packageName }
            .distinctBy { it.packageName }
            .sortedWith(compareBy(
                { it.packageName !in allowed }, // consentite in cima
                { it.loadLabel(packageManager).toString().lowercase() }
            ))
            .map { info ->
                AppItem(
                    label       = info.loadLabel(packageManager).toString(),
                    packageName = info.packageName,
                    icon        = info.loadIcon(packageManager),
                    isAllowed   = info.packageName in allowed
                )
            }
    }

    // ── Data class ────────────────────────────────────────────────────────

    data class AppItem(
        val label: String,
        val packageName: String,
        val icon: Drawable,
        var isAllowed: Boolean
    )

    // ── Adapter ───────────────────────────────────────────────────────────

    inner class AppsAdapter(
        private val onToggle: (AppItem, Boolean) -> Unit
    ) : RecyclerView.Adapter<AppsAdapter.ViewHolder>() {

        private var items: List<AppItem> = emptyList()

        fun filter(source: List<AppItem>, query: String) {
            items = if (query.isEmpty()) source
                    else source.filter { it.label.contains(query, ignoreCase = true) }
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = layoutInflater.inflate(R.layout.item_app, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount() = items.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val icon     = view.findViewById<ImageView>(R.id.appIcon)
            private val name     = view.findViewById<TextView>(R.id.appName)
            private val checkbox = view.findViewById<MaterialCheckBox>(R.id.appCheckbox)

            fun bind(item: AppItem) {
                icon.setImageDrawable(item.icon)
                name.text = item.label
                checkbox.isChecked = item.isAllowed

                itemView.setOnClickListener {
                    item.isAllowed = !item.isAllowed
                    checkbox.isChecked = item.isAllowed
                    onToggle(item, item.isAllowed)
                }
            }
        }
    }
}
