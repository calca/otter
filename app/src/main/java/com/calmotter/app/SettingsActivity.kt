package com.calmotter.app

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AlertDialog
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
                    phraseManager = phraseManager,
                    resumeSignal = resumeSignal,
                    isAccessibilityServiceEnabled = { isAccessibilityServiceEnabled(this) },
                    isDndAccessGranted = { isDndAccessGranted(this) },
                    isDefaultHome = { isDefaultHome() },
                    onGrantAccessibility = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    onGrantDnd = { startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) },
                    onSetHome = { promptSetAsHome() },
                    onPickTheme = { theme -> pickTheme(theme) },
                    onManageApps = { promptPasswordThenOpenAllowedApps() },
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
     * Forza la ricomparsa del selettore "App Home" di Android: disabilitare e
     * riabilitare il componente azzera la preferenza già salvata dal sistema,
     * così l'utente può scegliere/confermare CalmOtter come app Home.
     *
     * MainActivity ora gestisce sia l'icona del launcher sia il ruolo Home
     * (le due Activity separate sono state unificate, vedi MainActivity.kt),
     * quindi questo toggle disabilita/riabilita momentaneamente anche
     * l'icona del launcher, non solo l'ingresso Home — inevitabile una volta
     * che sono lo stesso componente, ma DONT_KILL_APP + riabilitazione
     * immediata lo rendono un flicker trascurabile, non un vero
     * disallineamento visibile.
     */
    private fun promptSetAsHome() {
        launcherManager.saveOriginalLauncherIfNeeded()

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

    /**
     * L'elenco delle app extra consentite durante una pausa può essere
     * modificato solo da chi conosce la password: la verifica avviene qui,
     * prima di aprire AllowedAppsActivity.
     */
    private fun promptPasswordThenOpenAllowedApps() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = getString(R.string.hint_unlock_password)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.manage_allowed_apps)
            .setMessage(R.string.manage_allowed_apps_password_prompt)
            .setView(input)
            .setPositiveButton(R.string.confirm) { _, _ ->
                if (passwordManager.isLockedOut()) {
                    // Dialog "usa e getta": niente countdown live, mostriamo
                    // solo quanto manca al termine del lockout in questo momento.
                    Toast.makeText(
                        this,
                        getString(R.string.password_locked_out, passwordManager.lockoutRemainingSeconds()),
                        Toast.LENGTH_SHORT
                    ).show()
                } else if (passwordManager.verify(input.text.toString())) {
                    startActivity(Intent(this, AllowedAppsActivity::class.java))
                } else {
                    Toast.makeText(this, getString(R.string.wrong_password), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
