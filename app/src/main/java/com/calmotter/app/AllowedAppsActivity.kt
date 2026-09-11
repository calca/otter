package com.calmotter.app

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.lifecycleScope
import com.calmotter.app.ui.screens.AllowedAppsScreen
import com.calmotter.app.ui.screens.AppItem
import com.calmotter.app.ui.theme.CalmOtterTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lista di app extra consentite durante una pausa.
 *
 * Raggiungibile solo dopo verifica password in MainActivity.
 * Le modifiche vengono salvate immediatamente al toggle di ogni riga,
 * senza bisogno di un pulsante Salva esplicito.
 *
 * Il caricamento delle app (icone incluse, convertite qui in Bitmap — vedi
 * AppItem in AllowedAppsScreen.kt) avviene su un thread IO per non
 * bloccare la UI — su telefoni con molte app può richiedere qualche
 * secondo.
 */
class AllowedAppsActivity : BaseActivity() {

    override val themeVariant = ThemeVariant.WITH_ACTION_BAR

    private lateinit var allowedAppsManager: AllowedAppsManager

    private var isLoading by mutableStateOf(true)
    private var allApps by mutableStateOf<List<AppItem>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.apply {
            title = getString(R.string.allowed_apps_title)
            setDisplayHomeAsUpEnabled(true)
        }

        allowedAppsManager = AllowedAppsManager.getInstance(applicationContext)

        setContent {
            CalmOtterTheme(appTheme = ThemeManager.getTheme(this)) {
                AllowedAppsScreen(
                    isLoading = isLoading,
                    apps = allApps,
                    onToggle = ::toggleApp,
                )
            }
        }

        // Caricamento asincrono su thread IO
        lifecycleScope.launch {
            val allowed = allowedAppsManager.getAllowedPackages()
            val apps = withContext(Dispatchers.IO) { loadApps(allowed) }

            allApps = apps
            isLoading = false
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }

    /**
     * Salvataggio immediato ad ogni toggle, e aggiornamento della lista in
     * memoria (nuova lista con l'item copiato/aggiornato, dato che AppItem è
     * immutabile) così la riga si ridisegna con lo stato corretto.
     */
    private fun toggleApp(item: AppItem) {
        val newIsAllowed = !item.isAllowed

        val current = allowedAppsManager.getAllowedPackages().toMutableSet()
        if (newIsAllowed) current.add(item.packageName) else current.remove(item.packageName)
        allowedAppsManager.setAllowedPackages(current)

        allApps = allApps.map {
            if (it.packageName == item.packageName) it.copy(isAllowed = newIsAllowed) else it
        }
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
                    icon        = info.loadIcon(packageManager).toBitmap(),
                    isAllowed   = info.packageName in allowed
                )
            }
    }
}
