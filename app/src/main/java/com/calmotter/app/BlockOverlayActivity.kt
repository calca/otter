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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_block_overlay)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Intenzionalmente vuoto per bloccare il tasto back
            }
        })

        passwordManager = PasswordManager(applicationContext)
        sessionManager = SessionManager(applicationContext)
        phraseManager = PhraseManager(applicationContext)

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
            val entered = passwordField.text.toString()
            if (passwordManager.verify(entered)) {
                sessionManager.endSession()
                Toast.makeText(this, getString(R.string.session_ended), Toast.LENGTH_SHORT).show()
                finish()
            } else {
                statusText.text = getString(R.string.wrong_password)
                passwordField.text.clear()
            }
        }

        startCountdown(remainingText)
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
        super.onDestroy()
    }
}
