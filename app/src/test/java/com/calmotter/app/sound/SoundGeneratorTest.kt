package com.calmotter.app.sound

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Suoni di relax generati (specs/relax-sound/): in gamma, non muti, riproducibili. */
class SoundGeneratorTest {

    private fun samples(sound: RelaxSound, seed: Int = 1, count: Int = 22_050 * 3): ShortArray =
        ShortArray(count).also { SoundGenerator(sound, seed).fill(it) }

    private fun rms(buffer: ShortArray): Double =
        Math.sqrt(buffer.sumOf { it.toDouble() * it } / buffer.size) / Short.MAX_VALUE

    @Test
    fun everySoundHasAudibleButNotSaturatedLevel() {
        listOf(RelaxSound.RAIN, RelaxSound.WAVES, RelaxSound.BROWN, RelaxSound.DRONE).forEach { sound ->
            val level = rms(samples(sound))
            assertTrue("$sound too quiet: $level", level > 0.01)
            assertTrue("$sound too loud: $level", level < 0.5)
        }
    }

    @Test
    fun noSoundClipsAtTheLimits() {
        listOf(RelaxSound.RAIN, RelaxSound.WAVES, RelaxSound.BROWN, RelaxSound.DRONE).forEach { sound ->
            val peak = samples(sound).maxOf { Math.abs(it.toInt()) }
            assertTrue("$sound clips: $peak", peak < Short.MAX_VALUE)
        }
    }

    @Test
    fun offIsSilent() {
        assertEquals(0.0, rms(samples(RelaxSound.OFF)), 0.0)
    }

    @Test
    fun theSameSeedGivesTheSameSoundAndAnotherSeedDoesNot() {
        assertTrue(samples(RelaxSound.RAIN, 7).contentEquals(samples(RelaxSound.RAIN, 7)))
        assertTrue(!samples(RelaxSound.RAIN, 7).contentEquals(samples(RelaxSound.RAIN, 8)))
    }

    @Test
    fun blocksContinueWhereThePreviousOneEnded() {
        // Riempire in due metà dà lo stesso flusso che riempire in una volta.
        val whole = samples(RelaxSound.WAVES, 3, 4_000)
        val generator = SoundGenerator(RelaxSound.WAVES, 3)
        val first = ShortArray(2_000).also { generator.fill(it) }
        val second = ShortArray(2_000).also { generator.fill(it) }
        assertTrue(whole.contentEquals(first + second))
    }

    @Test
    fun wavesSwellAndFadeOverTheirPeriod() {
        val buffer = samples(RelaxSound.WAVES, 5, 22_050 * 12)
        val perSecond = (0 until 12).map { s ->
            rms(buffer.copyOfRange(s * 22_050, (s + 1) * 22_050))
        }
        assertTrue(perSecond.max() > perSecond.min() * 1.5)
    }

    @Test
    fun unknownStoredNameReadsAsNull() {
        assertNull(RelaxSound.fromName("nope"))
        assertNull(RelaxSound.fromName(null))
        assertEquals(RelaxSound.RAIN, RelaxSound.fromName("RAIN"))
    }
}
