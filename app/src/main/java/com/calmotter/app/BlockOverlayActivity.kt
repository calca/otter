package com.calmotter.app

import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback

class BlockOverlayActivity : BaseActivity() {
    override val themeVariant = ThemeVariant.BLOCK

    private lateinit var passwordManager: PasswordManager
    private lateinit var sessionManager: SessionManager
    private lateinit var phraseManager: PhraseManager
    private var countDownTimer: CountDownTimer? = null
    private var lockoutTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_block_overlay)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Intenzionalmente vuoto per bloccare il tasto back
            }
        })

        passwordManager = PasswordManager.getInstance(applicationContext)
        sessionManager = SessionManager.getInstance(applicationContext)
        phraseManager = PhraseManager.getInstance(applicationContext)

        val passwordField = findViewById<EditText>(R.id.passwordField)
        val unlockButton = findViewById<Button>(R.id.unlockButton)
        val statusText = findViewById<TextView>(R.id.statusText)
        val remainingText = findViewById<TextView>(R.id.remainingText)
        val phraseText = findViewById<TextView>(R.id.phraseText)

        // Frase opzionale
        val phrase = phraseManager.randomPhrase()
        if (phrase != null) {
            phraseText.text = getString(R.string.phrase_format, phrase)
            phraseText.visibility = View.VISIBLE
        }

        unlockButton.setOnClickListener {
            if (passwordManager.isLockedOut()) {
                startLockoutCountdown(passwordField, unlockButton, statusText)
                return@setOnClickListener
            }
            val entered = passwordField.text.toString()
            if (passwordManager.verify(entered)) {
                sessionManager.endSession()
                Toast.makeText(this, getString(R.string.session_ended), Toast.LENGTH_SHORT).show()
                finish()
            } else {
                passwordField.text.clear()
                if (passwordManager.isLockedOut()) {
                    startLockoutCountdown(passwordField, unlockButton, statusText)
                } else {
                    statusText.text = getString(R.string.wrong_password)
                }
            }
        }

        if (passwordManager.isLockedOut()) {
            startLockoutCountdown(passwordField, unlockButton, statusText)
        }

        startCountdown(remainingText)
    }

    /**
     * Disabilita campo password e pulsante finché il lockout non scade,
     * aggiornando il messaggio una volta al secondo (stesso pattern del
     * CountDownTimer usato per il tempo rimanente della sessione).
     */
    private fun startLockoutCountdown(passwordField: EditText, unlockButton: Button, statusText: TextView) {
        lockoutTimer?.cancel()
        passwordField.isEnabled = false
        unlockButton.isEnabled = false

        val remainingMillis = passwordManager.lockoutRemainingSeconds() * 1000L
        lockoutTimer = object : CountDownTimer(remainingMillis, 1_000L) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = passwordManager.lockoutRemainingSeconds()
                if (secondsLeft <= 0) {
                    onFinish()
                    return
                }
                statusText.text = getString(R.string.password_locked_out, secondsLeft)
            }

            override fun onFinish() {
                statusText.text = ""
                passwordField.isEnabled = true
                unlockButton.isEnabled = true
            }
        }.start()
    }

    private fun startCountdown(remainingText: TextView) {
        val remainingMillis = sessionManager.remainingMillis()
        if (remainingMillis <= 0) {
            sessionManager.endSession(completedNaturally = true)
            finish()
            return
        }

        countDownTimer = object : CountDownTimer(remainingMillis, 60_000) {
            override fun onTick(millisUntilFinished: Long) {
                remainingText.text = CalmCountdown.format(millisUntilFinished)
            }

            override fun onFinish() {
                sessionManager.endSession(completedNaturally = true)
                Toast.makeText(this@BlockOverlayActivity, getString(R.string.session_ended), Toast.LENGTH_SHORT).show()
                finish()
            }
        }.start()
    }

    override fun onDestroy() {
        countDownTimer?.cancel()
        lockoutTimer?.cancel()
        super.onDestroy()
    }
}
