package com.calmotter.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.util.Log
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
            // Ogni minuto, insieme alla notifica: se una routine o un tocco
            // nella tendina ha spento Non disturbare, la pausa lo riaccende.
            sessionManager.ensureDndApplied()
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

        // Il titolo dice cosa succede, il testo quanto manca (arrotondato
        // come sulla schermata della pausa: contare i minuti è proprio ciò
        // che la pausa toglie). Niente frasi a caso: prima testo e sottotesto
        // cambiavano a ogni aggiornamento, ogni minuto, e la notifica di una
        // pausa calma era la cosa più irrequieta della tendina.
        val companions = sessionManager.companions()
        val title = when {
            !sessionManager.isGroupSession() -> getString(R.string.pause_notification_title)
            companions.size == 1 -> getString(R.string.pause_notification_together, companions.single())
            else -> getString(R.string.pause_notification_together_plain)
        }
        val contentText = CalmCountdown.remainingText(remaining, this)

        // Tap sulla notifica → apre MainActivity
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile_otter)
            .setContentTitle(title)
            .setContentText(contentText)
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
            // Da un allarme inesatto (pausa programmata) Android 12+ può
            // rifiutare l'avvio in background. La pausa resta comunque valida
            // (DND, allarme di fine e blocco non dipendono dal servizio, che
            // tiene solo vivo il processo e la notifica): MainActivity lo
            // riavvia alla prima apertura. Vedi specs/scheduled-pauses/.
            try {
                context.startForegroundService(Intent(context, SessionForegroundService::class.java))
            } catch (e: IllegalStateException) {
                Log.w("SessionForegroundService", "Avvio in primo piano rifiutato, riprovo all'apertura", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SessionForegroundService::class.java))
        }
    }
}
