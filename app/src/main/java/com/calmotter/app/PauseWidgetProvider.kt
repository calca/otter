package com.calmotter.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews

/**
 * Widget 1×1 per la home screen del launcher.
 *
 * Comportamento al tap:
 * - Nessuna sessione attiva → avvia una sessione con l'ultima durata usata
 *   (o 30 min se è la prima volta), senza aprire l'app.
 * - Sessione già attiva → apre BlockOverlayActivity per permettere
 *   lo sblocco anticipato.
 *
 * Il widget si aggiorna:
 * - Ogni 30 minuti (updatePeriodMillis nel manifest, minimo Android).
 * - Subito dopo startSession/endSession via updateAllWidgets().
 */
class PauseWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id ->
            updateWidget(context, appWidgetManager, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action == ACTION_TAP) {
            handleTap(context)
        }
    }

    // ──────────────────────────────────────────────
    // Logica tap
    // ──────────────────────────────────────────────

    private fun handleTap(context: Context) {
        val sessionManager = SessionManager.getInstance(context)

        if (sessionManager.isSessionActive()) {
            // Sessione attiva: porta alla schermata di blocco
            val intent = Intent(context, BlockOverlayActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(intent)
        } else {
            // Nessuna sessione: avvia con l'ultima durata usata (default 30 min)
            val duration = getLastDuration(context)
            sessionManager.startSession(duration)
            updateAllWidgets(context)
        }
    }

    // ──────────────────────────────────────────────
    // Rendering RemoteViews
    // ──────────────────────────────────────────────

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int
    ) {
        val sessionManager = SessionManager.getInstance(context)
        val views = RemoteViews(context.packageName, R.layout.widget_pause)
        val isActive = sessionManager.isSessionActive()

        // Toggle visibilità stati
        views.setViewVisibility(R.id.widgetIdle, if (isActive) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.widgetActive, if (isActive) View.VISIBLE else View.GONE)

        if (isActive) {
            val remaining = sessionManager.remainingMillis()
            val minutes = (remaining / 60_000L).toInt().coerceAtLeast(0)
            // Testo compatto per lo spazio ridotto del widget
            val timeText = when {
                minutes < 5  -> context.getString(R.string.widget_soon)
                minutes < 60 -> context.getString(R.string.widget_minutes, (minutes / 5) * 5)
                else         -> context.getString(R.string.widget_hours, minutes / 60, minutes % 60)
            }
            views.setTextViewText(R.id.widgetTimeText, timeText)
        }

        // PendingIntent per il tap
        val tapIntent = Intent(context, PauseWidgetProvider::class.java).apply {
            action = ACTION_TAP
        }
        val pendingTap = PendingIntent.getBroadcast(
            context, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widgetIdle, pendingTap)
        views.setOnClickPendingIntent(R.id.widgetActive, pendingTap)

        appWidgetManager.updateAppWidget(widgetId, views)
    }

    // ──────────────────────────────────────────────
    // Durata predefinita: ultima usata o 30 min
    // ──────────────────────────────────────────────

    private fun getLastDuration(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_WIDGET, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_LAST_DURATION, DEFAULT_DURATION_MIN)
    }

    companion object {
        private const val ACTION_TAP = "com.calmotter.app.WIDGET_TAP"
        private const val PREFS_WIDGET = "calm_otter_widget"
        private const val KEY_LAST_DURATION = "last_duration"
        private const val DEFAULT_DURATION_MIN = 30

        /**
         * Aggiorna tutti i widget istanziati sulla home screen.
         * Chiamato da SessionManager dopo startSession/endSession.
         */
        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, PauseWidgetProvider::class.java)
            )
            if (ids.isEmpty()) return
            val intent = Intent(context, PauseWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }

        /** Salva la durata appena usata così il widget la riusa al prossimo tap. */
        fun saveLastDuration(context: Context, minutes: Int) {
            context.getSharedPreferences(PREFS_WIDGET, Context.MODE_PRIVATE)
                .edit().putInt(KEY_LAST_DURATION, minutes).apply()
        }
    }
}
