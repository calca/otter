package com.calmotter.app

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Il riquadro "Calm Otter" nelle Impostazioni rapide (specs/quick-settings-tile/):
 * avvia una pausa dalla tendina, cioè da dovunque, anche da dentro l'app che
 * si sta mangiando il tempo.
 *
 * Fa le stesse cose del tocco sul widget ([PauseWidgetTapAction]): pausa in
 * corso → apre la schermata di blocco; nessuna pausa → la avvia con
 * l'ultima durata scelta ([LastDuration]). In più, se mancano i permessi
 * apre l'app invece di avviare una pausa che non bloccherebbe nulla.
 *
 * Lo stato non si aggiorna a ogni minuto (servirebbe un servizio sempre
 * attivo): si ricalcola quando la tendina si apre, cioè quando qualcuno lo
 * guarda, e quando una pausa inizia o finisce ([requestRefresh]).
 */
class PauseTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        val sessionManager = SessionManager.getInstance(applicationContext)
        when {
            sessionManager.isSessionActive() -> openAndCollapse(BlockOverlayActivity::class.java)
            !permissionsGranted() -> openAndCollapse(MainActivity::class.java)
            else -> {
                sessionManager.startSession(LastDuration.get(applicationContext))
                // La schermata di blocco conferma subito che la pausa è
                // partita, e aprirla è anche l'unico modo per chiudere la
                // tendina.
                openAndCollapse(BlockOverlayActivity::class.java)
            }
        }
    }

    // Stessa scorciatoia di MainActivity: nelle build di debug i permessi non
    // servono, così la pausa si prova anche sull'emulatore.
    private fun permissionsGranted(): Boolean =
        BuildConfig.DEBUG || (isAccessibilityServiceEnabled(this) && isDndAccessGranted(this))

    private fun refresh() {
        val tile = qsTile ?: return
        val sessionManager = SessionManager.getInstance(applicationContext)
        val active = sessionManager.isSessionActive()
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.app_name)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (active) {
                CalmCountdown.format(sessionManager.remainingMillis(), this)
            } else {
                getString(R.string.tile_idle_subtitle, durationPillLabel(LastDuration.get(this)))
            }
        }
        tile.updateTile()
    }

    private fun openAndCollapse(activity: Class<*>) {
        val intent = Intent(this, activity).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE),
            )
        } else {
            openAndCollapseBeforeApi34(intent)
        }
    }

    // Sotto Android 14 la variante con PendingIntent non esiste: quella con
    // Intent è l'unica disponibile, e lint la segnala anche dietro il
    // controllo di versione.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    private fun openAndCollapseBeforeApi34(intent: Intent) {
        startActivityAndCollapse(intent)
    }

    companion object {
        /** Chiede al sistema di riaggiornare il riquadro: da chiamare quando una pausa inizia o finisce. */
        fun requestRefresh(context: Context) {
            requestListeningState(context, ComponentName(context, PauseTileService::class.java))
        }
    }
}
