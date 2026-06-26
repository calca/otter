package com.calmotter.app

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.progressindicator.CircularProgressIndicator
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class DashboardActivity : BaseActivity() {
    override val themeVariant = ThemeVariant.BLOCK

    private lateinit var timerDialContainer: FrameLayout
    private lateinit var timerProgress: CircularProgressIndicator
    private lateinit var timerThumb: View
    private lateinit var timerValueText: TextView
    private lateinit var startSessionButton: Button
    private lateinit var bottomNavigation: BottomNavigationView

    private lateinit var sessionManager: SessionManager

    private var currentMinutes = 25
    private val maxMinutes = 120

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        sessionManager = SessionManager(applicationContext)
        
        // Se c'è già una sessione, vai alla schermata di blocco
        if (sessionManager.isSessionActive()) {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_dashboard)
        setupViews()
        setupBottomNavigation()
        updateTimerUI(currentMinutes)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupViews() {
        timerDialContainer = findViewById(R.id.timerDialContainer)
        timerProgress = findViewById(R.id.timerProgress)
        timerThumb = findViewById(R.id.timerThumb)
        timerValueText = findViewById(R.id.timerValueText)
        startSessionButton = findViewById(R.id.startSessionButton)

        timerDialContainer.setOnTouchListener { _, event ->
            handleTouch(event)
            true
        }

        startSessionButton.setOnClickListener {
            sessionManager.startSession(currentMinutes)
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
        }
    }

    private fun handleTouch(event: MotionEvent) {
        val x = event.x - (timerDialContainer.width / 2f)
        val y = event.y - (timerDialContainer.height / 2f)
        
        // Calcola l'angolo in radianti (0 è a destra, ruotiamo di -90 per avere 0 in alto)
        var angle = atan2(y.toDouble(), x.toDouble()) + Math.PI / 2
        if (angle < 0) angle += 2 * Math.PI
        
        // Converti l'angolo in minuti (360 gradi = maxMinutes)
        val percentage = angle / (2 * Math.PI)
        val minutes = (percentage * maxMinutes).toInt().coerceIn(1, maxMinutes)
        
        if (minutes != currentMinutes) {
            currentMinutes = minutes
            updateTimerUI(currentMinutes)
        }
    }

    private fun updateTimerUI(minutes: Int) {
        timerValueText.text = minutes.toString()
        
        // Aggiorna la barra di progresso
        val progress = (minutes / maxMinutes.toFloat() * 100).toInt()
        timerProgress.setProgress(progress, true)
        
        // Posiziona il cursore (thumb)
        val angle = (minutes / maxMinutes.toFloat()) * 2 * Math.PI - Math.PI / 2
        val radius = 120f * resources.displayMetrics.density // Raggio della ghiera
        
        timerThumb.translationX = (cos(angle) * radius).toFloat()
        timerThumb.translationY = (sin(angle) * radius).toFloat()
    }

    private fun setupBottomNavigation() {
        bottomNavigation = findViewById(R.id.bottomNavigation)
        bottomNavigation.selectedItemId = R.id.navigation_dashboard
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_stats -> {
                    startActivity(Intent(this, HistoryActivity::class.java))
                    true
                }
                else -> true
            }
        }
    }
}
