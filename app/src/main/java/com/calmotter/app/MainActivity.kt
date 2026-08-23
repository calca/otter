package com.calmotter.app

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.calmotter.app.ui.screens.MainScreen
import com.calmotter.app.ui.theme.CalmOtterTheme

class MainActivity : BaseActivity() {

    private lateinit var passwordManager: PasswordManager
    private lateinit var sessionManager: SessionManager
    private lateinit var launcherManager: LauncherManager
    private lateinit var phraseManager: PhraseManager

    // Incrementato a ogni onResume(): passato come parametro a MainScreen così
    // che il suo LaunchedEffect(resumeSignal) ricalcoli lo stato che dipende
    // dal sistema operativo (accessibilità, DND, app Home, sessione attiva) —
    // setContent {} viene chiamato una sola volta in onCreate, quindi Compose
    // non ha altrimenti modo di accorgersi di questi cambi di stato esterni.
    private var resumeSignal by mutableIntStateOf(0)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* refreshUi non serve: il permesso non cambia il layout */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        passwordManager = PasswordManager.getInstance(applicationContext)
        sessionManager = SessionManager.getInstance(applicationContext)
        launcherManager = LauncherManager.getInstance(applicationContext)
        phraseManager = PhraseManager.getInstance(applicationContext)
        launcherManager.saveOriginalLauncherIfNeeded()

        // Su Android 13+ chiediamo il permesso per le notifiche al primo avvio
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            CalmOtterTheme(appTheme = ThemeManager.getTheme(this)) {
                MainScreen(
                    resumeSignal = resumeSignal,
                    sessionManager = sessionManager,
                    phraseManager = phraseManager,
                    currentTheme = ThemeManager.getTheme(this),
                    isAccessibilityServiceEnabled = { isAccessibilityServiceEnabled() },
                    isDndAccessGranted = {
                        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                            .isNotificationPolicyAccessGranted
                    },
                    isDefaultHome = { isDefaultHome() },
                    onGrantAccessibility = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    onGrantDnd = { startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) },
                    onSetHome = { promptSetAsHome() },
                    onManageApps = { promptPasswordThenOpenAllowedApps() },
                    onChangePassword = { startActivity(Intent(this, ChangePasswordActivity::class.java)) },
                    onHistory = { startActivity(Intent(this, HistoryActivity::class.java)) },
                    onPickTheme = { theme -> pickTheme(theme) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // Se la password non è ancora impostata, l'onboarding non è stato
        // completato: reindirizza. Non dovrebbe mai succedere in uso normale
        // (OnboardingActivity fa il redirect qui solo dopo il completamento),
        // ma copre il caso in cui l'utente torni indietro o reinstalli.
        if (!passwordManager.isPasswordSet()) {
            startActivity(Intent(this, OnboardingActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            return
        }

        resumeSignal++
    }

    private fun pickTheme(theme: AppTheme) {
        if (ThemeManager.getTheme(this) == theme) return
        ThemeManager.setTheme(this, theme)
        // Ricrea l'activity per applicare il nuovo tema immediatamente
        recreate()
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
     */
    private fun promptSetAsHome() {
        launcherManager.saveOriginalLauncherIfNeeded()

        val componentName = ComponentName(this, HomeActivity::class.java)
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

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = ComponentName(this, AppBlockerAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(":").any { ComponentName.unflattenFromString(it) == expected }
    }
}
