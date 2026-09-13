package com.calmotter.app

import android.os.Bundle
import android.widget.Toast
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
    override val themeVariant = ThemeVariant.BLOCK

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Intenzionalmente vuoto per bloccare il tasto back
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
                        Toast.makeText(this, getString(R.string.session_ended), Toast.LENGTH_SHORT).show()
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
