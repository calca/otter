package com.calmotter.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CalmCountdownTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

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
                val result = CalmCountdown.format(remaining, context)
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
            val result = CalmCountdown.format(remainingMillis, context)
            assertTrue(
                "expected '$expectedRounded' as substring of: $result",
                result.contains(expectedRounded.toString())
            )
        }
    }

    /**
     * L'ultimo giro del ciclo di BlockScreen deve cadere sulla scadenza, non
     * fino a un minuto dopo: era il difetto per cui il blocco restava a video
     * a pausa già finita.
     */
    @Test
    fun nextTickNeverWaitsPastTheExpiry() {
        assertEquals(60_000L, CalmCountdown.nextTickDelayMillis(45 * 60_000L))
        assertEquals(60_000L, CalmCountdown.nextTickDelayMillis(60_000L))
        assertEquals(20_000L, CalmCountdown.nextTickDelayMillis(20_000L))
        assertEquals(1L, CalmCountdown.nextTickDelayMillis(1L))
        // Mai zero né negativo: delay(0) in un ciclo stretto sarebbe peggio.
        assertEquals(1L, CalmCountdown.nextTickDelayMillis(0L))
        assertEquals(1L, CalmCountdown.nextTickDelayMillis(-500L))
    }
}
