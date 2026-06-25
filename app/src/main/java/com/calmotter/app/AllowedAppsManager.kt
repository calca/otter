package com.calmotter.app

import android.content.Context

/**
 * Elenco di pacchetti aggiuntivi, oltre al telefono, che restano accessibili
 * durante una sessione di pausa (es. mappe, messaggi famiglia...).
 * Dato puramente locale, non sensibile: nessuna cifratura necessaria.
 * La modifica di questo elenco è gated da password lato UI (vedi MainActivity).
 */
class AllowedAppsManager(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAllowedPackages(): Set<String> =
        prefs.getStringSet(KEY_PACKAGES, emptySet()) ?: emptySet()

    fun setAllowedPackages(packages: Set<String>) {
        prefs.edit().putStringSet(KEY_PACKAGES, packages).apply()
    }

    companion object {
        private const val PREFS_NAME = "calm_otter_allowed_apps"
        private const val KEY_PACKAGES = "allowed_packages"
    }
}
