package com.calmotter.app

/**
 * Trasforma i millisecondi rimanenti in una frase rilassante, senza
 * mostrare mai il conto alla rovescia esatto che genera ansia.
 *
 * - Sotto i 5 minuti: frasi che segnalano imminente fine senza urgenza
 * - Da 5 minuti in su: tempo arrotondato al multiplo di 5 più vicino,
 *   con frasi variate per evitare che la schermata sembri un timer.
 */
object CalmCountdown {

    private val nearEndPhrases = listOf(
        "Quasi finita \uD83C\uDF43",
        "Ancora un momento…",
        "Tra poco sei libero",
        "Quasi ci sei",
        "Un ultimo respiro"
    )

    private val templatePhrases = listOf(
        "Ancora circa %d minuti",
        "Hai ancora %d minuti per te",
        "Tra circa %d minuti",
        "Goditi ancora %d minuti",
        "%d minuti di spazio"
    )

    fun format(remainingMillis: Long): String {
        val totalMinutes = (remainingMillis / 60_000L).toInt()

        if (totalMinutes < 5) {
            return nearEndPhrases.random()
        }

        // Arrotonda al multiplo di 5 più vicino (sempre per difetto, mai sopravvalore)
        val rounded = (totalMinutes / 5) * 5

        return templatePhrases.random().format(rounded)
    }
}
