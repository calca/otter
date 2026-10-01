package com.calmotter.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.calmotter.app.ui.screens.EXHALE_MILLIS
import com.calmotter.app.ui.screens.INHALE_MILLIS
import com.calmotter.app.ui.screens.breathFullness
import com.calmotter.app.ui.screens.isInhaling
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Durate offerte, durata ricordata e ritmo della pausa respiro
 * (specs/breathing-pause/).
 */
@RunWith(RobolectricTestRunner::class)
class SessionDurationsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clearSavedDuration() {
        context.getSharedPreferences("calm_otter_widget", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun breathingPauseComesFirstThenHalfHourSteps() {
        assertEquals(listOf(10, 30, 60, 90, 120, 150, 180, 210, 240), SESSION_DURATION_OPTIONS)
        assertTrue(DEFAULT_SESSION_DURATION_MINUTES in SESSION_DURATION_OPTIONS)
    }

    @Test
    fun pillLabelsMatchTheHomeWording() {
        assertEquals("10 min", durationPillLabel(10))
        assertEquals("30 min", durationPillLabel(30))
        assertEquals("1 h", durationPillLabel(60))
        assertEquals("1 h 30", durationPillLabel(90))
        assertEquals("4 h", durationPillLabel(240))
    }

    @Test
    fun onlyPausesUpToTenMinutesBreathe() {
        assertTrue(isBreathingPause(10 * 60_000L))
        assertTrue(isBreathingPause(5 * 60_000L))
        assertFalse(isBreathingPause(10 * 60_000L + 1))
        assertFalse(isBreathingPause(30 * 60_000L))
        assertFalse(isBreathingPause(0L))
    }

    @Test
    fun freshInstallProposesOneHour() {
        assertEquals(60, LastDuration.get(context))
    }

    @Test
    fun theLastChosenDurationIsRemembered() {
        LastDuration.save(context, 10)
        assertEquals(10, LastDuration.get(context))
        LastDuration.save(context, 150)
        assertEquals(150, LastDuration.get(context))
    }

    @Test
    fun aDurationNoLongerOfferedFallsBackToTheDefault() {
        LastDuration.save(context, 45)
        assertEquals(DEFAULT_SESSION_DURATION_MINUTES, LastDuration.get(context))
    }

    @Test
    fun breathInThenOut() {
        assertTrue(isInhaling(0))
        assertTrue(isInhaling(INHALE_MILLIS - 1))
        assertFalse(isInhaling(INHALE_MILLIS))
        assertFalse(isInhaling(INHALE_MILLIS + EXHALE_MILLIS - 1))
        assertTrue(isInhaling(INHALE_MILLIS + EXHALE_MILLIS)) // nuovo ciclo
    }

    @Test
    fun fullnessGoesFromEmptyToFullAndBack() {
        assertEquals(0f, breathFullness(0), 0.001f)
        assertEquals(1f, breathFullness(INHALE_MILLIS), 0.001f)
        assertEquals(0f, breathFullness(INHALE_MILLIS + EXHALE_MILLIS), 0.001f)
        val midInhale = breathFullness(INHALE_MILLIS / 2)
        assertTrue(midInhale > 0.4f && midInhale < 0.6f)
    }
}
