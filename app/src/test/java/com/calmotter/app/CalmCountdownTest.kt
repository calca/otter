package com.calmotter.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalmCountdownTest {

    /**
     * Sotto i 5 minuti il risultato è una delle frasi "quasi finita", nessuna
     * delle quali contiene cifre (a differenza dei template usati da 5 minuti
     * in su, che incorporano sempre un numero). Verifichiamo questa proprietà
     * invece di elencare le frasi esatte, per restare robusti a modifiche
     * dell'elenco delle frasi.
     */
    @Test
    fun underFiveMinutesReturnsNonBlankPhraseWithoutDigits() {
        val remainingMillisValues = listOf(
            0L,
            1L,
            60_000L,
            4 * 60_000L,
            4 * 60_000L + 59_999L
        )
        for (remaining in remainingMillisValues) {
            repeat(10) {
                val result = CalmCountdown.format(remaining)
                assertTrue("expected non-blank result for $remaining ms", result.isNotBlank())
                assertFalse(
                    "expected no digits for $remaining ms but got: $result",
                    result.any { it.isDigit() }
                )
            }
        }
    }

    @Test
    fun fiveMinutesOrMoreContainsRoundedDownMinutesAsSubstring() {
        // 47 minuti -> arrotondato per difetto al multiplo di 5 più vicino -> 45
        assertContainsRoundedMinutes(47, 45)
        // Esattamente 5 minuti -> resta 5
        assertContainsRoundedMinutes(5, 5)
        // 59 minuti -> 55
        assertContainsRoundedMinutes(59, 55)
        // 300 minuti -> 300 (già multiplo di 5)
        assertContainsRoundedMinutes(300, 300)
        // 61 minuti -> 60
        assertContainsRoundedMinutes(61, 60)
    }

    private fun assertContainsRoundedMinutes(totalMinutes: Int, expectedRounded: Int) {
        val remainingMillis = totalMinutes * 60_000L
        repeat(10) {
            val result = CalmCountdown.format(remainingMillis)
            assertTrue(
                "expected '$expectedRounded' as substring of: $result",
                result.contains(expectedRounded.toString())
            )
        }
    }
}
