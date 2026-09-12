package com.calmotter.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import com.calmotter.app.ui.screens.OnboardingScreen
import com.calmotter.app.ui.theme.CalmOtterTheme

class OnboardingActivity : BaseActivity() {

    private lateinit var passwordManager: PasswordManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        passwordManager = PasswordManager.getInstance(applicationContext)

        setContent {
            CalmOtterTheme(appTheme = ThemeManager.getTheme(this)) {
                OnboardingScreen(
                    passwordManager = passwordManager,
                    onFinished = { finishOnboarding() },
                )
            }
        }
    }

    private fun finishOnboarding() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
    }
}
