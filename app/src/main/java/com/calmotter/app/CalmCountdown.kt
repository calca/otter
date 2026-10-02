package com.calmotter.app

import android.content.Context
import androidx.annotation.VisibleForTesting
import kotlin.random.Random

/**
 * Trasforma i millisecondi rimanenti in una frase rilassante, senza
 * mostrare mai il conto alla rovescia esatto che genera ansia.
 *
 * - Sotto i 5 minuti: frasi che segnalano imminente fine senza urgenza
 * - Da 5 minuti a un'ora: minuti arrotondati per difetto al multiplo di 5
 * - Da un'ora in su: ore e minuti arrotondati per difetto al quarto d'ora
 *   ("3 ore e 45 minuti", "4 ore"). Prima restava tutto in minuti, e una
 *   pausa di 4 ore diceva "235 minuti di calma" — segnalato.
 * Frasi variate per evitare che la schermata sembri un timer; per difetto,
 * mai più tempo di quello che resta.
 *
 * Le frasi vivono in `R.array.calm_countdown_near_end_phrases`/
 * `calm_countdown_template_phrases` (non più letterali Kotlin hardcoded
 * come in origine — bug reale: restavano sempre in italiano a prescindere
 * dalla lingua del dispositivo, perché non passavano da `strings.xml`).
 * Richiede un [Context] per questo — i due chiamanti (`BlockScreen.kt`,
 * `SessionForegroundService.kt`) ne hanno già uno a disposizione.
 */
object CalmCountdown {

    /** Sorgente della scelta delle frasi; fissabile negli screenshot test. */
    @VisibleForTesting
    internal var random: Random = Random.Default

    /** Cadenza di aggiornamento del testo: circa una volta al minuto. */
    const val TICK_MILLIS = 60_000L

    /**
     * Quanto aspettare prima del prossimo controllo: un minuto, ma mai oltre
     * la scadenza. Con un'attesa fissa di un minuto la schermata si accorgeva
     * della fine fino a 60 secondi dopo, lasciando il blocco a video a pausa
     * già finita.
     */
    fun nextTickDelayMillis(remainingMillis: Long): Long =
        remainingMillis.coerceIn(1L, TICK_MILLIS)

    fun format(remainingMillis: Long, context: Context): String {
        val totalMinutes = (remainingMillis / 60_000L).toInt()

        if (totalMinutes < 5) {
            return context.resources.getStringArray(R.array.calm_countdown_near_end_phrases).random(random)
        }

        return context.resources.getStringArray(R.array.calm_countdown_template_phrases)
            .random(random)
            .format(durationText(totalMinutes, context))
    }

    /** "55 minuti", "1 ora", "3 ore e 45 minuti": vedi la doc dell'oggetto per gli arrotondamenti. */
    @VisibleForTesting
    internal fun durationText(totalMinutes: Int, context: Context): String {
        val res = context.resources
        if (totalMinutes < 60) {
            val rounded = (totalMinutes / 5) * 5
            return res.getQuantityString(R.plurals.countdown_minutes, rounded, rounded)
        }
        val rounded = (totalMinutes / 15) * 15
        val hours = rounded / 60
        val minutes = rounded % 60
        val hoursText = res.getQuantityString(R.plurals.countdown_hours, hours, hours)
        if (minutes == 0) return hoursText
        return res.getString(
            R.string.countdown_hours_minutes,
            hoursText,
            res.getQuantityString(R.plurals.countdown_minutes, minutes, minutes),
        )
    }
}
