package com.calmotter.app

import android.Manifest
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.PowerManager
import android.telecom.TelecomManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Il cablaggio della pausa dopo un uso lungo (specs/usage-pause/): vive
 * dentro [AppBlockerAccessibilityService], legato dal sistema finché
 * Accessibilità è attiva, quindi con il processo vivo senza un servizio in
 * primo piano. Ascolta lo schermo (SCREEN_ON/OFF e USER_PRESENT non si
 * dichiarano nel manifest: serve un ricevitore registrato da qualcosa che
 * resta vivo) e l'app in primo piano che il servizio vede già.
 *
 * Nessun tic ogni minuto: un solo callback puntato sulla prossima soglia
 * (l'avviso, poi la partenza), rimirato a ogni evento che cambia il conto.
 * Le soglie sono in minuti *d'uso*: il conto corre solo a schermo acceso,
 * quando il telefono non dorme, quindi un Handler basta.
 */
internal class UsageWatcher(private val context: Context, private val handler: Handler) {

    private val manager = UsagePauseManager.getInstance(context)
    private val counter = UsageCounter(manager.counterState())
    private var foregroundPackage: String? = null
    private val tick = Runnable { evaluate() }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> screenOff()
                Intent.ACTION_USER_PRESENT -> screenOn()
                // Senza blocco schermo USER_PRESENT può non arrivare: acceso
                // e non bloccato vale come sbloccato.
                Intent.ACTION_SCREEN_ON -> if (!isKeyguardLocked()) screenOn()
            }
        }
    }

    fun start() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(context, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        if (active()) sync()
    }

    /**
     * Si conta solo con la funzione accesa: spenta, nessun dato sull'uso del
     * telefono, nemmeno in locale (è anche quello che dice la spiegazione
     * dell'accessibilità).
     */
    private fun active(): Boolean = manager.rule().enabled

    /** Allinea il conto a com'è lo schermo adesso: il processo può essere ripartito mentre il telefono era in uso. */
    private fun sync() {
        val pm = context.getSystemService(PowerManager::class.java)
        if (pm.isInteractive && !isKeyguardLocked()) screenOn() else screenOff()
        updateExcluded()
    }

    fun stop() {
        handler.removeCallbacks(tick)
        runCatching { context.unregisterReceiver(screenReceiver) }
        // Accessibilità spenta: non si conta più (spec, User Story 3).
        counter.screenOff(System.currentTimeMillis())
        save()
    }

    /** L'app a schermo intero in primo piano, già filtrata dal servizio (niente tastiere né systemUI). */
    fun onForegroundApp(packageName: String) {
        foregroundPackage = packageName
        if (active()) updateExcluded()
    }

    /** Una pausa è partita o finita, di qualunque tipo: il conto riparte da zero. */
    fun onSessionChanged() {
        val now = System.currentTimeMillis()
        cancelHeadsUp()
        manager.clearRound()
        if (!active()) return
        counter.reset(now)
        updateExcluded()
    }

    /** "Non ora": la pausa si sposta di quindici minuti d'uso. */
    fun onNotNow() {
        val now = System.currentTimeMillis()
        val rule = manager.rule()
        manager.extraMillis = counter.usedMillis(now) + USAGE_NOT_NOW_MILLIS - rule.afterMinutes * 60_000L
        manager.headsUpShown = false
        cancelHeadsUp()
        evaluate()
    }

    /** La regola è cambiata in Impostazioni. */
    fun onRuleChanged() {
        if (active()) {
            sync()
        } else {
            // Spenta: il conto si cancella, non resta in attesa.
            handler.removeCallbacks(tick)
            cancelHeadsUp()
            manager.clearRound()
            counter.state = UsageState()
            save()
        }
    }

    private fun screenOn() {
        if (!active()) return
        val now = System.currentTimeMillis()
        if (counter.screenOn(now)) {
            // Una pausa vera (5 minuti a schermo spento): l'avviso non serve più.
            cancelHeadsUp()
            manager.clearRound()
        }
        save()
        evaluate()
    }

    private fun screenOff() {
        if (!active()) return
        counter.screenOff(System.currentTimeMillis())
        save()
        handler.removeCallbacks(tick)
    }

    /**
     * Il conto si ferma in chiamata, in un'app che resta durante la pausa e
     * durante una pausa. Le app che restano sono un patto di fiducia: se fra
     * queste c'è un social, è una scelta (deciso con l'utente).
     */
    private fun updateExcluded() {
        val now = System.currentTimeMillis()
        val pkg = foregroundPackage
        val sessionActive = SessionManager.getInstance(context).isSessionActive()
        val excludedApp = pkg != null && (
            BlockPolicy.isCallScreen(pkg) ||
                pkg == defaultDialer() ||
                pkg in AllowedAppsManager.getInstance(context).packagesFor(manager.rule().profileId)
            )
        counter.setExcluded(now, sessionActive || excludedApp)
        save()
        evaluate()
    }

    private fun evaluate() {
        handler.removeCallbacks(tick)
        val rule = manager.rule()
        if (!rule.enabled || !counter.isRunning) return
        val now = System.currentTimeMillis()

        // Di notte niente pause: un avviso già mostrato sparisce, e si
        // riguarda alle 7:00 (il conto intanto continua).
        if (isUsageQuietTime(now)) {
            if (manager.headsUpShown) {
                cancelHeadsUp()
                manager.headsUpShown = false
            }
            schedule(millisToNextQuietChange(now))
            return
        }
        // Una pausa programmata sta per arrivare, o una pausa è in corso:
        // nessuna proposta, si riguarda fra un minuto.
        val scheduledSoon = nextScheduledStart(ScheduleManager.getInstance(context).all(), now)
            ?.let { it - now <= SCHEDULED_SOON_MILLIS } == true
        if (scheduledSoon || SessionManager.getInstance(context).isSessionActive()) {
            schedule(RECHECK_MILLIS)
            return
        }

        val used = counter.usedMillis(now)
        val limit = rule.afterMinutes * 60_000L + manager.extraMillis
        val untilNight = millisToNextQuietChange(now)
        if (!manager.headsUpShown) {
            if (used >= limit - USAGE_HEADS_UP_MILLIS) {
                // Sempre cinque minuti d'avviso, anche se la soglia è stata
                // passata mentre non si poteva proporre (notte, programmata).
                if (used + USAGE_HEADS_UP_MILLIS > limit) {
                    manager.extraMillis = used + USAGE_HEADS_UP_MILLIS - rule.afterMinutes * 60_000L
                }
                manager.headsUpShown = true
                showHeadsUp(used, rule)
                schedule(minOf(USAGE_HEADS_UP_MILLIS, untilNight))
            } else {
                schedule(minOf(limit - USAGE_HEADS_UP_MILLIS - used, untilNight))
            }
        } else if (used >= limit) {
            startPause(rule)
        } else {
            schedule(minOf(limit - used, untilNight))
        }
    }

    private fun schedule(delayMillis: Long) {
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, delayMillis.coerceAtLeast(1_000L))
    }

    private fun startPause(rule: UsagePauseRule) {
        cancelHeadsUp()
        manager.clearRound()
        if (!ScheduledPauseReceiver.permissionsGranted(context)) {
            ScheduledPauseReceiver.notifyNotStarted(context)
            counter.reset(System.currentTimeMillis())
            save()
            return
        }
        // Come una pausa programmata: nessuno l'ha scelta adesso, non cambia
        // la durata proposta in Home. startSession avvisa il servizio, che
        // copre subito l'app aperta e azzera questo conto (onSessionChanged).
        SessionManager.getInstance(context).startSession(
            rule.durationMinutes,
            rememberDuration = false,
            profileId = rule.profileId,
            origin = PauseOrigin.USAGE,
            originDetail = rule.afterMinutes,
        )
    }

    private fun showHeadsUp(usedMillis: Long, rule: UsagePauseRule) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = context.getSystemService(NotificationManager::class.java)
        // Importanza alta: deve comparire sopra l'app che si sta usando,
        // altrimenti la pausa arriva senza che l'avviso sia stato visto.
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.usage_pause_channel_name), NotificationManager.IMPORTANCE_HIGH)
        )
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = context.getString(R.string.usage_pause_heads_up_title, durationPillLabel(rule.durationMinutes))
        val text = context.getString(R.string.usage_pause_heads_up, durationPillLabel((usedMillis / 60_000L).toInt()))
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile_otter)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setOnlyAlertOnce(true)
        // "Non ora" solo se la regola non è un patto, e al massimo tre volte al giorno.
        if (!rule.locked && manager.notNowLeft() > 0) {
            val notNow = PendingIntent.getBroadcast(
                context, 0,
                Intent(context, UsagePauseReceiver::class.java).setAction(UsagePauseReceiver.ACTION_NOT_NOW),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.addAction(0, context.getString(R.string.usage_pause_not_now), notNow)
        }
        NotificationManagerCompat.from(context).notify(HEADS_UP_NOTIFICATION_ID, builder.build())
    }

    private fun cancelHeadsUp() = NotificationManagerCompat.from(context).cancel(HEADS_UP_NOTIFICATION_ID)

    private fun save() = manager.saveCounterState(counter.state)

    private fun isKeyguardLocked(): Boolean =
        context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true

    private fun defaultDialer(): String? =
        (context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager)?.defaultDialerPackage

    companion object {
        private const val CHANNEL_ID = "calm_otter_usage_pause"
        internal const val HEADS_UP_NOTIFICATION_ID = 4100
        private const val SCHEDULED_SOON_MILLIS = 15 * 60_000L
        private const val RECHECK_MILLIS = 60_000L
    }
}

/** "Non ora" dalla notifica dell'avviso. */
class UsagePauseReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_NOT_NOW) return
        val manager = UsagePauseManager.getInstance(context)
        val rule = manager.rule()
        // Ricontrollato qui: la notifica può essere vecchia di una modifica.
        if (!rule.enabled || rule.locked || manager.notNowLeft() == 0) return
        manager.recordNotNow()
        NotificationManagerCompat.from(context).cancel(UsageWatcher.HEADS_UP_NOTIFICATION_ID)
        AppBlockerAccessibilityService.onUsageNotNow()
    }

    companion object {
        const val ACTION_NOT_NOW = "com.calmotter.app.action.USAGE_NOT_NOW"
    }
}
