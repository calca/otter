package com.calmotter.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * I due allarmi di una pausa: la fine naturale e la fine dell'attesa
 * dell'uscita lenta (specs/slow-exit/). Entrambi arrivano a
 * [SessionExpiryReceiver]. Separati da [SessionManager], che decide quando
 * armarli; i codici delle richieste stanno in [AlarmIds].
 */
internal class SessionAlarms(private val context: Context) {

    /**
     * Programma una scadenza automatica via AlarmManager: alla fine del tempo
     * scelto, la sessione termina (DND disattivato) anche se l'utente non
     * sta interagendo con l'app in quel momento. Usiamo un allarme "inesatto"
     * (nessun permesso aggiuntivo richiesto): può avere qualche minuto di
     * scarto in Doze, accettabile per questo caso d'uso.
     */
    fun scheduleExpiry(endTime: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endTime, expiryPendingIntent())
    }

    fun cancelExpiry() {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(expiryPendingIntent())
    }

    private fun expiryPendingIntent(): PendingIntent {
        val intent = Intent(context, SessionExpiryReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            AlarmIds.SESSION_EXPIRY,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun scheduleSlowExit(deadline: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deadline, slowExitPendingIntent())
    }

    fun cancelSlowExit() {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(slowExitPendingIntent())
    }

    private fun slowExitPendingIntent(): PendingIntent {
        val intent = Intent(context, SessionExpiryReceiver::class.java)
            .setAction(SessionExpiryReceiver.ACTION_SLOW_EXIT)
        return PendingIntent.getBroadcast(
            context,
            AlarmIds.SLOW_EXIT,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
