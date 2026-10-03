package com.calmotter.app

import android.content.Context
import androidx.core.content.edit

/**
 * Il fumetto "Toccami quando vuoi una pausa" sopra l'otter in Home: la
 * scritta "Tocca Otter per iniziare" da sola, la prima volta, poteva non
 * bastare (segnalato). Compare finché non si è mai fatta una pausa e sparisce
 * per sempre alla prima — da qualunque parte parta — o toccandolo.
 */
object OtterHint {
    private const val PREFS = "calm_otter_ui"
    private const val KEY_DONE = "otter_hint_done"

    fun shouldShow(context: Context, hasHistory: Boolean): Boolean =
        !hasHistory && !prefs(context).getBoolean(KEY_DONE, false)

    fun markDone(context: Context) = prefs(context).edit { putBoolean(KEY_DONE, true) }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
