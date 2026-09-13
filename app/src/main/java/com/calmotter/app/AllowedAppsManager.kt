package com.calmotter.app

import android.content.Context

/**
 * Elenco di pacchetti aggiuntivi, oltre al telefono, che restano accessibili
 * durante una sessione di pausa (es. mappe, messaggi famiglia...).
 * Dato puramente locale, non sensibile: nessuna cifratura necessaria.
 * La modifica di questo elenco è gated da password lato UI (vedi MainActivity).
 */
class AllowedAppsManager private constructor(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAllowedPackages(): Set<String> =
        prefs.getStringSet(KEY_PACKAGES, emptySet()) ?: emptySet()

    fun setAllowedPackages(packages: Set<String>) {
        prefs.edit().putStringSet(KEY_PACKAGES, packages).apply()
    }

    companion object {
        // Tetto alle app configurabili dall'utente (il telefono resta sempre
        // raggiungibile a parte, vedi MainActivity/BlockScreen — non conta in
        // questo limite): una row di icone in BlockScreen deve restare corta
        // e leggibile a colpo d'occhio, non diventare un mini app-drawer.
        // Abbassato da 5 a 3 dopo un bug reale: telefono + 5 app + sblocco
        // (7 badge) non entravano in una riga su schermi normali — l'ultimo
        // badge (lo sblocco stesso!) finiva fuori schermo e non tappabile,
        // senza scroll orizzontale a recuperarlo. Telefono + 3 app + sblocco
        // (5 badge) ci sta sempre.
        const val MAX_ALLOWED_APPS = 3

        private const val PREFS_NAME = "calm_otter_allowed_apps"
        private const val KEY_PACKAGES = "allowed_packages"

        @Volatile private var instance: AllowedAppsManager? = null

        fun getInstance(context: Context): AllowedAppsManager =
            instance ?: synchronized(this) {
                instance ?: AllowedAppsManager(context.applicationContext).also { instance = it }
            }
    }
}
