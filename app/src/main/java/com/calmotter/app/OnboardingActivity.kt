package com.calmotter.app

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText

class OnboardingActivity : BaseActivity() {

    private lateinit var passwordManager: PasswordManager

    private val steps = listOf(
        R.id.step1, R.id.step2, R.id.step3, R.id.step4, R.id.step5
    )
    private var currentStep = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        passwordManager = PasswordManager(applicationContext)

        buildStepIndicator()
        bindPermissionButtons()
        bindNavigation()
        showStep(0)
    }

    // ──────────────────────────────────────────────
    // Navigazione
    // ──────────────────────────────────────────────

    private fun bindNavigation() {
        val nextButton = findViewById<Button>(R.id.nextButton)
        val backButton = findViewById<Button>(R.id.backButton)

        nextButton.setOnClickListener { handleNext() }
        backButton.setOnClickListener { handleBack() }
    }

    private fun handleNext() {
        // Step 3 (permessi): il "Avanti" è disponibile sempre — i permessi
        // possono essere concessi dopo, ma avvisiamo se mancano.
        // Step 4 (password): validazione obbligatoria prima di proseguire.
        if (currentStep == 3) {
            if (!validateAndSavePassword()) return
        }

        if (currentStep < steps.lastIndex) {
            showStep(currentStep + 1)
        } else {
            // Onboarding completato
            finishOnboarding()
        }
    }

    private fun handleBack() {
        if (currentStep > 0) showStep(currentStep - 1)
    }

    private fun showStep(index: Int) {
        steps.forEachIndexed { i, id ->
            findViewById<View>(id).visibility = if (i == index) View.VISIBLE else View.GONE
        }

        val nextButton = findViewById<Button>(R.id.nextButton)
        val backButton = findViewById<Button>(R.id.backButton)

        backButton.visibility = if (index == 0) View.INVISIBLE else View.VISIBLE
        nextButton.text = getString(
            if (index == steps.lastIndex) R.string.onb_finish else R.string.onb_next
        )

        // Aggiorna i pallini
        updateStepIndicator(index)

        // Aggiorna lo stato permessi ogni volta che si entra nello step 3
        if (index == 2) updatePermissionsStatus()

        currentStep = index
    }

    // ──────────────────────────────────────────────
    // Step 3 — Permessi
    // ──────────────────────────────────────────────

    private fun bindPermissionButtons() {
        findViewById<Button>(R.id.grantAccessibilityButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.grantDndButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }
    }

    private fun updatePermissionsStatus() {
        val accessibilityOk = isAccessibilityServiceEnabled()
        val dndOk = (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .isNotificationPolicyAccessGranted

        val statusText = findViewById<TextView>(R.id.permissionsStatus)
        statusText.text = when {
            accessibilityOk && dndOk -> getString(R.string.onb3_permissions_ok)
            accessibilityOk          -> getString(R.string.onb3_missing_dnd)
            dndOk                    -> getString(R.string.onb3_missing_accessibility)
            else                     -> getString(R.string.onb3_missing_both)
        }

        // Disabilita il bottone dei permessi già concessi
        findViewById<Button>(R.id.grantAccessibilityButton).isEnabled = !accessibilityOk
        findViewById<Button>(R.id.grantDndButton).isEnabled = !dndOk
    }

    override fun onResume() {
        super.onResume()
        // Aggiorna lo stato permessi quando l'utente torna dalle impostazioni
        if (currentStep == 2) updatePermissionsStatus()
    }

    // ──────────────────────────────────────────────
    // Step 4 — Password
    // ──────────────────────────────────────────────

    private fun validateAndSavePassword(): Boolean {
        val p1 = findViewById<TextInputEditText>(R.id.onbPasswordField).text.toString()
        val p2 = findViewById<TextInputEditText>(R.id.onbPasswordConfirmField).text.toString()
        val errorView = findViewById<TextView>(R.id.onbPasswordError)

        val error = when {
            p1.length < 4 -> getString(R.string.password_too_short)
            p1 != p2      -> getString(R.string.passwords_dont_match)
            else          -> null
        }

        return if (error != null) {
            errorView.text = error
            errorView.visibility = View.VISIBLE
            false
        } else {
            passwordManager.setPassword(p1)
            errorView.visibility = View.GONE
            true
        }
    }

    // ──────────────────────────────────────────────
    // Step 5 — Fine
    // ──────────────────────────────────────────────

    private fun finishOnboarding() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
    }

    // ──────────────────────────────────────────────
    // Indicatore step (pallini)
    // ──────────────────────────────────────────────

    private fun buildStepIndicator() {
        val container = findViewById<LinearLayout>(R.id.stepIndicator)
        val sizePx = (8 * resources.displayMetrics.density).toInt()
        val marginPx = (4 * resources.displayMetrics.density).toInt()

        repeat(steps.size) {
            val dot = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).also { lp ->
                    lp.setMargins(marginPx, 0, marginPx, 0)
                }
                setBackgroundResource(R.drawable.dot_inactive)
            }
            container.addView(dot)
        }
    }

    private fun updateStepIndicator(activeIndex: Int) {
        val container = findViewById<LinearLayout>(R.id.stepIndicator)
        for (i in 0 until container.childCount) {
            container.getChildAt(i).setBackgroundResource(
                if (i == activeIndex) R.drawable.dot_active else R.drawable.dot_inactive
            )
        }
    }

    // ──────────────────────────────────────────────
    // Utility
    // ──────────────────────────────────────────────

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = ComponentName(this, AppBlockerAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(":").any { ComponentName.unflattenFromString(it) == expected }
    }
}
