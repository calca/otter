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
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON") return

        val sessionManager = SessionManager(context)
        if (!sessionManager.isSessionActive()) return

        // Ripristina DND e allarme di scadenza, azzerati dal riavvio
        sessionManager.reapplyAfterBoot()

        val blockIntent = Intent(context, BlockOverlayActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        context.startActivity(blockIntent)
    }
}
