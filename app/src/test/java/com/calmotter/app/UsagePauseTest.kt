package com.calmotter.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Pausa dopo un uso lungo (specs/usage-pause/). */
@RunWith(RobolectricTestRunner::class)
class UsagePauseTest {

    private val min = 60_000L
    /** Un istante qualunque: 0 vale "fermo" per il contatore, come nella realtà. */
    private val t0 = 1_792_000_000_000L
    private val rome = ZoneId.of("Europe/Rome")
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) =
        LocalDateTime.of(y, mo, d, h, mi).atZone(rome).toInstant().toEpochMilli()

    @Test
    fun countsOnlyWithTheScreenOn() {
        val c = UsageCounter()
        c.screenOn(t0)
        assertEquals(10 * min, c.usedMillis(t0 + 10 * min))
        c.screenOff(t0 + 10 * min)
        assertEquals(10 * min, c.usedMillis(t0 + 12 * min)) // spento: fermo
        assertFalse(c.isRunning)
    }

    @Test
    fun aShortScreenOffStopsTheCountWithoutResettingIt() {
        val c = UsageCounter()
        c.screenOn(t0)
        c.screenOff(t0 + 20 * min)
        assertFalse(c.screenOn(t0 + 24 * min)) // 4 minuti: non è una pausa vera
        assertEquals(30 * min, c.usedMillis(t0 + 34 * min))
    }

    @Test
    fun fiveMinutesWithTheScreenOffResetTheCount() {
        val c = UsageCounter()
        c.screenOn(t0)
        c.screenOff(t0 + 20 * min)
        assertTrue(c.screenOn(t0 + 25 * min))
        assertEquals(3 * min, c.usedMillis(t0 + 28 * min))
    }

    @Test
    fun callsAndAppsThatStayDoNotCount() {
        val c = UsageCounter()
        c.screenOn(t0)
        c.setExcluded(t0 + 10 * min, true) // in chiamata
        assertEquals(10 * min, c.usedMillis(t0 + 40 * min))
        c.setExcluded(t0 + 40 * min, false)
        assertEquals(15 * min, c.usedMillis(t0 + 45 * min)) // riprende senza azzerarsi
    }

    @Test
    fun excludedWithTheScreenOffStaysStoppedWhenTheScreenComesBack() {
        val c = UsageCounter()
        c.setExcluded(t0, true) // pausa in corso
        c.screenOn(t0)
        assertFalse(c.isRunning)
        c.setExcluded(t0 + 10 * min, false) // pausa finita
        assertEquals(5 * min, c.usedMillis(t0 + 15 * min))
    }

    @Test
    fun theStateSurvivesARestart() {
        val c = UsageCounter()
        c.screenOn(t0)
        c.screenOff(t0 + 30 * min)
        val restored = UsageCounter(c.state)
        assertEquals(30 * min, restored.usedMillis(t0 + 31 * min))
        assertTrue(restored.screenOn(t0 + 32 * min).not())
    }

    @Test
    fun theNightIsFrom23To7() {
        assertTrue(isUsageQuietTime(at(2026, 10, 15, 23, 0), rome))
        assertTrue(isUsageQuietTime(at(2026, 10, 16, 6, 59), rome))
        assertFalse(isUsageQuietTime(at(2026, 10, 16, 7, 0), rome))
        assertFalse(isUsageQuietTime(at(2026, 10, 15, 22, 59), rome))
        assertEquals(30 * min, millisToNextQuietChange(at(2026, 10, 15, 22, 30), rome))
        assertEquals(60 * min, millisToNextQuietChange(at(2026, 10, 16, 6, 0), rome))
        assertEquals(8 * 60 * min, millisToNextQuietChange(at(2026, 10, 15, 23, 0), rome))
    }

    @Test
    fun loosenedRulesNeedThePasswordOnlyWhenProtected() {
        val rule = UsagePauseRule(enabled = true, afterMinutes = 45, durationMinutes = 10)
        assertFalse(usagePasswordNeededToSave(rule, rule.copy(enabled = false))) // non protetta: libera
        assertTrue(usagePasswordNeededToSave(rule, rule.copy(locked = true))) // proteggerla

        val locked = rule.copy(locked = true)
        assertFalse(usagePasswordNeededToSave(locked, locked.copy(afterMinutes = 30))) // prima: stringe
        assertFalse(usagePasswordNeededToSave(locked, locked.copy(durationMinutes = 30))) // più lunga
        assertTrue(usagePasswordNeededToSave(locked, locked.copy(afterMinutes = 60)))
        assertTrue(usagePasswordNeededToSave(locked, locked.copy(enabled = false)))
        assertTrue(usagePasswordNeededToSave(locked, locked.copy(profileId = 2)))
        assertTrue(usagePasswordNeededToSave(locked, locked.copy(locked = false)))
    }

    @Test
    fun theNextScheduledPauseIsSeen() {
        val now = at(2026, 10, 15, 20, 50) // giovedì
        val evening = ScheduledPause(id = 1, days = 0b0011111, startMinuteOfDay = 21 * 60, durationMinutes = 60)
        assertEquals(at(2026, 10, 15, 21, 0), nextScheduledStart(listOf(evening), now))
        assertNull(nextScheduledStart(listOf(evening.copy(enabled = false)), now))
        val surprise = ScheduledPause(
            id = 2, days = 0b1111111, startMinuteOfDay = 14 * 60, durationMinutes = 30,
            windowEndMinuteOfDay = 18 * 60, drawnAt = at(2026, 10, 15, 20, 55),
        )
        assertEquals(at(2026, 10, 15, 20, 55), nextScheduledStart(listOf(evening, surprise), now))
    }

    @Test
    fun notNowAtMostThreeTimesADay() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val manager = UsagePauseManager(context)
        val today = LocalDate.of(2026, 10, 15)
        assertEquals(3, manager.notNowLeft(today))
        repeat(3) { manager.recordNotNow(today) }
        assertEquals(0, manager.notNowLeft(today))
        assertEquals(3, manager.notNowLeft(today.plusDays(1))) // a mezzanotte riparte
    }

    @Test
    fun ruleAndCounterAreSavedAndReadBack() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val manager = UsagePauseManager(context)
        assertFalse(manager.rule().enabled) // spenta di default
        val rule = UsagePauseRule(enabled = true, afterMinutes = 30, durationMinutes = 30, profileId = 2, locked = true)
        manager.saveRule(rule)
        val state = UsageState(accumulatedMillis = 5 * min, runningSince = 7L, lastScreenOffAt = 3L, screenOn = true)
        manager.saveCounterState(state)
        val again = UsagePauseManager(context)
        assertEquals(rule, again.rule())
        assertEquals(state, again.counterState())
    }
}
