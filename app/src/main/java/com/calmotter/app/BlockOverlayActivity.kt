package com.calmotter.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import com.calmotter.app.ui.screens.BlockScreen
import com.calmotter.app.ui.theme.CalmOtterTheme

/**
 * Overlay di blocco lanciato da [AppBlockerAccessibilityService] quando si
 * apre un'app non consentita durante una sessione attiva. Mostra lo stesso
 * `BlockScreen`, con le stesse `allowedApps` (vedi [loadAllowedAppLaunchItems]
 * in `AllowedAppLaunchItems.kt`, condiviso con `MainActivity`) e lo stesso
 * comportamento a scadenza/sblocco, di quando una sessione attiva viene
 * incontrata dall'icona del launcher o dal tasto Home — le tre schermate di
 * blocco sono state deliberatamente unificate, vedi il commento di classe
 * di `MainActivity`.
 */
class BlockOverlayActivity : BaseActivity() {

    override fun onResume() {
        super.onResume()
        // Rimuove la finestra-ponte di AppBlockerAccessibilityService, se
        // ancora presente: questa Activity è ora davvero in primo piano, non
        // serve più coprire lo schermo dell'app bloccata nell'attesa.
        AppBlockerAccessibilityService.notifyBlockScreenVisible()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Pausa con solo alcune app vietate: il resto del telefono è
                // libero, quindi il back deve far uscire dall'app vietata
                // (alla home) invece di restare muto. Con la lista "solo
                // queste" resta intenzionalmente vuoto: tutto è bloccato.
                val profile = AllowedAppsManager.getInstance(applicationContext)
                    .profile(SessionManager.getInstance(applicationContext).sessionProfileId())
                if (profile.mode == AllowedAppsMode.BLOCKLIST) {
                    startActivity(
                        Intent(Intent.ACTION_MAIN)
                            .addCategory(Intent.CATEGORY_HOME)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                    finish()
                }
            }
        })

        val passwordManager = PasswordManager.getInstance(applicationContext)
        val sessionManager = SessionManager.getInstance(applicationContext)
        val phraseManager = PhraseManager.getInstance(applicationContext)

        val phrase = phraseManager.randomPhrase()
        val phraseText = phrase?.let { getString(R.string.phrase_format, it) }

        setContent {
            CalmOtterTheme(appTheme = ThemeManager.getTheme(this)) {
                BlockScreen(
                    sessionManager = sessionManager,
                    passwordManager = passwordManager,
                    phraseText = phraseText,
                    onExpiredImmediately = { finish() },
                    onExpiredNaturally = {
                        // Pausa arrivata in fondo: prima di chiudere, "com'è
                        // andata?" (specs/closing-moment/).
                        ClosingMomentActivity.startIfPending(this)
                        finish()
                    },
                    onUnlocked = { finish() },
                    allowedApps = loadAllowedAppLaunchItems(applicationContext),
                    onLaunchApp = { pkg -> launchAllowedApp(applicationContext, pkg) },
                )
            }
        }
    }
}
