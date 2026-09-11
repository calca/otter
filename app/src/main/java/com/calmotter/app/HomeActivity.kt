package com.calmotter.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import com.calmotter.app.ui.screens.BlockScreen
import com.calmotter.app.ui.theme.CalmOtterTheme

class HomeActivity : BaseActivity() {
    override val themeVariant = ThemeVariant.BLOCK

    private lateinit var launcherManager: LauncherManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Intenzionalmente vuoto per bloccare il tasto back
            }
        })

        val sessionManager = SessionManager.getInstance(applicationContext)
        val passwordManager = PasswordManager.getInstance(applicationContext)
        launcherManager = LauncherManager.getInstance(applicationContext)
        val phraseManager = PhraseManager.getInstance(applicationContext)

        if (!sessionManager.isSessionActive()) {
            forwardToOriginalLauncher()
            return
        }

        val phrase = phraseManager.randomPhrase()
        val phraseText = phrase?.let { "“$it”" }

        setContent {
            CalmOtterTheme(appTheme = ThemeManager.getTheme(this)) {
                BlockScreen(
                    sessionManager = sessionManager,
                    passwordManager = passwordManager,
                    phraseText = phraseText,
                    onExpiredImmediately = { forwardToOriginalLauncher() },
                    onExpiredNaturally = { forwardToOriginalLauncher() },
                    onUnlocked = { forwardToOriginalLauncher() },
                )
            }
        }
    }

    private fun forwardToOriginalLauncher() {
        val originalPackage = launcherManager.getOriginalLauncherPackage()
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            if (originalPackage != null) setPackage(originalPackage)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        finish()
    }
}
