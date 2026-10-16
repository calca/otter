package com.calmotter.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import com.calmotter.app.ui.screens.SettingsScreen
import com.calmotter.app.ui.theme.CalmOtterTheme

// Repo pubblico su GitHub — unica fonte per link "codice sorgente",
// "licenza" (file LICENSE nello stesso repo) e "sviluppatore" in Settings.
private const val GITHUB_REPO_URL = "https://github.com/calca/otter"
private const val GITHUB_DEVELOPER_URL = "https://github.com/calca"
private const val STABLE_PACKAGE = "com.calmotter.app"
private const val PLAY_STORE_PACKAGE = "com.android.vending"

/**
 * Configurazione: personalizzazione (palette), stato di accessibilità/DND
 * (spostati qui dalla Home — vedi MainScreen.kt — perché
 * non bloccano l'avvio di una pausa, solo la sua applicazione più rigorosa,
 * e controllarli ogni volta che si apre l'app era percepito come fastidioso,
 * vedi home-and-settings/requirements.md), le due azioni protette da
 * password (cambio password, elenco app consentite), le preferenze non
 * critiche, e una sezione "Info" con link esterni (repo GitHub, licenza,
 * sviluppatore) — l'unico punto dell'app che apre un browser esterno.
 *
 * A differenza della versione precedente di questa Activity, ora HA un
 * resumeSignal: lo stato di accessibilità/DND può cambiare mentre
 * l'utente è nelle Impostazioni di sistema (aperte da qui) e torna indietro
 * — stesso pattern di MainActivity/OnboardingActivity, dato che setContent
 * {} viene chiamato una sola volta in onCreate.
 */
class SettingsActivity : BaseActivity() {

    override val themeVariant = ThemeVariant.WITH_ACTION_BAR

    private lateinit var passwordManager: PasswordManager
    private lateinit var phraseManager: PhraseManager

    private var resumeSignal by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.apply {
            title = getString(R.string.settings_title)
            setDisplayHomeAsUpEnabled(true)
        }

        passwordManager = PasswordManager.getInstance(applicationContext)
        phraseManager = PhraseManager.getInstance(applicationContext)

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
                    onGrantAccessibility = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    onGrantDnd = { startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) },
                    onPickTheme = { theme -> pickTheme(theme) },
                    onManageAppsVerified = { startActivity(Intent(this, AllowedAppsActivity::class.java)) },
                    onScheduledPauses = { startActivity(Intent(this, ScheduledPausesActivity::class.java)) },
                    onUsagePause = { startActivity(Intent(this, UsagePauseActivity::class.java)) },
                    onChangePassword = { startActivity(Intent(this, ChangePasswordActivity::class.java)) },
                    onOpenGitHub = { openUrl(GITHUB_REPO_URL) },
                    onOpenLicense = { openUrl("$GITHUB_REPO_URL/blob/main/LICENSE") },
                    onOpenDeveloper = { openUrl(GITHUB_DEVELOPER_URL) },
                    onFeedback = { openUrl(feedbackUrl()) },
                    onRate = ::openPlayStoreListing,
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

    /**
     * "Invia un commento": una issue GitHub con il modulo della beta
     * (.github/ISSUE_TEMPLATE/beta-feedback.yml), versione e telefono già
     * compilati. Come gli altri link di Info, è il browser che apre la
     * pagina su un tocco dell'utente: l'app non manda nulla.
     */
    private fun feedbackUrl(): String {
        val version = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        val device = "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}"
        return "$GITHUB_REPO_URL/issues/new".toUri().buildUpon()
            .appendQueryParameter("template", "beta-feedback.yml")
            .appendQueryParameter("version", version)
            .appendQueryParameter("device", device)
            .build().toString()
    }

    private fun openUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    }

    /**
     * La scheda di Calm Otter nell'app Play Store, dove si lascia il voto;
     * senza Play Store (o se non risponde), la stessa pagina sul web. Sempre
     * la versione stable, anche dalla beta, come [Share.PLAY_STORE_URL].
     */
    private fun openPlayStoreListing() {
        val market = Intent(Intent.ACTION_VIEW, "market://details?id=$STABLE_PACKAGE".toUri())
            .setPackage(PLAY_STORE_PACKAGE)
        runCatching { startActivity(market) }
            .onFailure { openUrl(Share.PLAY_STORE_URL) }
    }

}
