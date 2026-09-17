package com.calmotter.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.TelecomManager
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.calmotter.app.ui.mascot.OtterFloatMark
import com.calmotter.app.ui.screens.OtterAnchoredScreen
import com.calmotter.app.ui.screens.ProgressRing
import com.calmotter.app.ui.theme.CalmOtterTheme

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
    private var bridgeOwner: BridgeOverlayOwner? = null
    private val dismissBridgeOverlayRunnable = Runnable { dismissBridgeOverlay() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        // **Il config XML da solo non basta.** `res/xml/
        // accessibility_service_config.xml` dichiara già
        // `typeWindowStateChanged`, e l'APK lo contiene correttamente
        // (verificato con `aapt2 dump xmltree`: accessibilityEventTypes=0x20),
        // ma il servizio veniva comunque collegato con `eventTypes = 0` —
        // cioè iscritto a *nessun* evento. Il sistema non gli consegnava mai
        // un TYPE_WINDOW_STATE_CHANGED e il blocco delle app non scattava
        // mai, pur con tutti i permessi concessi: il bug principale
        // dell'app, riprodotto e diagnosticato su emulatore API 37
        // (`dumpsys accessibility` mostrava `eventTypes=` vuoto per questo
        // servizio, e `getServiceInfo().eventTypes` valeva 0 già dentro
        // questa callback).
        //
        // Riaffermare qui l'info a runtime è la forma robusta: vale su
        // qualunque versione, non dipende da come il sistema ha (o non ha)
        // applicato il meta-data, e non toglie nulla — il config XML resta
        // comunque necessario perché il servizio compaia in Impostazioni
        // con etichetta e descrizione prima ancora di essere attivato.
        serviceInfo = (serviceInfo ?: AccessibilityServiceInfo()).apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = EVENT_NOTIFICATION_TIMEOUT_MS
        }
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
            val owner = BridgeOverlayOwner()
            val appTheme = ThemeManager.getTheme(this)
            val fraction = sessionProgressFraction()
            val realSystemBarsBottomPx = realSystemBarsBottomPx(windowManager)

            val view = ComposeView(this).apply {
                // Sfondo a livello View, dipinto subito dal sistema: copre
                // l'app bloccata già al primo frame, senza aspettare che
                // Compose abbia composto. Compose ci disegna sopra lo sfondo
                // vero della palette un frame dopo.
                setBackgroundColor(Color.BLACK)
                setViewTreeLifecycleOwner(owner)
                setViewTreeViewModelStoreOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
                setContent {
                    CalmOtterTheme(appTheme = appTheme) {
                        // **Compensazione degli inset mancanti.** Questa
                        // finestra non riceve gli stessi inset di sistema di
                        // un'Activity: misurato sull'emulatore, vede la barra
                        // di stato ma *non* quella di navigazione (bottom=0
                        // invece di 72px), quindi `safeDrawingPadding()`
                        // dentro [OtterAnchoredScreen] calcolava un'area alta
                        // 72px in più e centrava l'otter 36px più in basso
                        // della schermata vera — un salto visibile al cambio.
                        //
                        // Si toglie la differenza come padding, invece di
                        // ricalcolare qui la posizione dell'otter: la formula
                        // resta una sola, quella di [OtterAnchoredScreen]
                        // (vedi "due numeri che devono coincidere non sono
                        // un'invariante" in specs/app-blocking-and-home-lock).
                        // Se un giorno la finestra ricevesse gli inset giusti,
                        // la differenza diventa zero e non cambia nulla.
                        val density = LocalDensity.current
                        val missingBottom = with(density) {
                            (realSystemBarsBottomPx - WindowInsets.safeDrawing.getBottom(density))
                                .coerceAtLeast(0)
                                .toDp()
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = missingBottom)
                                // Opaco: a differenza di un'Activity, questa
                                // finestra non ha dietro di sé lo sfondo del
                                // tema XML su cui calmBackground() (un velo
                                // traslucido) conta — senza questa base si
                                // vedrebbe l'app bloccata attraverso il velo.
                                .background(MaterialTheme.colorScheme.background)
                        ) {
                            // **Lo stesso contenitore della schermata vera.**
                            // La posizione dell'otter non è ricalcolata qui:
                            // è OtterAnchoredScreen a deciderla, esattamente
                            // come per BlockScreen (stesso horizontalPadding,
                            // stesso markSize, nessun header). È l'unico modo
                            // per garantire che l'otter non si sposti di un
                            // pixel quando la schermata vera prende il posto
                            // di questa — vedi la storia dell'"otter che
                            // scivola" in specs/app-blocking-and-home-lock.
                            OtterAnchoredScreen(
                                horizontalPadding = 40.dp,
                                otter = {
                                    ProgressRing(fraction = fraction, modifier = Modifier.size(176.dp))
                                    OtterFloatMark(markSize = 124.dp)
                                },
                                below = {},
                            )
                        }
                    }
                }
            }

            owner.start()
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                // La finestra deve occupare *tutto* il display e ricevere gli
                // inset delle barre di sistema come li riceve un'Activity
                // edge-to-edge, non venire adattata dal window manager: senza
                // questi flag il `safeDrawingPadding()` dentro
                // [OtterAnchoredScreen] calcolava un'area sicura diversa da
                // quella della schermata vera e l'otter del ponte finiva 42px
                // più in basso (misurato: maschera a 1284-1409 invece di
                // 1242-1367), cioè un salto visibile al cambio.
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR,
                PixelFormat.OPAQUE
            ).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // Stessa ragione: nessun tipo di inset "consumato" dal
                    // window manager, li gestisce il contenuto come nell'app.
                    fitInsetsTypes = 0
                }
            }
            windowManager.addView(view, params)
            bridgeOverlay = view
            bridgeOwner = owner
            mainHandler.postDelayed(dismissBridgeOverlayRunnable, BRIDGE_TIMEOUT_MS)
        } catch (e: Exception) {
            Log.w(TAG, "Impossibile mostrare la finestra-ponte di blocco", e)
        }
    }

    /**
     * Inset inferiore *reale* delle barre di sistema, letto dal window
     * manager e non dalla finestra-ponte — è il valore che l'Activity di
     * blocco riceve, e serve come riferimento per la compensazione descritta
     * in [showBridgeOverlay]. Sotto API 30 non c'è un modo diretto: si
     * ritorna 0, cioè "nessuna compensazione", che è anche il comportamento
     * che si aveva prima.
     */
    private fun realSystemBarsBottomPx(windowManager: WindowManager): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return 0
        return windowManager.currentWindowMetrics.windowInsets
            .getInsets(android.view.WindowInsets.Type.systemBars())
            .bottom
    }

    /**
     * Stessa frazione che [BlockScreen] passa al proprio [ProgressRing]:
     * l'anello della finestra-ponte parte già al punto giusto invece di
     * saltare da vuoto al valore reale quando arriva la schermata vera.
     */
    private fun sessionProgressFraction(): Float {
        val sessionManager = SessionManager.getInstance(applicationContext)
        val total = sessionManager.totalMillis()
        if (total <= 0L) return 0f
        return (1f - sessionManager.remainingMillis().toFloat() / total.toFloat())
            .coerceIn(0f, 1f)
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
        bridgeOwner?.destroy()
        bridgeOwner = null
    }

    /**
     * Minimo indispensabile perché un `ComposeView` possa vivere dentro una
     * finestra aggiunta a mano da un servizio: Compose pretende dall'albero
     * delle view un `LifecycleOwner` e un `SavedStateRegistryOwner` (li
     * troverebbe da sé solo dentro un'Activity/Fragment). Vita legata alla
     * singola comparsa della finestra-ponte: creato in
     * [showBridgeOverlay], distrutto in [dismissBridgeOverlay].
     */
    private class BridgeOverlayOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateRegistryController = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val viewModelStore: ViewModelStore = ViewModelStore()
        override val savedStateRegistry: SavedStateRegistry
            get() = savedStateRegistryController.savedStateRegistry

        fun start() {
            savedStateRegistryController.performAttach()
            savedStateRegistryController.performRestore(null)
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        }

        fun destroy() {
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
            viewModelStore.clear()
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

        /** Zero, non i 100ms del config XML: `notificationTimeout` è il
         * ritardo minimo con cui il sistema consegna un evento dello stesso
         * tipo, quindi ogni millisecondo qui è tempo in cui l'app bloccata
         * resta scoperta. Non c'è motivo di frenare: il gestore esce subito
         * quando non c'è una sessione attiva, che è il caso comune. */
        private const val EVENT_NOTIFICATION_TIMEOUT_MS = 0L

        @Volatile private var instance: AppBlockerAccessibilityService? = null

        /** Chiamato da [BlockOverlayActivity.onResume]: la vera schermata di
         * blocco è visibile, la finestra-ponte non serve più. */
        fun notifyBlockScreenVisible() {
            instance?.dismissBridgeOverlay()
        }
    }
}
