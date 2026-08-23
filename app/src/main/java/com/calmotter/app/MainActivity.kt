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
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : BaseActivity() {

    private lateinit var passwordManager: PasswordManager
    private lateinit var sessionManager: SessionManager
    private lateinit var launcherManager: LauncherManager
    private lateinit var phraseManager: PhraseManager

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* refreshUi non serve: il permesso non cambia il layout */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    private fun refreshUi() {
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
        bindMainScreen()
    }

    private fun bindMainScreen() {
        val statusText = findViewById<TextView>(R.id.statusText)
        val durationPicker = findViewById<android.widget.NumberPicker>(R.id.durationPicker)
        val startButton = findViewById<Button>(R.id.startPauseButton)
        val permissionsButton = findViewById<Button>(R.id.permissionsButton)
        val setHomeButton = findViewById<Button>(R.id.setHomeButton)
        val manageAppsButton = findViewById<Button>(R.id.manageAppsButton)

        durationPicker.minValue = 1
        durationPicker.maxValue = DURATION_LABELS.size
        durationPicker.displayedValues = DURATION_LABELS
        durationPicker.wrapSelectorWheel = false

        val accessibilityOk = isAccessibilityServiceEnabled()
        val dndOk = (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .isNotificationPolicyAccessGranted
        val homeOk = isDefaultHome()
        val sessionActive = sessionManager.isSessionActive()

        statusText.text = when {
            sessionActive -> {
                val remainingMin = (sessionManager.remainingMillis() / 60_000L).toInt() + 1
                getString(R.string.session_active_with_time, remainingMin)
            }
            !accessibilityOk || !dndOk -> getString(R.string.permissions_missing)
            else -> getString(R.string.ready)
        }

        durationPicker.visibility = if (sessionActive) View.GONE else View.VISIBLE
        permissionsButton.visibility = if (!sessionActive && (!accessibilityOk || !dndOk)) View.VISIBLE else View.GONE
        setHomeButton.visibility = if (!sessionActive && !homeOk) View.VISIBLE else View.GONE
        startButton.isEnabled = accessibilityOk && dndOk && !sessionActive

        startButton.setOnClickListener {
            val durationMinutes = durationPicker.value * 30
            sessionManager.startSession(durationMinutes)
            Toast.makeText(this, getString(R.string.session_started), Toast.LENGTH_SHORT).show()
            refreshUi()
        }

        permissionsButton.setOnClickListener {
            if (!accessibilityOk) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } else if (!dndOk) {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            }
        }

        setHomeButton.setOnClickListener {
            promptSetAsHome()
        }

        manageAppsButton.setOnClickListener {
            promptPasswordThenOpenAllowedApps()
        }

        val changePasswordButton = findViewById<Button>(R.id.changePasswordButton)
        changePasswordButton.setOnClickListener {
            startActivity(Intent(this, ChangePasswordActivity::class.java))
        }

        val historyButton = findViewById<Button>(R.id.historyButton)
        historyButton.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        bindThemePicker()
    }

    private fun bindThemePicker() {
        val current = ThemeManager.getTheme(this)
        val dotSage        = findViewById<View>(R.id.dotSage)
        val dotLavender    = findViewById<View>(R.id.dotLavender)
        val dotTerracotta  = findViewById<View>(R.id.dotTerracotta)

        fun updateDots(selected: AppTheme) {
            dotSage.isSelected        = selected == AppTheme.SAGE
            dotLavender.isSelected    = selected == AppTheme.LAVENDER
            dotTerracotta.isSelected  = selected == AppTheme.TERRACOTTA
        }

        updateDots(current)

        fun pickTheme(theme: AppTheme) {
            if (ThemeManager.getTheme(this) == theme) return
            ThemeManager.setTheme(this, theme)
            // Ricrea l'activity per applicare il nuovo tema immediatamente
            recreate()
        }

        findViewById<View>(R.id.themeSage).setOnClickListener       { pickTheme(AppTheme.SAGE) }
        findViewById<View>(R.id.themeLavender).setOnClickListener   { pickTheme(AppTheme.LAVENDER) }
        findViewById<View>(R.id.themeTerracotta).setOnClickListener { pickTheme(AppTheme.TERRACOTTA) }

        val phrasesToggle = findViewById<CheckBox>(R.id.phrasesToggle)
        phrasesToggle.isChecked = phraseManager.isEnabled()
        phrasesToggle.setOnCheckedChangeListener { _, checked ->
            phraseManager.setEnabled(checked)
        }
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

    companion object {
        // Indice 1 = 30 min, indice 2 = 60 min, ... fino a 4 ore, a passi di 30 minuti
        private val DURATION_LABELS = arrayOf(
            "30 min", "1 h", "1 h 30", "2 h", "2 h 30", "3 h", "3 h 30", "4 h"
        )
    }
}
