package com.calmotter.app

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.VisibleForTesting

/**
 * Gestisce lo stato "sessione di pausa attiva/non attiva", la sua durata e,
 * in parallelo, attiva/disattiva la modalità Non disturbare lasciando
 * passare solo le chiamate.
 */
class SessionManager private constructor(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Vero se la sessione è attiva. Se il tempo impostato è già scaduto
     * (rete di sicurezza nel caso l'allarme di sistema non sia scattato),
     * la sessione viene chiusa automaticamente qui.
     */
    fun isSessionActive(): Boolean {
        if (!prefs.getBoolean(KEY_ACTIVE, false)) return false
        if (System.currentTimeMillis() >= prefs.getLong(KEY_END_TIME, 0L)) {
            endSession(completedNaturally = true)
            return false
        }
        return true
    }

    /** Millisecondi rimanenti alla fine della pausa (0 se non attiva/scaduta). */
    fun remainingMillis(): Long {
        val end = prefs.getLong(KEY_END_TIME, 0L)
        return (end - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    /**
     * [isGroupSession]: true quando questa sessione è partita da
     * GroupPauseHostActivity/GroupPauseJoinActivity (pausa di gruppo, vedi
     * specs/group-pause/) invece che dal tap sull'otter in Home — letto poi
     * da [isGroupSession] per mostrare un indicatore in BlockScreen e
     * riportato in [SessionRecord] per il tag in Cronologia. Non cambia in
     * alcun modo il funzionamento della sessione stessa (durata, DND,
     * sblocco): è solo un'etichetta.
     */
    fun startSession(durationMinutes: Int, isGroupSession: Boolean = false) {
        val now = System.currentTimeMillis()
        val endTime = now + durationMinutes * 60_000L
        prefs.edit()
            .putBoolean(KEY_ACTIVE, true)
            .putLong(KEY_START_TIME, now)
            .putLong(KEY_END_TIME, endTime)
            .putInt(KEY_PLANNED_MINUTES, durationMinutes)
            .putBoolean(KEY_IS_GROUP, isGroupSession)
            .apply()
        setOnlyCallsAllowed(true)
        scheduleAutoExpiry(endTime)
        SessionForegroundService.start(context)
        PauseWidgetProvider.saveLastDuration(context, durationMinutes)
        PauseWidgetProvider.updateAllWidgets(context)
    }

    /** Vero se la sessione attiva (o appena terminata) era una pausa di gruppo. */
    fun isGroupSession(): Boolean = prefs.getBoolean(KEY_IS_GROUP, false)

    /** Millisecondi totali della sessione (0 se non disponibile). */
    fun totalMillis(): Long {
        val start = prefs.getLong(KEY_START_TIME, 0L)
        val end = prefs.getLong(KEY_END_TIME, 0L)
        return (end - start).coerceAtLeast(0L)
    }

    /**
     * Termina la sessione e la registra nella cronologia.
     * @param completedNaturally true se scaduta per timer, false se sbloccata con password
     */
    fun endSession(completedNaturally: Boolean = false) {
        val startTime = prefs.getLong(KEY_START_TIME, 0L)
        val plannedMinutes = prefs.getInt(KEY_PLANNED_MINUTES, 0)
        val effectiveMinutes = ((System.currentTimeMillis() - startTime) / 60_000L)
            .toInt().coerceAtLeast(0)

        if (startTime > 0 && plannedMinutes > 0) {
            SessionHistoryManager.getInstance(context).add(
                SessionRecord(
                    startTimeMs        = startTime,
                    plannedMinutes     = plannedMinutes,
                    effectiveMinutes   = effectiveMinutes,
                    completedNaturally = completedNaturally,
                    isGroupSession     = prefs.getBoolean(KEY_IS_GROUP, false),
                )
            )
        }

        prefs.edit()
            .putBoolean(KEY_ACTIVE, false)
            .remove(KEY_END_TIME)
            .apply()
        setOnlyCallsAllowed(false)
        cancelAutoExpiry()
        SessionForegroundService.stop(context)
        PauseWidgetProvider.updateAllWidgets(context)
    }

    /**
     * Riapplica DND, allarme e service dopo un riavvio.
     */
    fun reapplyAfterBoot() {
        val endTime = prefs.getLong(KEY_END_TIME, 0L)
        setOnlyCallsAllowed(true)
        scheduleAutoExpiry(endTime)
        SessionForegroundService.start(context)
    }

    /**
     * Programma una scadenza automatica via AlarmManager: alla fine del tempo
     * scelto, la sessione termina (DND disattivato) anche se l'utente non
     * sta interagendo con l'app in quel momento. Usiamo un allarme "inesatto"
     * (nessun permesso aggiuntivo richiesto): può avere qualche minuto di
     * scarto in Doze, accettabile per questo caso d'uso.
     */
    private fun scheduleAutoExpiry(endTime: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endTime, expiryPendingIntent())
    }

    private fun cancelAutoExpiry() {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(expiryPendingIntent())
    }

    private fun expiryPendingIntent(): PendingIntent {
        val intent = Intent(context, SessionExpiryReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            EXPIRY_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun setOnlyCallsAllowed(enabled: Boolean) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) return // permesso non concesso: si ignora silenziosamente

        if (enabled) {
            // Silenzia tutto tranne le chiamate telefoniche, da qualunque numero.
            // Nasconde anche i pallini (dots), la tendina (pull-down) e la barra di stato (status bar).
            val suppressedEffects = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                NotificationManager.Policy.SUPPRESSED_EFFECT_BADGE or
                        NotificationManager.Policy.SUPPRESSED_EFFECT_NOTIFICATION_LIST or
                        NotificationManager.Policy.SUPPRESSED_EFFECT_STATUS_BAR
            } else {
                0
            }

            nm.setNotificationPolicy(
                NotificationManager.Policy(
                    NotificationManager.Policy.PRIORITY_CATEGORY_CALLS,
                    NotificationManager.Policy.PRIORITY_SENDERS_ANY,
                    0,
                    suppressedEffects
                )
            )
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        } else {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        }
    }

    companion object {
        private const val PREFS_NAME = "calm_otter_session"
        private const val KEY_ACTIVE = "session_active"
        private const val KEY_START_TIME = "session_start_time"
        private const val KEY_END_TIME = "session_end_time"
        private const val KEY_PLANNED_MINUTES = "session_planned_minutes"
        private const val KEY_IS_GROUP = "session_is_group"
        private const val EXPIRY_REQUEST_CODE = 1001

        @Volatile private var instance: SessionManager? = null

        fun getInstance(context: Context): SessionManager =
            instance ?: synchronized(this) {
                instance ?: SessionManager(context.applicationContext).also { instance = it }
            }

        @VisibleForTesting
        internal fun resetInstanceForTests() {
            instance = null
        }
    }
}
