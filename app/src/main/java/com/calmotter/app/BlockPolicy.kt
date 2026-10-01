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
     * L'insieme dei pacchetti consentiti: dialer predefinito, systemUI,
     * dialog di sistema, questa stessa app (per mostrare il blocco), le
     * tastiere abilitate e la whitelist scelta dall'utente.
     */
    fun allowedPackages(
        defaultDialer: String?,
        ownPackage: String,
        keyboards: Set<String>,
        userAllowed: Set<String>,
    ): Set<String> =
        setOfNotNull(defaultDialer, SYSTEM_UI_PACKAGE, ANDROID_PACKAGE, ownPackage) +
            keyboards + userAllowed

    /**
     * `true` se la finestra appena comparsa va coperta con il blocco.
     *
     * - Senza sessione attiva non si blocca mai.
     * - Solo finestre a schermo intero: popup e barre di sistema aprono
     *   finestre sopra la schermata corrente, già giudicata.
     * - Un pacchetto consentito non si blocca.
     */
    fun shouldBlock(
        sessionActive: Boolean,
        isFullScreen: Boolean,
        packageName: String,
        allowedPackages: Set<String>,
    ): Boolean = sessionActive && isFullScreen && packageName !in allowedPackages
}
