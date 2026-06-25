package com.calmotter.app

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.telecom.TelecomManager
import android.view.accessibility.AccessibilityEvent

/**
 * Servizio di accessibilità che osserva quale app va in primo piano.
 *
 * Se è attiva una sessione di pausa e l'app aperta non è il dialer di
 * default (o questa stessa app, o la systemUI), riporta immediatamente
 * l'utente sulla schermata di blocco.
 *
 * LIMITE NOTO (da comunicare chiaramente all'utente): questo è un blocco
 * "soft". L'utente può sempre disattivare il servizio da Impostazioni >
 * Accessibilità, o avviare il telefono in Safe Mode, bypassando il blocco.
 * Un blocco realmente a prova di utente richiederebbe il provisioning
 * dell'app come Device Owner, fuori dallo scope di questo MVP.
 */
class AppBlockerAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString() ?: return
        val sessionManager = SessionManager(applicationContext)
        if (!sessionManager.isSessionActive()) return
        if (packageName in allowedPackages()) return

        val intent = Intent(this, BlockOverlayActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    private fun allowedPackages(): Set<String> {
        val telecomManager = getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        val extraAllowed = AllowedAppsManager(applicationContext).getAllowedPackages()
        return setOfNotNull(
            telecomManager?.defaultDialerPackage, // app Telefono di default del dispositivo
            "com.android.systemui",               // status bar, tendina notifiche, schermata di blocco
            "android",                             // dialog di sistema
            packageName                            // questa stessa app, per mostrare il blocco
        ) + extraAllowed
    }

    override fun onInterrupt() {}
}
