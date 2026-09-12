package com.calmotter.app

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Widget 1×1 per la home screen del launcher, in Jetpack Glance.
 *
 * Comportamento al tap (vedi [PauseWidgetTapAction]):
 * - Nessuna sessione attiva → avvia una sessione con l'ultima durata usata
 *   (o 30 min se è la prima volta), senza aprire l'app.
 * - Sessione già attiva → apre BlockOverlayActivity per permettere
 *   lo sblocco anticipato.
 *
 * Il widget si aggiorna:
 * - Ogni 30 minuti (updatePeriodMillis in widget_pause_info.xml, minimo Android).
 * - Subito dopo startSession/endSession via updateAllWidgets().
 */
class PauseGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val sessionManager = SessionManager.getInstance(context)

        provideContent {
            val isActive = sessionManager.isSessionActive()

            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .background(Color(0xFFEAF0E8))
                    .cornerRadius(16.dp)
                    .clickable(actionRunCallback<PauseWidgetTapAction>()),
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                if (isActive) {
                    val minutes = (sessionManager.remainingMillis() / 60_000L).toInt().coerceAtLeast(0)
                    // Testo compatto per lo spazio ridotto del widget
                    val timeText = when {
                        minutes < 5 -> context.getString(R.string.widget_soon)
                        minutes < 60 -> context.getString(R.string.widget_minutes, (minutes / 5) * 5)
                        else -> context.getString(R.string.widget_hours, minutes / 60, minutes % 60)
                    }
                    Image(
                        provider = ImageProvider(R.drawable.ic_pause_widget),
                        contentDescription = null,
                        modifier = GlanceModifier.size(26.dp)
                    )
                    Text(
                        text = timeText,
                        style = TextStyle(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorProvider(day = Color(0xFF3D7A5C), night = Color(0xFF3D7A5C))
                        )
                    )
                } else {
                    Image(
                        provider = ImageProvider(R.drawable.ic_otter_widget),
                        contentDescription = null,
                        modifier = GlanceModifier.size(28.dp)
                    )
                    Text(
                        text = context.getString(R.string.widget_label_idle),
                        style = TextStyle(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorProvider(day = Color(0xFF2C4A3E), night = Color(0xFF2C4A3E))
                        )
                    )
                }
            }
        }
    }
}

/** Gestisce il tap sul widget: avvia una sessione oppure apre la schermata di blocco. */
class PauseWidgetTapAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val sessionManager = SessionManager.getInstance(context)

        if (sessionManager.isSessionActive()) {
            val intent = Intent(context, BlockOverlayActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(intent)
        } else {
            // sessionManager.startSession() salva già l'ultima durata e aggiorna
            // il widget (vedi SessionManager.kt).
            sessionManager.startSession(PauseWidgetProvider.getLastDuration(context))
        }
    }
}

class PauseWidgetProvider : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = PauseGlanceWidget()

    companion object {
        private const val PREFS_WIDGET = "calm_otter_widget"
        private const val KEY_LAST_DURATION = "last_duration"
        private const val DEFAULT_DURATION_MIN = 30

        /**
         * Aggiorna tutti i widget istanziati sulla home screen.
         * Chiamato da SessionManager dopo startSession/endSession.
         */
        fun updateAllWidgets(context: Context) {
            CoroutineScope(Dispatchers.Default).launch {
                PauseGlanceWidget().updateAll(context)
            }
        }

        /** Salva la durata appena usata così il widget la riusa al prossimo tap. */
        fun saveLastDuration(context: Context, minutes: Int) {
            context.getSharedPreferences(PREFS_WIDGET, Context.MODE_PRIVATE)
                .edit().putInt(KEY_LAST_DURATION, minutes).apply()
        }

        fun getLastDuration(context: Context): Int {
            val prefs = context.getSharedPreferences(PREFS_WIDGET, Context.MODE_PRIVATE)
            return prefs.getInt(KEY_LAST_DURATION, DEFAULT_DURATION_MIN)
        }
    }
}
