package com.calmotter.app

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.calmotter.app.ui.screens.SettingsScreen
import com.calmotter.app.ui.theme.CalmOtterTheme

// Repo pubblico su GitHub — unica fonte per link "codice sorgente",
// "licenza" (file LICENSE nello stesso repo) e "sviluppatore" in Settings.
private const val GITHUB_REPO_URL = "https://github.com/calca/otter"
private const val GITHUB_DEVELOPER_URL = "https://github.com/calca"

/**
 * Configurazione: personalizzazione (palette), stato di accessibilità/DND/
 * Home predefinita (spostati qui dalla Home — vedi MainScreen.kt — perché
 * non bloccano l'avvio di una pausa, solo la sua applicazione più rigorosa,
 * e controllarli ogni volta che si apre l'app era percepito come fastidioso,
 * vedi home-and-settings/requirements.md), le due azioni protette da
 * password (cambio password, elenco app consentite), le preferenze non
 * critiche, e una sezione "Info" con link esterni (repo GitHub, licenza,
 * sviluppatore) — l'unico punto dell'app che apre un browser esterno.
 *
 * A differenza della versione precedente di questa Activity, ora HA un
 * resumeSignal: lo stato di accessibilità/DND/Home può cambiare mentre
 * l'utente è nelle Impostazioni di sistema (aperte da qui) e torna indietro
 * — stesso pattern di MainActivity/OnboardingActivity, dato che setContent
 * {} viene chiamato una sola volta in onCreate.
 */
class SettingsActivity : BaseActivity() {

    override val themeVariant = ThemeVariant.WITH_ACTION_BAR

    private lateinit var passwordManager: PasswordManager
    private lateinit var phraseManager: PhraseManager
    private lateinit var launcherManager: LauncherManager

    private var resumeSignal by mutableIntStateOf(0)

    // RequestRoleActivity (vedi promptSetAsHome()) determina il pacchetto
    // richiedente dal token dell'Activity chiamante, che il sistema propaga
    // solo quando l'intent parte da un lancio "for result" — un
    // startActivity() semplice non basta: risulterebbe in "Package name
    // cannot be null or empty: null" lato sistema, e l'Activity si
    // chiuderebbe da sola senza mostrare alcun selettore (bug reale,
    // osservato via logcat). Il risultato stesso non serve: onResume()
    // ricalcola già isDefaultHome() al ritorno.
    private val roleRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* onResume() già ricalcola isDefaultHome() */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.apply {
            title = getString(R.string.settings_title)
            setDisplayHomeAsUpEnabled(true)
        }

        passwordManager = PasswordManager.getInstance(applicationContext)
        phraseManager = PhraseManager.getInstance(applicationContext)
        launcherManager = LauncherManager.getInstance(applicationContext)

        setContent {
            CalmOtterTheme(appTheme = ThemeManager.getTheme(this)) {
                SettingsScreen(
                    currentTheme = ThemeManager.getTheme(this),
                    partnerName = passwordManager.getPartnerName(),
                    passwordManager = passwordManager,
                    phraseManager = phraseManager,
                    resumeSignal = resumeSignal,
                    isAccessibilityServiceEnabled = { isAccessibilityServiceEnabled(this) },
                    isDndAccessGranted = { isDndAccessGranted(this) },
                    isDefaultHome = { isDefaultHome() },
                    onGrantAccessibility = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    onGrantDnd = { startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) },
                    onSetHome = { promptSetAsHome() },
                    onPickTheme = { theme -> pickTheme(theme) },
                    onManageAppsVerified = { startActivity(Intent(this, AllowedAppsActivity::class.java)) },
                    onChangePassword = { startActivity(Intent(this, ChangePasswordActivity::class.java)) },
                    onOpenGitHub = { openUrl(GITHUB_REPO_URL) },
                    onOpenLicense = { openUrl("$GITHUB_REPO_URL/blob/main/LICENSE") },
                    onOpenDeveloper = { openUrl(GITHUB_DEVELOPER_URL) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeSignal++
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun pickTheme(theme: AppTheme) {
        if (ThemeManager.getTheme(this) == theme) return
        ThemeManager.setTheme(this, theme)
        // Ricrea l'activity per applicare il nuovo tema immediatamente
        recreate()
    }

    private fun openUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun isDefaultHome(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName == packageName
    }

    /**
     * Forza la ricomparsa del selettore "App Home" di Android, così l'utente
     * può scegliere/confermare CalmOtter come app Home.
     *
     * Da Android 10 (API 29) in poi la preferenza Home è gestita da
     * [RoleManager], non più dal vecchio meccanismo "always" di
     * PackageManager — **primo bug reale**: il vecchio trick (disabilitare
     * e riabilitare il componente per azzerare quella preferenza "always")
     * non ha più alcun effetto su RoleManager. Il tap sul pulsante
     * risolveva silenziosamente un intent Home generico sul launcher già
     * impostato (verificato via log: nessun selettore mostrato, nessun
     * cambio di titolare del ruolo), dando l'impressione che il pulsante
     * non facesse nulla. `RoleManager.createRequestRoleIntent(ROLE_HOME)`
     * è l'API pensata apposta per questo: mostra sempre il selettore di
     * sistema per il ruolo Home, anche quando un altro titolare è già
     * impostato.
     *
     * **Secondo bug reale, scoperto correggendo il primo**: quell'intent
     * non deve MAI partire da un semplice `startActivity()` — l'Activity di
     * sistema che lo gestisce (`RequestRoleActivity`) legge il pacchetto
     * richiedente dal token dell'Activity chiamante, che Android propaga
     * solo quando il lancio avviene "for result". Con `startActivity()`
     * semplice si ottiene silenziosamente `"Package name cannot be null or
     * empty: null"` lato sistema (visto in logcat) e l'attività si chiude
     * da sola, di nuovo senza mostrare alcun selettore — stesso sintomo
     * "il pulsante non fa nulla" del primo bug, causa diversa. Corretto
     * lanciandolo tramite [roleRequestLauncher]
     * (`registerForActivityResult`) invece di `startActivity()` diretto.
     *
     * Sotto Android 10 (minSdk 26: API 26-28) RoleManager non esiste
     * ancora — lì la preferenza Home era davvero gestita dal vecchio
     * meccanismo "always", quindi il trick disabilita/riabilita resta
     * valido e viene mantenuto per quelle versioni. MainActivity ora
     * gestisce sia l'icona del launcher sia il ruolo Home (le due Activity
     * separate sono state unificate, vedi MainActivity.kt), quindi quel
     * toggle disabilita/riabilita momentaneamente anche l'icona del
     * launcher, non solo l'ingresso Home — inevitabile una volta che sono
     * lo stesso componente, ma DONT_KILL_APP + riabilitazione immediata lo
     * rendono un flicker trascurabile, non un vero disallineamento
     * visibile.
     *
     * **Terzo bug reale, stessa famiglia dei due sopra**: quando CalmOtter
     * detiene già il ruolo Home (l'interruttore in Settings è già acceso) e
     * l'utente lo tocca comunque, `createRequestRoleIntent()` produce un
     * intent che il sistema chiude da solo senza mostrare alcun selettore —
     * non c'è nulla da "richiedere" a chi è già titolare del ruolo. Stesso
     * sintomo "il tap non fa nulla" dei due bug precedenti, causa ancora
     * diversa. In quel caso si apre invece `Settings.ACTION_HOME_SETTINGS`
     * (la schermata di sistema "App Home"), che mostra sempre l'elenco con
     * il titolare attuale evidenziato, indipendentemente da chi lo sia —
     * l'unico modo per far vedere qualcosa quando si preme di nuovo un
     * interruttore già "acceso" che non può davvero spegnersi da qui (vedi
     * la doc di HomeCard in SettingsScreen.kt).
     */
    private fun promptSetAsHome() {
        launcherManager.refreshOriginalLauncherPackage()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                if (roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                    startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                } else {
                    roleRequestLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
                }
            }
            return
        }

        val componentName = ComponentName(this, MainActivity::class.java)
        packageManager.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
        packageManager.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )

        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }

}
