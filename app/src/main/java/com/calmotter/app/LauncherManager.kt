package com.calmotter.app

import android.content.Context
import android.content.Intent

/**
 * Memorizza il pacchetto del launcher "vero" del telefono (quello usato
 * prima che CalmOtter diventasse l'app Home), così che HomeActivity possa
 * inoltrargli il tasto Home quando nessuna sessione di pausa è attiva.
 *
 * `queryIntentActivities` restituisce TUTTE le app che dichiarano di poter
 * gestire l'intent HOME, indipendentemente da quale sia il default attuale:
 * per questo la rilevazione funziona anche se chiamata dopo che CalmOtter è
 * già stato impostato come Home.
 */
class LauncherManager private constructor(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getOriginalLauncherPackage(): String? = prefs.getString(KEY_PACKAGE, null)

    fun saveOriginalLauncherIfNeeded() {
        if (prefs.contains(KEY_PACKAGE)) return

        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val detected = context.packageManager.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName }
            .firstOrNull { it != context.packageName }

        if (detected != null) {
            prefs.edit().putString(KEY_PACKAGE, detected).apply()
        }
    }

    companion object {
        private const val PREFS_NAME = "calm_otter_launcher"
        private const val KEY_PACKAGE = "original_launcher_package"

        @Volatile private var instance: LauncherManager? = null

        fun getInstance(context: Context): LauncherManager =
            instance ?: synchronized(this) {
                instance ?: LauncherManager(context.applicationContext).also { instance = it }
            }
    }
}
