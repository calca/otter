package com.calmotter.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Ricevuto quando l'allarme programmato da SessionManager scade: termina
 * la sessione (disattiva il blocco e il Non disturbare) anche se l'utente
 * non sta interagendo con il telefono in quel momento.
 */
class SessionExpiryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // markBackgroundSummary = true: se BlockScreen ha già chiuso la
        // sessione lui stesso (l'utente c'era), endSession() qui è un no-op
        // per la guardia di idempotenza e il segnale non viene scritto — solo
        // quando è davvero questo allarme a terminare la pausa la Home
        // mostra il riepilogo una tantum. Vedi SessionManager.endSession().
        SessionManager.getInstance(context).endSession(completedNaturally = true, markBackgroundSummary = true)
    }
}
