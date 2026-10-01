package com.calmotter.app

import android.content.Context
import androidx.core.content.edit

/**
 * Impostazioni dell'uscita lenta (specs/slow-exit/): chiudere una pausa
 * senza password dopo un'attesa. **Accesa di default**, con 10 minuti: la
 * scelta dell'app è un patto morbido, non una trappola — chi tiene la
 * password lo sa dall'onboarding e può spegnerla o cambiare l'attesa dalle
 * Impostazioni, dietro la password. Dato non sensibile, SharedPreferences
 * semplici.
 */
class SlowExitManager internal constructor(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, true)

    fun waitMinutes(): Int =
        prefs.getInt(KEY_WAIT, DEFAULT_WAIT_MINUTES).takeIf { it in WAIT_OPTIONS } ?: DEFAULT_WAIT_MINUTES

    /** Va chiamato solo dopo la verifica della password. */
    fun update(enabled: Boolean, waitMinutes: Int = waitMinutes()) {
        prefs.edit {
            putBoolean(KEY_ENABLED, enabled)
            putInt(KEY_WAIT, waitMinutes)
        }
    }

    companion object {
        val WAIT_OPTIONS = listOf(5, 10, 15)
        const val DEFAULT_WAIT_MINUTES = 10

        private const val PREFS_NAME = "calm_otter_slow_exit"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_WAIT = "wait_minutes"

        /** L'istanza dell'app: vive in [AppGraph], una per Application (vedi CalmOtterApplication). */
        fun getInstance(context: Context): SlowExitManager = context.appGraph.slowExitManager
    }
}
