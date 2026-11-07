package com.calmotter.app

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
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
import com.calmotter.app.ui.theme.CalmOtterTheme
import kotlin.math.abs

/**
 * Cosa mostra la bolla (specs/floating-bubble/). Derivato, mai salvato.
 */
sealed interface BubbleState {
    data object Hidden : BubbleState
    /** Una pausa programmata o a sorpresa parte fra poco. */
    data class Upcoming(val startsAt: Long) : BubbleState
    /** Una pausa è in corso e finisce a [endsAt]. */
    data class Active(val endsAt: Long) : BubbleState
}

/**
 * La decisione, pura e provabile da sola. La bolla c'è solo se l'utente l'ha
 * accesa e solo mentre sta guardando un'altra app: dentro Calm Otter la
 * stessa informazione è già a schermo.
 */
fun bubbleState(
    enabled: Boolean,
    ownAppInForeground: Boolean,
    sessionActive: Boolean,
    sessionEndsAt: Long,
    upcomingStartsAt: Long,
    now: Long,
): BubbleState = when {
    !enabled || ownAppInForeground -> BubbleState.Hidden
    sessionActive && sessionEndsAt > now -> BubbleState.Active(sessionEndsAt)
    !sessionActive && upcomingStartsAt > now -> BubbleState.Upcoming(upcomingStartsAt)
    else -> BubbleState.Hidden
}

/** Minuti da mostrare, arrotondati per eccesso: "0 min" non ha senso finché c'è tempo. */
fun bubbleMinutes(untilMillis: Long, now: Long): Int =
    (((untilMillis - now).coerceAtLeast(0L) + 59_999L) / 60_000L).toInt().coerceAtLeast(1)

/** Impostazione e posizione della bolla: preferenze semplici, nessun dato sensibile. */
object BubbleSettings {
    private const val PREFS = "floating_bubble"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_X = "x"
    private const val KEY_Y = "y"

    /** Anticipo dell'avviso prima di una pausa programmata (stesso della notifica). */
    const val LEAD_MINUTES = 5

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_ENABLED, enabled) }
        AppBlockerAccessibilityService.onBubbleSettingChanged()
    }

    /** x: false = bordo sinistro, true = destro; y in pixel. -1 = mai spostata. */
    fun onRightEdge(context: Context): Boolean = prefs(context).getBoolean(KEY_X, true)
    fun y(context: Context): Int = prefs(context).getInt(KEY_Y, -1)

    fun savePosition(context: Context, rightEdge: Boolean, y: Int) =
        prefs(context).edit { putBoolean(KEY_X, rightEdge); putInt(KEY_Y, y) }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/**
 * La finestra della bolla, disegnata dal servizio di accessibilità con
 * TYPE_ACCESSIBILITY_OVERLAY (come la finestra-ponte): nessun permesso in
 * più. Non prende il focus, quindi non tocca tastiera né tasto back. Un
 * tocco apre Calm Otter (che durante una pausa mostra il blocco) e non
 * allenta niente; trascinata, si aggancia al bordo più vicino.
 */
internal class FloatingBubbleWindow(private val service: android.accessibilityservice.AccessibilityService) {
    private var view: ComposeView? = null
    private var owner: OverlayLifecycleOwner? = null
    private var params: WindowManager.LayoutParams? = null
    private var text by mutableStateOf("")
    private var description by mutableStateOf("")

    val isShown: Boolean get() = view != null

    fun show(label: String, spoken: String) {
        text = label
        description = spoken
        if (view != null) return
        val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val lifecycleOwner = OverlayLifecycleOwner()
        val appTheme = ThemeManager.getTheme(service)
        val composeView = ComposeView(service).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent {
                CalmOtterTheme(appTheme = appTheme) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shadowElevation = 6.dp,
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                            Text(text, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            contentDescription = description
            isClickable = true
            setOnClickListener {
                service.startActivity(
                    Intent(service, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                )
            }
        }
        val metrics = service.resources.displayMetrics
        val layout = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or if (BubbleSettings.onRightEdge(service)) Gravity.END else Gravity.START
            x = (8 * metrics.density).toInt()
            y = BubbleSettings.y(service).takeIf { it >= 0 } ?: (metrics.heightPixels / 4)
        }
        composeView.setOnTouchListener(DragListener(windowManager, layout, composeView))
        try {
            lifecycleOwner.start()
            windowManager.addView(composeView, layout)
            view = composeView
            owner = lifecycleOwner
            params = layout
        } catch (e: Exception) {
            android.util.Log.w("FloatingBubble", "Impossibile mostrare la bolla", e)
            lifecycleOwner.destroy()
        }
    }

    fun update(label: String, spoken: String) {
        text = label
        description = spoken
        view?.contentDescription = spoken
    }

    fun hide() {
        val v = view ?: return
        view = null
        params = null
        try {
            (service.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v)
        } catch (e: Exception) {
            android.util.Log.w("FloatingBubble", "Impossibile rimuovere la bolla", e)
        }
        owner?.destroy()
        owner = null
    }

    /** Trascinamento: solo oltre la soglia del sistema, altrimenti resta un tocco (e un click per TalkBack). */
    private inner class DragListener(
        private val windowManager: WindowManager,
        private val layout: WindowManager.LayoutParams,
        private val target: View,
    ) : View.OnTouchListener {
        private val slop = ViewConfiguration.get(service).scaledTouchSlop
        private var downX = 0f
        private var downY = 0f
        private var startY = 0
        private var startX = 0
        private var dragging = false

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            val fromEnd = layout.gravity and Gravity.END == Gravity.END
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY
                    startX = layout.x; startY = layout.y
                    dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) dragging = true
                    if (dragging) {
                        // Con gravity END la x cresce verso l'interno da destra.
                        layout.x = startX + (if (fromEnd) -dx else dx).toInt()
                        layout.y = startY + dy.toInt()
                        windowManager.updateViewLayout(target, layout)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        val width = service.resources.displayMetrics.widthPixels
                        val center = (if (fromEnd) width - layout.x - target.width / 2 else layout.x + target.width / 2)
                        val right = center > width / 2
                        layout.gravity = Gravity.TOP or if (right) Gravity.END else Gravity.START
                        layout.x = (8 * service.resources.displayMetrics.density).toInt()
                        layout.y = layout.y.coerceIn(0, service.resources.displayMetrics.heightPixels - target.height)
                        windowManager.updateViewLayout(target, layout)
                        BubbleSettings.savePosition(service, right, layout.y)
                        dragging = false
                        return true
                    }
                }
            }
            return dragging
        }
    }
}

/**
 * Il minimo perché un `ComposeView` viva dentro una finestra aggiunta a mano
 * da un servizio: Compose cerca nell'albero delle view un LifecycleOwner e un
 * SavedStateRegistryOwner. Condiviso fra la finestra-ponte e la bolla.
 */
internal class OverlayLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
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
