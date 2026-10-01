package com.calmotter.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Riceve i due allarmi di fine pausa: la scadenza naturale e la fine
 * dell'attesa dell'uscita lenta ([ACTION_SLOW_EXIT], specs/slow-exit/).
 */
class SessionExpiryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sessionManager = SessionManager.getInstance(context)
        if (intent.action == ACTION_SLOW_EXIT) {
            // Se nel frattempo l'attesa è stata annullata non c'è più una
            // scadenza: l'allarme arrivato in ritardo non deve chiudere nulla.
            if (sessionManager.slowExitDeadline() == 0L) return
            sessionManager.endSession(reason = EndReason.SLOW_EXIT)
        } else {
            sessionManager.endSession(completedNaturally = true, markBackgroundSummary = true)
        }
    }

    companion object {
        const val ACTION_SLOW_EXIT = "com.calmotter.app.action.SLOW_EXIT"
    }
}
