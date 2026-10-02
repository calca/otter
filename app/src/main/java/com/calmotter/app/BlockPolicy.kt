package com.calmotter.app

/**
 * La decisione "questo evento va bloccato?" di [AppBlockerAccessibilityService],
 * senza nulla di Android dentro: il servizio raccoglie i dati dal sistema
 * (sessione attiva, pacchetto, tastiere, dialer) e li passa qui. Separata per
 * poterla testare con un unit test — il servizio stesso non si può
 * strumentare, e questa è la parte che, sbagliando, copre lo schermo mentre
 * l'utente usa qualcosa che gli è permesso (vedi i commenti nel servizio).
 */
object BlockPolicy {

    /** Package di sistema sempre consentiti durante una pausa. */
    const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    const val ANDROID_PACKAGE = "android"

    /**
     * Le schermate di chiamata dei produttori. La chiamata in arrivo o in
     * corso non è sempre il dialer predefinito: su Samsung è un'app di
     * sistema a parte (`com.samsung.android.incallui`), e senza questo
     * elenco una telefonata durante la pausa faceva comparire il blocco
     * sopra la chiamata — segnalato. Le chiamate devono sempre passare: è
     * l'unica eccezione dichiarata della pausa. Oltre all'elenco, qualunque
     * pacchetto che finisce in `.incallui` (vedi [isCallScreen]).
     */
    val IN_CALL_PACKAGES = setOf(
        "com.samsung.android.incallui",
        "com.samsung.android.dialer",
        "com.android.incallui",
        "com.android.dialer",
        "com.android.server.telecom",
        "com.google.android.dialer",
    )

    /** `true` per una schermata di chiamata: dell'elenco o `*.incallui` di qualunque produttore. */
    fun isCallScreen(packageName: String): Boolean =
        packageName in IN_CALL_PACKAGES || packageName.endsWith(".incallui")

    /**
     * L'insieme dei pacchetti consentiti: dialer predefinito, schermate di
     * chiamata note ([IN_CALL_PACKAGES]), systemUI, dialog di sistema, questa
     * stessa app (per mostrare il blocco), le tastiere abilitate e la
     * whitelist scelta dall'utente.
     */
    fun allowedPackages(
        defaultDialer: String?,
        ownPackage: String,
        keyboards: Set<String>,
        userAllowed: Set<String>,
    ): Set<String> =
        setOfNotNull(defaultDialer, SYSTEM_UI_PACKAGE, ANDROID_PACKAGE, ownPackage) +
            IN_CALL_PACKAGES + keyboards + userAllowed

    /**
     * `true` se la finestra appena comparsa va coperta con il blocco.
     *
     * - Senza sessione attiva non si blocca mai.
     * - Solo finestre a schermo intero: popup e barre di sistema aprono
     *   finestre sopra la schermata corrente, già giudicata.
     * - Un pacchetto consentito non si blocca, né una schermata di chiamata.
     */
    fun shouldBlock(
        sessionActive: Boolean,
        isFullScreen: Boolean,
        packageName: String,
        allowedPackages: Set<String>,
    ): Boolean = sessionActive && isFullScreen && packageName !in allowedPackages && !isCallScreen(packageName)
}
