package com.calmotter.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.ServiceCompat
import com.calmotter.app.sound.RelaxSound
import com.calmotter.app.sound.RelaxSoundPlayer
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
    private var soundPlayer: RelaxSoundPlayer? = null
    // Vero se il servizio è partito anche come riproduzione multimediale: dal
    // boot (Android 15) o in certi stati il sistema lo rifiuta, e allora la
    // pausa resta valida ma in silenzio.
    private var mediaForeground = false
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
        if (!mediaForeground) enterForeground()
        syncSoundPlayback()
        if (intent?.action != ACTION_SYNC_SOUND) {
            handler.removeCallbacks(updateRunnable)
            handler.post(updateRunnable)
        }
        return START_STICKY // il sistema tenta di riavviarlo se viene killato
    }

    /**
     * Va in primo piano. Con un suono scelto dichiara anche la riproduzione
     * multimediale (specs/relax-sound/); se il sistema la rifiuta, ripiega sul
     * solo tipo di prima e la pausa resta senza suono.
     */
    private fun enterForeground() {
        val notification = buildNotification()
        val wantsSound = sessionManager.sessionSound() != RelaxSound.OFF
        if (wantsSound && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                ServiceCompat.startForeground(
                    this, NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                )
                mediaForeground = true
                return
            } catch (e: Exception) {
                Log.w("SessionForegroundService", "Primo piano multimediale rifiutato, la pausa resta senza suono", e)
            }
        }
        // Il tipo va detto anche qui: senza, da Android 14 valgono tutti quelli
        // del manifest, multimediale compreso.
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
    }

    /** Allinea il suono a ciò che la pausa chiede: suona, è in pausa (spento dall'utente) o tace. */
    private fun syncSoundPlayback() {
        val sound = if (mediaForeground) sessionManager.sessionSound() else RelaxSound.OFF
        val player = soundPlayer ?: RelaxSoundPlayer(this).also { soundPlayer = it }
        when {
            sound == RelaxSound.OFF -> player.stop()
            sessionManager.isSoundMuted() -> player.stop()
            else -> if (!player.isPlaying) player.play(sound)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(updateRunnable)
        soundPlayer?.stop()
        soundPlayer = null
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
        // Una pausa partita da sola dice perché anche qui ("Pausa delle
        // 21:00"): la tendina è raggiungibile anche a schermo bloccato.
        val title = when {
            !sessionManager.isGroupSession() -> sessionManager.originLabel() ?: getString(R.string.pause_notification_title)
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

        private const val ACTION_SYNC_SOUND = "com.calmotter.app.action.SYNC_SOUND"

        /** Fa riallineare il suono (acceso/spento dall'utente) a un servizio già in corso. */
        fun syncSound(context: Context) {
            val sessionManager = SessionManager.getInstance(context)
            if (!sessionManager.isSessionActive()) return
            try {
                context.startService(
                    Intent(context, SessionForegroundService::class.java).setAction(ACTION_SYNC_SOUND),
                )
            } catch (e: IllegalStateException) {
                Log.w("SessionForegroundService", "Impossibile aggiornare il suono", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SessionForegroundService::class.java))
        }
    }
}
