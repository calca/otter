package com.calmotter.app.nfc

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.calmotter.app.AppForeground
import com.calmotter.app.BuildConfig
import com.calmotter.app.PasswordManager
import com.calmotter.app.QuickTogetherActivity
import com.calmotter.app.R
import com.calmotter.app.SessionManager
import com.calmotter.app.durationPillLabel
import com.calmotter.app.isAccessibilityServiceEnabled
import com.calmotter.app.isDndAccessGranted

/**
 * Il lato "carta" (B) di "Avvicina i telefoni" (specs/nfc-quick-together/):
 * cosa risponde questo telefono quando un altro gli si avvicina, e cosa fa
 * della proposta ricevuta. Chiamato da [GroupPauseHceService] sul thread NFC.
 */
object QuickTogetherInbox {

    private const val CHANNEL_ID = "calm_otter_together"
    const val NOTIFICATION_ID = 5001

    /** Se questo telefono può unirsi adesso: stesso controllo del tocco sull'otter. */
    fun state(context: Context): QuickTogetherProtocol.State = when {
        SessionManager.getInstance(context).isSessionActive() -> QuickTogetherProtocol.State.IN_PAUSE
        !PasswordManager.getInstance(context).isPasswordSet() -> QuickTogetherProtocol.State.NOT_SET_UP
        !BuildConfig.DEBUG && !(isAccessibilityServiceEnabled(context) && isDndAccessGranted(context)) ->
            QuickTogetherProtocol.State.NOT_SET_UP
        else -> QuickTogetherProtocol.State.READY
    }

    /**
     * La proposta è arrivata ed è valida. Con l'app aperta compare subito il
     * conto alla rovescia; con l'app chiusa o in background una notifica con
     * "Unisciti" — da un servizio HCE in background Android non lascia aprire
     * un'Activity.
     */
    fun deliver(context: Context, proposal: QuickTogetherProtocol.Proposal, receivedAt: Long = System.currentTimeMillis()) {
        val startAt = receivedAt + proposal.startDelayMillis
        val endAt = startAt + proposal.durationMinutes * 60_000L
        val intent = QuickTogetherActivity.cardIntent(
            context,
            companion = proposal.name,
            durationMinutes = proposal.durationMinutes,
            activityId = proposal.activityId,
            groupTag = proposal.groupTag,
            startAt = startAt,
            endAt = endAt,
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (AppForeground.isForeground) {
            context.startActivity(intent)
        } else {
            notify(context, proposal, intent, endAt - receivedAt)
        }
    }

    private fun notify(context: Context, proposal: QuickTogetherProtocol.Proposal, intent: Intent, timeoutMillis: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.quick_together_channel), NotificationManager.IMPORTANCE_HIGH)
        )
        val open = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = context.getString(
            R.string.quick_together_notification,
            proposal.name,
            durationPillLabel(proposal.durationMinutes),
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile_otter)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setContentIntent(open)
            .addAction(0, context.getString(R.string.quick_together_join), open)
            .setAutoCancel(true)
            .setTimeoutAfter(timeoutMillis)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}
