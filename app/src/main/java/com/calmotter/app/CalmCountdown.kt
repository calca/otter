package com.calmotter.app

import android.content.Context

/**
 * Trasforma i millisecondi rimanenti in una frase rilassante, senza
 * mostrare mai il conto alla rovescia esatto che genera ansia.
 *
 * - Sotto i 5 minuti: frasi che segnalano imminente fine senza urgenza
 * - Da 5 minuti in su: tempo arrotondato al multiplo di 5 più vicino,
 *   con frasi variate per evitare che la schermata sembri un timer.
 *
 * Le frasi vivono in `R.array.calm_countdown_near_end_phrases`/
 * `calm_countdown_template_phrases` (non più letterali Kotlin hardcoded
 * come in origine — bug reale: restavano sempre in italiano a prescindere
 * dalla lingua del dispositivo, perché non passavano da `strings.xml`).
 * Richiede un [Context] per questo — i due chiamanti (`BlockScreen.kt`,
 * `SessionForegroundService.kt`) ne hanno già uno a disposizione.
 */
object CalmCountdown {

    fun format(remainingMillis: Long, context: Context): String {
        val totalMinutes = (remainingMillis / 60_000L).toInt()

        if (totalMinutes < 5) {
            return context.resources.getStringArray(R.array.calm_countdown_near_end_phrases).random()
        }

        // Arrotonda al multiplo di 5 più vicino (sempre per difetto, mai sopravvalore)
        val rounded = (totalMinutes / 5) * 5

        return context.resources.getStringArray(R.array.calm_countdown_template_phrases)
            .random()
            .format(rounded)
    }
}
