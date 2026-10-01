package com.calmotter.app

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit

/** Una lista con nome di app che restano raggiungibili durante una pausa. */
data class AllowedAppsProfile(val id: Int, val name: String, val packages: Set<String>) {
    val isDefault: Boolean get() = id == AllowedAppsManager.DEFAULT_PROFILE_ID
}

/**
 * Le app che restano accessibili durante una pausa, oltre al telefono
 * (mappe, messaggi di famiglia...), organizzate in **profili**
 * (specs/allowed-app-profiles/): un profilo predefinito, "Standard", che
 * esiste sempre, più fino a [MAX_EXTRA_PROFILES] profili con un nome.
 *
 * Dato puramente locale, non sensibile: nessuna cifratura necessaria.
 * Creare, rinominare, modificare e cancellare profili è protetto da password
 * lato UI; scegliere quale usare per la prossima pausa no, perché tutti i
 * profili sono già stati approvati da chi tiene la password.
 */
class AllowedAppsManager private constructor(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Tutti i profili, il predefinito per primo. */
    fun profiles(): List<AllowedAppsProfile> =
        listOf(profile(DEFAULT_PROFILE_ID)) + extraProfileIds().map { profile(it) }

    /** Il profilo con questo id, o il predefinito se non esiste (più). */
    fun profile(id: Int): AllowedAppsProfile {
        if (id != DEFAULT_PROFILE_ID && id in extraProfileIds()) {
            return AllowedAppsProfile(
                id = id,
                name = prefs.getString(nameKey(id), null).orEmpty(),
                packages = prefs.getStringSet(packagesKey(id), emptySet()) ?: emptySet(),
            )
        }
        return AllowedAppsProfile(
            id = DEFAULT_PROFILE_ID,
            name = context.getString(R.string.allowed_profile_default_name),
            // Stessa chiave della lista unica di prima: chi l'aveva la ritrova
            // come profilo predefinito, senza migrazioni.
            packages = prefs.getStringSet(KEY_PACKAGES, emptySet()) ?: emptySet(),
        )
    }

    fun packagesFor(profileId: Int): Set<String> = profile(profileId).packages

    fun setPackages(profileId: Int, packages: Set<String>) {
        val key = if (profileId == DEFAULT_PROFILE_ID || profileId !in extraProfileIds()) KEY_PACKAGES else packagesKey(profileId)
        prefs.edit { putStringSet(key, packages) }
    }

    fun canCreateProfile(): Boolean = extraProfileIds().size < MAX_EXTRA_PROFILES

    /** Crea un profilo vuoto con questo nome; null se si è già al massimo. */
    fun createProfile(name: String): AllowedAppsProfile? {
        if (!canCreateProfile()) return null
        val id = (extraProfileIds().maxOrNull() ?: DEFAULT_PROFILE_ID) + 1
        prefs.edit {
            putString(KEY_PROFILE_IDS, (extraProfileIds() + id).joinToString(","))
            putString(nameKey(id), name.trim())
            putStringSet(packagesKey(id), emptySet())
        }
        return profile(id)
    }

    fun renameProfile(id: Int, name: String) {
        if (id == DEFAULT_PROFILE_ID || id !in extraProfileIds()) return
        prefs.edit { putString(nameKey(id), name.trim()) }
    }

    /** Cancella un profilo (mai il predefinito); se era quello scelto si torna al predefinito. */
    fun deleteProfile(id: Int) {
        if (id == DEFAULT_PROFILE_ID || id !in extraProfileIds()) return
        prefs.edit {
            putString(KEY_PROFILE_IDS, (extraProfileIds() - id).joinToString(","))
            remove(nameKey(id))
            remove(packagesKey(id))
            if (prefs.getInt(KEY_SELECTED, DEFAULT_PROFILE_ID) == id) putInt(KEY_SELECTED, DEFAULT_PROFILE_ID)
        }
    }

    /** Il profilo scelto per la prossima pausa (Home, widget, riquadro, Time together). */
    fun selectedProfileId(): Int {
        val id = prefs.getInt(KEY_SELECTED, DEFAULT_PROFILE_ID)
        return if (id == DEFAULT_PROFILE_ID || id in extraProfileIds()) id else DEFAULT_PROFILE_ID
    }

    fun selectProfile(id: Int) {
        prefs.edit { putInt(KEY_SELECTED, profile(id).id) }
    }

    private fun extraProfileIds(): List<Int> =
        prefs.getString(KEY_PROFILE_IDS, "").orEmpty()
            .split(",")
            .mapNotNull { it.trim().toIntOrNull() }

    private fun nameKey(id: Int) = "profile_${id}_name"
    private fun packagesKey(id: Int) = "profile_${id}_packages"

    companion object {
        // Tetto alle app configurabili dall'utente per profilo (il telefono
        // resta sempre raggiungibile a parte, vedi BlockScreen — non conta
        // in questo limite): una row di icone in BlockScreen deve restare
        // corta e leggibile a colpo d'occhio, non diventare un mini
        // app-drawer. Abbassato da 5 a 3 dopo un bug reale: telefono + 5 app
        // + sblocco (7 badge) non entravano in una riga su schermi normali —
        // l'ultimo badge (lo sblocco stesso!) finiva fuori schermo e non
        // tappabile, senza scroll orizzontale a recuperarlo. Telefono + 3 app
        // + sblocco (5 badge) ci sta sempre.
        const val MAX_ALLOWED_APPS = 3

        const val DEFAULT_PROFILE_ID = 0

        /** Profili oltre al predefinito: più di così diventa una gestione, non una scelta. */
        const val MAX_EXTRA_PROFILES = 4

        private const val PREFS_NAME = "calm_otter_allowed_apps"
        private const val KEY_PACKAGES = "allowed_packages"
        private const val KEY_PROFILE_IDS = "profile_ids"
        private const val KEY_SELECTED = "selected_profile"

        @Volatile private var instance: AllowedAppsManager? = null

        fun getInstance(context: Context): AllowedAppsManager =
            instance ?: synchronized(this) {
                instance ?: AllowedAppsManager(context.applicationContext).also { instance = it }
            }

        @VisibleForTesting
        internal fun resetInstanceForTests() {
            instance = null
        }
    }
}
