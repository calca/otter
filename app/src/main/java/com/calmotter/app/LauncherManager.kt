package com.calmotter.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Memorizza il pacchetto del launcher "vero" del telefono (quello usato
 * prima che CalmOtter diventasse l'app Home), così che MainActivity (quando
 * invocata come app Home, vedi il suo handleIntent()) possa inoltrargli il
 * tasto Home quando nessuna sessione di pausa è attiva.
 *
 * Bug reale scoperto e corretto: `queryIntentActivities` restituisce TUTTE
 * le app che dichiarano di poter gestire l'intent HOME, ma l'ordine non
 * riflette affatto quale sia "il vero launcher" — su un dispositivo reale
 * l'elenco include anche `com.android.settings` (FallbackHome, il fallback
 * di sistema usato quando non c'è nessun launcher vero disponibile), che
 * poteva finire scelto per puro ordine di enumerazione. Se questo accade,
 * MainActivity.forwardToOriginalLauncher() prova a inoltrare a un pacchetto
 * che non gestisce davvero l'Home in modo stabile, fallisce, e il ramo di
 * fallback (un intent Home generico, senza filtro pacchetto) rischia di
 * risolvere di nuovo su CalmOtter stesso — che nel frattempo è ancora
 * l'app Home impostata — creando un loop di istanze che si rilanciano a
 * vicenda. Corretto con due misure: [EXCLUDED_PACKAGES] scarta i
 * fallback di sistema noti, e la rilevazione preferisce `resolveActivity`
 * (il default davvero attivo in questo momento, affidabile quando chiamata
 * prima che CalmOtter diventi Home — vedi sotto) a `queryIntentActivities`.
 */
class LauncherManager private constructor(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getOriginalLauncherPackage(): String? = prefs.getString(KEY_PACKAGE, null)

    /**
     * Aggiorna il launcher "originale" salvato con il default Home
     * attualmente attivo, se rilevabile in modo affidabile. Va chiamata ad
     * ogni avvio (non solo la prima volta): l'utente può cambiare launcher
     * di default in qualunque momento (impostarne uno nuovo, disinstallare
     * quello vecchio...), e il valore salvato deve seguirlo, non restare
     * congelato al primo rilevamento.
     *
     * `resolveActivity` è affidabile solo quando CalmOtter non è già l'app
     * Home corrente — se lo è (l'utente ha appena premuto Home, o questa
     * chiamata arriva da MainActivity.onCreate() mentre CalmOtter è
     * impostata come Home), restituirebbe CalmOtter stessa, ed è quindi
     * ignorato: si mantiene il valore già salvato invece di sovrascriverlo.
     * L'enumerazione via `queryIntentActivities` (ordine non affidabile,
     * vedi il commento di classe) resta un fallback usato SOLO per il
     * primo salvataggio in assoluto (nessun valore salvato ancora), per
     * coprire il caso limite in cui CalmOtter diventi l'app Home dalle
     * Impostazioni di sistema prima di essere mai stata aperta una volta —
     * non viene mai ripetuta sulle chiamate successive, per non rischiare
     * di rimpiazzare un valore buono già noto con una scelta arbitraria.
     */
    fun refreshOriginalLauncherPackage() {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

        val resolved = context.packageManager
            .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
        if (resolved != null && resolved != context.packageName && resolved !in EXCLUDED_PACKAGES) {
            prefs.edit().putString(KEY_PACKAGE, resolved).apply()
            return
        }

        if (prefs.contains(KEY_PACKAGE)) return

        val detected = context.packageManager.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName }
            .firstOrNull { it != context.packageName && it !in EXCLUDED_PACKAGES }

        if (detected != null) {
            prefs.edit().putString(KEY_PACKAGE, detected).apply()
        }
    }

    companion object {
        private const val PREFS_NAME = "calm_otter_launcher"
        private const val KEY_PACKAGE = "original_launcher_package"

        // Pacchetti che dichiarano CATEGORY_HOME solo come fallback di
        // sistema (mai un vero launcher scelto dall'utente) — non vanno mai
        // salvati né usati come bersaglio di un forward. Esposto anche a
        // MainActivity.forwardToOriginalLauncher(), che fa la sua stessa
        // ricerca fresca quando non può fidarsi del valore salvato.
        val EXCLUDED_PACKAGES = setOf("com.android.settings")

        @Volatile private var instance: LauncherManager? = null

        fun getInstance(context: Context): LauncherManager =
            instance ?: synchronized(this) {
                instance ?: LauncherManager(context.applicationContext).also { instance = it }
            }
    }
}
