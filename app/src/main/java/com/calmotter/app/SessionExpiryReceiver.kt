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
        SessionManager(context).endSession(completedNaturally = true)
    }
}
