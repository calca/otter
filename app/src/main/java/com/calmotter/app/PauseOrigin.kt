package com.calmotter.app

import android.content.Context

/**
 * Da dove è partita la pausa in corso. Le pause che nessuno ha avviato in
 * quel momento (programmata, a sorpresa, dopo troppo uso) arrivano senza un
 * gesto dell'utente, e la schermata di blocco identica a quella di una pausa
 * avviata a mano lasciava sorpresi senza un perché (segnalato): l'origine
 * prende il posto di "In pausa" nell'etichetta di stato e nel titolo della
 * notifica fissa, e all'arrivo dà un benvenuto (vedi
 * [SessionManager.consumeArrivalWelcome]). Vedi specs/pause-origin/.
 *
 * [detail] dipende dall'origine: l'ora d'inizio della pausa programmata (in
 * minuti dal mezzanotte, quella che l'utente ha impostato, non l'ora reale
 * di partenza, che con allarmi inesatti può slittare), i minuti di schermo
 * della regola d'uso; 0 per le altre.
 */
enum class PauseOrigin {
    MANUAL,
    SCHEDULED,
    SURPRISE,
    USAGE;

    val isAutomatic: Boolean get() = this != MANUAL

    /** "Pausa delle 21:00", "Pausa a sorpresa"…; null per una pausa avviata a mano. */
    fun label(context: Context, detail: Int): String? = when (this) {
        MANUAL -> null
        // Lo stesso formato della lista delle pause programmate: l'ora come
        // l'utente l'ha impostata. A sorpresa l'ora non si dice, come nel preavviso.
        SCHEDULED -> context.getString(R.string.pause_origin_scheduled, "%02d:%02d".format(detail / 60, detail % 60))
        SURPRISE -> context.getString(R.string.pause_origin_surprise)
        USAGE -> context.getString(R.string.pause_origin_usage, durationWords(context, detail))
    }

    /** La frase dell'otter all'arrivo di una pausa automatica; 0 per una avviata a mano. */
    val welcomeRes: Int
        get() = when (this) {
            MANUAL -> 0
            SCHEDULED -> R.string.pause_welcome_scheduled
            SURPRISE -> R.string.pause_welcome_surprise
            USAGE -> R.string.pause_welcome_usage
        }

    companion object {
        fun fromName(name: String?): PauseOrigin = entries.firstOrNull { it.name == name } ?: MANUAL
    }
}
