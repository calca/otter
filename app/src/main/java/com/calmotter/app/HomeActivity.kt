package com.calmotter.app

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

class HomeActivity : BaseActivity() {
    override val themeVariant = ThemeVariant.BLOCK

    private lateinit var sessionManager: SessionManager
    private lateinit var passwordManager: PasswordManager
    private lateinit var launcherManager: LauncherManager
    private lateinit var phraseManager: PhraseManager
    private var countDownTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Intenzionalmente vuoto per bloccare il tasto back
            }
        })

        sessionManager = SessionManager(applicationContext)
        passwordManager = PasswordManager(applicationContext)
        launcherManager = LauncherManager(applicationContext)
        phraseManager = PhraseManager(applicationContext)

        if (!sessionManager.isSessionActive()) {
            forwardToOriginalLauncher()
            return
        }

        setContentView(R.layout.activity_block_overlay)
        bindBlockScreen()
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

    private fun bindBlockScreen() {
        val passwordField = findViewById<EditText>(R.id.passwordField)
        val unlockButton = findViewById<Button>(R.id.unlockButton)
        val statusText = findViewById<TextView>(R.id.statusText)
        val remainingText = findViewById<TextView>(R.id.remainingText)
        val phraseText = findViewById<TextView>(R.id.phraseText)

        val phrase = phraseManager.randomPhrase()
        if (phrase != null) {
            phraseText.text = "\u201C$phrase\u201D"
            phraseText.visibility = View.VISIBLE
        }

        unlockButton.setOnClickListener {
            val entered = passwordField.text.toString()
            if (passwordManager.verify(entered)) {
                sessionManager.endSession()
                Toast.makeText(this, getString(R.string.session_ended), Toast.LENGTH_SHORT).show()
                forwardToOriginalLauncher()
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
            forwardToOriginalLauncher()
            return
        }

        countDownTimer = object : CountDownTimer(remainingMillis, 60_000) {
            override fun onTick(millisUntilFinished: Long) {
                remainingText.text = CalmCountdown.format(millisUntilFinished)
            }

            override fun onFinish() {
                sessionManager.endSession(completedNaturally = true)
                forwardToOriginalLauncher()
            }
        }.start()
    }

    override fun onDestroy() {
        countDownTimer?.cancel()
        super.onDestroy()
    }
}
