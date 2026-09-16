package com.calmotter.app

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.telecom.TelecomManager
import android.util.Log
import android.view.View
import android.view.WindowManager
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

    private val mainHandler = Handler(Looper.getMainLooper())
    private var bridgeOverlay: View? = null
    private val dismissBridgeOverlayRunnable = Runnable { dismissBridgeOverlay() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString() ?: return
        val sessionManager = SessionManager.getInstance(applicationContext)
        if (!sessionManager.isSessionActive()) return
        if (packageName in allowedPackages()) return

        // Copre subito lo schermo dell'app non consentita, PRIMA di provare ad
        // avviare BlockOverlayActivity: su alcuni dispositivi (osservato su
        // Samsung/One UI) l'avvio di un'Activity da un servizio in background
        // può essere ritardato o soppresso dal sistema (restrizioni "background
        // activity start" introdotte da Android 10), lasciando l'app bloccata
        // visibile e utilizzabile per tutto quel tempo. Questa finestra-ponte
        // usa TYPE_ACCESSIBILITY_OVERLAY, l'unico tipo di finestra che un
        // servizio di accessibilità può disegnare senza permessi aggiuntivi
        // (niente SYSTEM_ALERT_WINDOW da chiedere all'utente), e viene rimossa
        // da BlockOverlayActivity stessa non appena è visibile
        // (vedi [notifyBlockScreenVisible]), o comunque entro
        // [BRIDGE_TIMEOUT_MS] come rete di sicurezza se l'Activity non si
        // presenta mai — non deve restare a coprire lo schermo per sempre.
        showBridgeOverlay()

        val intent = Intent(this, BlockOverlayActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    private fun showBridgeOverlay() {
        if (bridgeOverlay != null) return
        try {
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val view = View(this).apply { setBackgroundColor(Color.BLACK) }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.OPAQUE
            )
            windowManager.addView(view, params)
            bridgeOverlay = view
            mainHandler.postDelayed(dismissBridgeOverlayRunnable, BRIDGE_TIMEOUT_MS)
        } catch (e: Exception) {
            Log.w(TAG, "Impossibile mostrare la finestra-ponte di blocco", e)
        }
    }

    private fun dismissBridgeOverlay() {
        val view = bridgeOverlay ?: return
        bridgeOverlay = null
        mainHandler.removeCallbacks(dismissBridgeOverlayRunnable)
        try {
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(view)
        } catch (e: Exception) {
            Log.w(TAG, "Impossibile rimuovere la finestra-ponte di blocco", e)
        }
    }

    private fun allowedPackages(): Set<String> {
        val telecomManager = getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        val extraAllowed = AllowedAppsManager.getInstance(applicationContext).getAllowedPackages()
        return setOfNotNull(
            telecomManager?.defaultDialerPackage, // app Telefono di default del dispositivo
            "com.android.systemui",               // status bar, tendina notifiche, schermata di blocco
            "android",                             // dialog di sistema
            packageName                            // questa stessa app, per mostrare il blocco
        ) + extraAllowed
    }

    override fun onInterrupt() {
        dismissBridgeOverlay()
    }

    override fun onDestroy() {
        dismissBridgeOverlay()
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AppBlockerA11yService"
        private const val BRIDGE_TIMEOUT_MS = 1500L

        @Volatile private var instance: AppBlockerAccessibilityService? = null

        /** Chiamato da [BlockOverlayActivity.onResume]: la vera schermata di
         * blocco è visibile, la finestra-ponte non serve più. */
        fun notifyBlockScreenVisible() {
            instance?.dismissBridgeOverlay()
        }
    }
}
