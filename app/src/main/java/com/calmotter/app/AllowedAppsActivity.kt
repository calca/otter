package com.calmotter.app

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lista di app extra consentite durante una pausa, oltre al telefono.
 *
 * Il dialer di default non compare in questa lista (vedi [loadApps]):
 * resta sempre raggiungibile indipendentemente da questa whitelist (vedi
 * AllowedAppLaunchItems.kt/AppBlockerAccessibilityService), quindi
 * mostrarlo con una checkbox deselezionabile sarebbe fuorviante — sembrerebbe
 * un'app che l'utente deve ricordarsi di abilitare.
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
    private var loadJob: Job? = null

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
                    onRefresh = { refreshApps(showConfirmation = true) },
                )
            }
        }

        refreshApps(showConfirmation = false)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }

    /**
     * Ricarica l'elenco delle app dal PackageManager (thread IO, come il
     * caricamento iniziale). Chiamata sia da onCreate() sia dal
     * pull-to-refresh in AllowedAppsScreen — un'app installata o
     * disinstallata mentre questa schermata era già aperta non
     * comparirebbe/scomparirebbe altrimenti finché non la si riapre.
     * [showConfirmation] evita un Toast ridondante al primissimo caricamento.
     */
    private fun refreshApps(showConfirmation: Boolean) {
        loadJob?.cancel()
        isLoading = true
        loadJob = lifecycleScope.launch {
            val allowed = allowedAppsManager.getAllowedPackages()
            val apps = withContext(Dispatchers.IO) { loadApps(allowed) }

            allApps = apps
            isLoading = false
            if (showConfirmation) {
                Toast.makeText(this@AllowedAppsActivity, R.string.allowed_apps_saved, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Salvataggio immediato ad ogni toggle, e aggiornamento della lista in
     * memoria (nuova lista con l'item copiato/aggiornato, dato che AppItem è
     * immutabile) così la riga si ridisegna con lo stato corretto.
     *
     * Tetto di [AllowedAppsManager.MAX_ALLOWED_APPS]: tentare di aggiungerne
     * una in più non fa nulla (a parte il Toast) invece di accettarla e
     * troncare altrove — così l'elenco mostrato qui resta sempre coerente
     * con quello che finisce effettivamente in whitelist.
     */
    private fun toggleApp(item: AppItem) {
        val newIsAllowed = !item.isAllowed

        val current = allowedAppsManager.getAllowedPackages().toMutableSet()
        if (newIsAllowed) {
            if (current.size >= AllowedAppsManager.MAX_ALLOWED_APPS) {
                Toast.makeText(
                    this,
                    getString(R.string.allowed_apps_limit_reached, AllowedAppsManager.MAX_ALLOWED_APPS),
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
            current.add(item.packageName)
        } else {
            current.remove(item.packageName)
        }
        allowedAppsManager.setAllowedPackages(current)

        allApps = allApps.map {
            if (it.packageName == item.packageName) it.copy(isAllowed = newIsAllowed) else it
        }
    }

    // ── Caricamento app ───────────────────────────────────────────────────

    private fun loadApps(allowed: Set<String>): List<AppItem> {
        // Il dialer di default è sempre consentito (vedi
        // AllowedAppLaunchItems.kt/AppBlockerAccessibilityService), a parte
        // dal tetto di MAX_ALLOWED_APPS: mostrarlo qui con una checkbox
        // vuota farebbe pensare all'utente che vada selezionato esplicitamente
        // per restare raggiungibile durante la pausa, il che non è vero.
        //
        // Calm Otter stessa è esclusa allo stesso modo — ma per entrambi i
        // flavor, non solo quello in esecuzione: `channel` (vedi CLAUDE.md)
        // esiste apposta perché beta e stable stiano installate fianco a
        // fianco sullo stesso dispositivo, quindi filtrare solo `packageName`
        // lasciava passare l'ALTRO flavor come se fosse un'app qualunque
        // (segnalato: "nella lista deve essere escluso Calm Otter, è sempre
        // abilitato come il phone" — visto proprio con beta e stable
        // installate insieme). `removeSuffix(".beta")` funziona per entrambe
        // le direzioni: da beta risale a "com.calmotter.app" e filtra i due
        // applicationId; da stable non ha nulla da togliere e filtra la
        // coppia comunque.
        val ownBasePackage = packageName.removeSuffix(".beta")
        val dialerPackage = dialerPackageName(this)
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager
            .queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .map { it.activityInfo }
            .filter {
                it.packageName != ownBasePackage &&
                    it.packageName != "$ownBasePackage.beta" &&
                    it.packageName != dialerPackage
            }
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
