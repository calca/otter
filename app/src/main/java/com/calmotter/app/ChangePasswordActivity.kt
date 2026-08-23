package com.calmotter.app

import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.textfield.TextInputEditText

class ChangePasswordActivity : BaseActivity() {
    override val themeVariant = ThemeVariant.WITH_ACTION_BAR

    private lateinit var passwordManager: PasswordManager
    private var lockoutTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_password)

        supportActionBar?.apply {
            title = getString(R.string.change_password_title)
            setDisplayHomeAsUpEnabled(true)
        }

        passwordManager = PasswordManager.getInstance(applicationContext)

        val currentField = findViewById<TextInputEditText>(R.id.currentPasswordField)
        val newField = findViewById<TextInputEditText>(R.id.newPasswordField)
        val confirmField = findViewById<TextInputEditText>(R.id.confirmPasswordField)
        val errorText = findViewById<TextView>(R.id.errorText)
        val saveButton = findViewById<Button>(R.id.saveButton)

        saveButton.setOnClickListener {
            if (passwordManager.isLockedOut()) {
                startLockoutCountdown(currentField, saveButton, errorText)
                return@setOnClickListener
            }

            val current = currentField.text.toString()
            val new1 = newField.text.toString()
            val new2 = confirmField.text.toString()

            // verify() ha effetti collaterali sul rate-limiting: va chiamata
            // una sola volta e il risultato riutilizzato, non richiamata due volte.
            val currentValid = passwordManager.verify(current)

            val error = when {
                !currentValid   -> getString(R.string.wrong_current_password)
                new1.length < 4 -> getString(R.string.password_too_short)
                new1 != new2    -> getString(R.string.passwords_dont_match)
                else            -> null
            }

            if (error != null) {
                errorText.text = error
                errorText.visibility = View.VISIBLE
                // Pulisce solo il campo errato per non costringere a riscrivere tutto
                if (!currentValid) currentField.text?.clear()
                else { newField.text?.clear(); confirmField.text?.clear() }

                if (passwordManager.isLockedOut()) {
                    startLockoutCountdown(currentField, saveButton, errorText)
                }
            } else {
                passwordManager.setPassword(new1)
                Toast.makeText(this, getString(R.string.password_changed), Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        if (passwordManager.isLockedOut()) {
            startLockoutCountdown(currentField, saveButton, errorText)
        }
    }

    /**
     * Disabilita il campo della password attuale e il pulsante di salvataggio
     * finché il lockout non scade, aggiornando il messaggio una volta al secondo.
     */
    private fun startLockoutCountdown(
        currentField: TextInputEditText,
        saveButton: Button,
        errorText: TextView
    ) {
        lockoutTimer?.cancel()
        currentField.isEnabled = false
        saveButton.isEnabled = false

        val remainingMillis = passwordManager.lockoutRemainingSeconds() * 1000L
        lockoutTimer = object : CountDownTimer(remainingMillis, 1_000L) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = passwordManager.lockoutRemainingSeconds()
                if (secondsLeft <= 0) {
                    onFinish()
                    return
                }
                errorText.text = getString(R.string.password_locked_out, secondsLeft)
                errorText.visibility = View.VISIBLE
            }

            override fun onFinish() {
                errorText.visibility = View.GONE
                currentField.isEnabled = true
                saveButton.isEnabled = true
            }
        }.start()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        lockoutTimer?.cancel()
        super.onDestroy()
    }
}
