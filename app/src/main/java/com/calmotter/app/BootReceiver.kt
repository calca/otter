package com.calmotter.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Riceve l'intent BOOT_COMPLETED (e QUICKBOOT_POWERON su alcuni dispositivi
 * Huawei/MIUI) dopo che il sistema è completamente avviato.
 *
 * Se al momento del riavvio era in corso una sessione valida (non scaduta),
 * la lancia immediatamente come nuova task — così l'utente si trova davanti
 * alla schermata di blocco invece che alla home normale.
 *
 * Nota: l'AccessibilityService riparte da solo se era abilitato; questo
 * receiver serve solo a portare subito in primo piano la BlockOverlayActivity,
 * perché il servizio da solo non apre nuove Activity al boot.
 *
 * Se invece la sessione era scaduta durante il riavvio (tempo expiryTime già
 * passato), SessionManager.isSessionActive() la chiude automaticamente e
 * qui non facciamo nulla — l'utente trova il telefono normale.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            // Ora o fuso cambiati: gli allarmi a ora locale (pause programmate,
            // nota della domenica) vanno ricalcolati. Nient'altro da fare.
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
            ACTION_EXACT_ALARM_PERMISSION_CHANGED -> AppScheduler.reconcile(context)
            Intent.ACTION_BOOT_COMPLETED, "android.intent.action.QUICKBOOT_POWERON" -> onBoot(context)
        }
    }

    private fun onBoot(context: Context) {
        val sessionManager = SessionManager.getInstance(context)
        // isSessionActive() chiude da sola una pausa scaduta durante il riavvio.
        if (sessionManager.isSessionActive()) sessionManager.reapplyAfterBoot()
        // Gli allarmi non sopravvivono al riavvio.
        AppScheduler.reconcile(context)
        if (!sessionManager.isSessionActive()) return

        val blockIntent = Intent(context, BlockOverlayActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        context.startActivity(blockIntent)
    }

    private companion object {
        /** AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED (API 31), come stringa per minSdk 26. */
        const val ACTION_EXACT_ALARM_PERMISSION_CHANGED = "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
    }
}
