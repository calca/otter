package com.calmotter.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/**
 * Foreground service attivo per tutta la durata di una sessione di pausa.
 *
 * Responsabilità:
 * - Mostra la notifica persistente con progress bar e messaggio calmo
 * - Si aggiorna ogni minuto con un nuovo messaggio random
 * - Mantiene il processo vivo, riducendo il rischio che Android uccida
 *   l'AccessibilityService su dispositivi con aggressive battery policy
 * - Si ferma da solo quando la sessione termina (via endSession o scadenza)
 *
 * Avvio: chiamato da SessionManager.startSession()
 * Stop:  chiamato da SessionManager.endSession() + SessionExpiryReceiver
 */
class SessionForegroundService : Service() {

    private lateinit var sessionManager: SessionManager
    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            if (!sessionManager.isSessionActive()) {
                stopSelf()
                return
            }
            updateNotification()
            handler.postDelayed(this, UPDATE_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        sessionManager = SessionManager.getInstance(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        handler.post(updateRunnable)
        return START_STICKY // il sistema tenta di riavviarlo se viene killato
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(updateRunnable)
        super.onDestroy()
    }

    private fun updateNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val remaining = sessionManager.remainingMillis()
        val total = sessionManager.totalMillis()

        // Progresso: quanti ms sono già trascorsi rispetto al totale (0–100)
        val progress = if (total > 0) {
            ((total - remaining).toFloat() / total * 100).toInt().coerceIn(0, 100)
        } else 0

        val contentText = CalmCountdown.format(remaining, this)
        val subText = resources.getStringArray(R.array.notification_encouragement_phrases).random()

        // Tap sulla notifica → apre MainActivity
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(contentText)
            .setSubText(subText)
            .setProgress(100, progress, false)
            .setOngoing(true)          // non eliminabile con swipe
            .setOnlyAlertOnce(true)    // nessun suono/vibrazione agli aggiornamenti
            .setContentIntent(tapIntent)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW  // IMPORTANCE_LOW = nessun suono, nessun popup
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "calm_otter_session"
        private const val UPDATE_INTERVAL_MS = 60_000L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, SessionForegroundService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SessionForegroundService::class.java))
        }
    }
}
