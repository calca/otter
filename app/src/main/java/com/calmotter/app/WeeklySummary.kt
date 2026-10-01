package com.calmotter.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.calmotter.app.ui.screens.weeklyChartData
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/**
 * La nota della domenica sera (specs/weekly-summary/): "Questa settimana: 7
 * pause di calma." Una sola notifica, solo se c'è stata almeno una pausa,
 * solo il numero di pause — mai ore, serie, obiettivi mancati, confronti o
 * urgenza: riconosce l'abitudine, non la misura. Attiva di default, si spegne
 * dalle Impostazioni senza password.
 *
 * Il conteggio è lo stesso della scheda settimanale della Cronologia
 * ([weeklyChartData], gli ultimi sette giorni), così i due numeri coincidono
 * sempre.
 */
object WeeklySummary {
    private const val PREFS_NAME = "calm_otter_weekly_note"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_PENDING = "pending"
    private const val CHANNEL_ID = "calm_otter_weekly_note"
    private const val NOTIFICATION_ID = 5000
    private const val REQUEST_CODE = 5001
    private const val WINDOW_MILLIS = 30 * 60_000L

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_ENABLED, enabled) }
        if (enabled) arm(context) else cancel(context)
    }

    /** Le pause da contare: quelle della scheda settimanale; 0 = nessuna nota. */
    fun pauseCount(sessions: List<SessionRecord>): Int = weeklyChartData(sessions).second

    /** La prossima domenica alle 20:00 dopo [now] (ora locale, ora legale compresa). */
    @VisibleForTesting
    internal fun nextSundayEvening(now: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        val current = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(now), zone)
        var candidate = current.toLocalDate()
            .with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            .atTime(LocalTime.of(20, 0))
            .atZone(zone)
        if (!candidate.isAfter(current)) candidate = candidate.plusWeeks(1)
        return candidate.toInstant().toEpochMilli()
    }

    /** Arma la prossima nota. Idempotente: si chiama a ogni apertura e al riavvio. */
    fun arm(context: Context) {
        if (!isEnabled(context)) return
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setWindow(AlarmManager.RTC_WAKEUP, nextSundayEvening(System.currentTimeMillis()), WINDOW_MILLIS, pendingIntent(context))
    }

    private fun cancel(context: Context) {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pendingIntent(context))
        prefs(context).edit { remove(KEY_PENDING) }
    }

    /** È l'ora della nota: la manda, o la rimanda alla fine della pausa in corso. */
    internal fun onAlarm(context: Context) {
        if (!isEnabled(context)) return
        if (SessionManager.getInstance(context).isSessionActive()) {
            prefs(context).edit { putBoolean(KEY_PENDING, true) }
        } else {
            post(context)
        }
        arm(context)
    }

    /** Chiamato a fine pausa: se la nota era stata rimandata, è il momento. */
    fun onSessionEnded(context: Context) {
        if (!prefs(context).getBoolean(KEY_PENDING, false)) return
        prefs(context).edit { remove(KEY_PENDING) }
        if (isEnabled(context)) post(context)
    }

    private fun post(context: Context) {
        val count = pauseCount(SessionHistoryManager.getInstance(context).getAll())
        if (count == 0) return
        // Controllo esplicito qui e non solo in notificationsAllowed(): lint
        // lo riconosce solo accanto a notify(). Il permesso non si chiede
        // mai per questa sola funzione.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.weekly_note_channel), NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, HistoryActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile_otter)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.resources.getQuantityString(R.plurals.weekly_note_pauses, count, count))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    fun notificationsAllowed(context: Context): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, REQUEST_CODE, Intent(context, WeeklySummaryReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

class WeeklySummaryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = WeeklySummary.onAlarm(context)
}
