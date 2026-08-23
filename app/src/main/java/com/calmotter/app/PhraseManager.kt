package com.calmotter.app

import android.content.Context
import androidx.annotation.VisibleForTesting

/**
 * Gestisce la preferenza utente "mostra frasi durante la pausa" e la
 * selezione casuale di una frase dall'array di risorse. La preferenza è
 * salvata in SharedPreferences semplici (non sensibile), modificabile
 * dall'utente stesso senza password dalla MainActivity.
 */
class PhraseManager private constructor(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, true) // default: attivo

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /** Restituisce una frase casuale, o null se le frasi sono disabilitate. */
    fun randomPhrase(): String? {
        if (!isEnabled()) return null
        val phrases = context.resources.getStringArray(R.array.pause_phrases)
        if (phrases.isEmpty()) return null
        return phrases.random()
    }

    companion object {
        private const val PREFS_NAME = "calm_otter_phrases"
        private const val KEY_ENABLED = "phrases_enabled"

        @Volatile private var instance: PhraseManager? = null

        fun getInstance(context: Context): PhraseManager =
            instance ?: synchronized(this) {
                instance ?: PhraseManager(context.applicationContext).also { instance = it }
            }

        @VisibleForTesting
        internal fun resetInstanceForTests() {
            instance = null
        }
    }
}
