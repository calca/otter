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
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.text.DateFormat
import java.util.Date

/**
 * Allarmi delle pause programmate (specs/scheduled-pauses/): uno cinque
 * minuti prima (avviso silenzioso) e uno alla partenza. Inesatti di
 * proposito (`setWindow`): gli allarmi esatti chiedono un permesso negato di
 * default da Android 14, e una pausa che parte alle 21:02 invece che alle
 * 21:00 va bene. Ogni giro arma solo la prossima occorrenza.
 */
object ScheduleAlarms {
    private const val HEADS_UP_MILLIS = 5 * 60_000L
    private const val WINDOW_MILLIS = 2 * 60_000L

    /** Riarma tutte le programmazioni: dopo una modifica, al riavvio, a ogni apertura (rete di sicurezza). */
    fun armAll(context: Context) {
        ScheduleManager.getInstance(context).all().forEach { arm(context, it) }
    }

    fun arm(context: Context, schedule: ScheduledPause) {
        cancel(context, schedule.id)
        if (!schedule.enabled) return
        val now = System.currentTimeMillis()
        val at = nextOccurrence(schedule, now) ?: return
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (at - HEADS_UP_MILLIS > now) {
            am.setWindow(AlarmManager.RTC_WAKEUP, at - HEADS_UP_MILLIS, WINDOW_MILLIS,
                pendingIntent(context, schedule.id, at, ScheduledPauseReceiver.ACTION_HEADS_UP))
        }
        am.setWindow(AlarmManager.RTC_WAKEUP, at, WINDOW_MILLIS,
            pendingIntent(context, schedule.id, at, ScheduledPauseReceiver.ACTION_START))
    }

    fun cancel(context: Context, id: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context, id, 0L, ScheduledPauseReceiver.ACTION_HEADS_UP))
        am.cancel(pendingIntent(context, id, 0L, ScheduledPauseReceiver.ACTION_START))
    }

    private fun pendingIntent(context: Context, id: Int, at: Long, action: String): PendingIntent {
        val intent = Intent(context, ScheduledPauseReceiver::class.java)
            .setAction(action)
            .putExtra(ScheduledPauseReceiver.EXTRA_ID, id)
            .putExtra(ScheduledPauseReceiver.EXTRA_AT, at)
        val code = 3000 + id * 2 + if (action == ScheduledPauseReceiver.ACTION_START) 1 else 0
        return PendingIntent.getBroadcast(
            context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

class ScheduledPauseReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val manager = ScheduleManager.getInstance(context)
        val schedule = manager.byId(intent.getIntExtra(EXTRA_ID, 0)) ?: return
        val at = intent.getLongExtra(EXTRA_AT, 0L)
        val sessionManager = SessionManager.getInstance(context)

        when (intent.action) {
            ACTION_HEADS_UP -> {
                if (schedule.enabled && at > schedule.skipUntil && !sessionManager.isSessionActive()) {
                    notify(context, HEADS_UP_NOTIFICATION_ID + schedule.id,
                        context.getString(R.string.schedule_heads_up, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(at))))
                }
            }
            ACTION_START -> {
                when {
                    !schedule.enabled -> Unit
                    at <= schedule.skipUntil -> manager.save(schedule.copy(skipUntil = 0L))
                    sessionManager.isSessionActive() -> Unit // mai una seconda pausa, mai allungare quella in corso
                    !permissionsGranted(context) ->
                        notify(context, START_FAILED_NOTIFICATION_ID, context.getString(R.string.schedule_missing_permissions))
                    else -> sessionManager.startSession(
                        schedule.durationMinutes,
                        // Nessuno l'ha scelta adesso: non cambia la durata proposta in Home.
                        rememberDuration = false,
                        profileId = schedule.profileId,
                    )
                }
                manager.byId(schedule.id)?.let { ScheduleAlarms.arm(context, it) }
            }
        }
    }

    private fun permissionsGranted(context: Context): Boolean =
        BuildConfig.DEBUG || (isAccessibilityServiceEnabled(context) && isDndAccessGranted(context))

    private fun notify(context: Context, id: Int, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.schedule_channel_name), NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile_otter)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    companion object {
        const val ACTION_HEADS_UP = "com.calmotter.app.action.SCHEDULE_HEADS_UP"
        const val ACTION_START = "com.calmotter.app.action.SCHEDULE_START"
        const val EXTRA_ID = "schedule_id"
        const val EXTRA_AT = "schedule_at"
        private const val CHANNEL_ID = "calm_otter_schedules"
        private const val HEADS_UP_NOTIFICATION_ID = 4000
        private const val START_FAILED_NOTIFICATION_ID = 3999
    }
}
