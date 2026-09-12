package com.calmotter.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.calmotter.app.ui.screens.OnboardingScreen
import com.calmotter.app.ui.theme.CalmOtterTheme

class OnboardingActivity : BaseActivity() {

    private lateinit var passwordManager: PasswordManager

    // Incrementato a ogni onResume(): passato come parametro a OnboardingScreen
    // così che il suo LaunchedEffect(resumeSignal, currentStep) ricalcoli lo
    // stato permessi (accessibilità/DND) quando l'utente torna dalle
    // Impostazioni — stesso pattern di MainActivity/MainScreen, dato che
    // setContent {} viene chiamato una sola volta in onCreate.
    private var resumeSignal by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        passwordManager = PasswordManager.getInstance(applicationContext)

        setContent {
            CalmOtterTheme(appTheme = ThemeManager.getTheme(this)) {
                OnboardingScreen(
                    resumeSignal = resumeSignal,
                    passwordManager = passwordManager,
                    isAccessibilityServiceEnabled = { isAccessibilityServiceEnabled(this) },
                    isDndAccessGranted = { isDndAccessGranted(this) },
                    onGrantAccessibility = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    onGrantDnd = { startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) },
                    onFinished = { finishOnboarding() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Aggiorna lo stato permessi quando l'utente torna dalle impostazioni
        // (solo effettivo se lo step 3 è quello visibile, vedi OnboardingScreen).
        resumeSignal++
    }

    private fun finishOnboarding() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
    }
}
