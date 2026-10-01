package com.calmotter.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDateTime
import java.time.ZoneId

/** Pause programmate (specs/scheduled-pauses/). */
@RunWith(RobolectricTestRunner::class)
class ScheduledPausesTest {

    private val rome = ZoneId.of("Europe/Rome")
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) =
        LocalDateTime.of(y, mo, d, h, mi).atZone(rome).toInstant().toEpochMilli()

    private val weekdays = 0b0011111 // lun-ven
    private val evening = ScheduledPause(id = 1, days = weekdays, startMinuteOfDay = 21 * 60, durationMinutes = 60)

    @Test
    fun laterTodayIfTheTimeHasNotPassed() {
        // Giovedì 1 ottobre 2026, 18:00 -> stasera alle 21:00
        assertEquals(at(2026, 10, 1, 21, 0), nextOccurrence(evening, at(2026, 10, 1, 18, 0), rome))
    }

    @Test
    fun skipsTheWeekend() {
        // Venerdì 2 ottobre, 22:00 -> lunedì 5 alle 21:00
        assertEquals(at(2026, 10, 5, 21, 0), nextOccurrence(evening, at(2026, 10, 2, 22, 0), rome))
    }

    @Test
    fun followsDaylightSavingTime() {
        // Domenica 25 ottobre 2026 finisce l'ora legale: le 21:00 restano le 21:00 locali.
        val sundays = evening.copy(days = 0b1000000)
        assertEquals(at(2026, 10, 25, 21, 0), nextOccurrence(sundays, at(2026, 10, 24, 12, 0), rome))
    }

    @Test
    fun skippingTonightMovesToTheNextDay() {
        val skipped = evening.copy(skipUntil = at(2026, 10, 1, 21, 0))
        assertEquals(at(2026, 10, 2, 21, 0), nextOccurrence(skipped, at(2026, 10, 1, 18, 0), rome))
    }

    @Test
    fun noDaysMeansNever() {
        assertNull(nextOccurrence(evening.copy(days = 0), at(2026, 10, 1, 18, 0), rome))
    }

    @Test
    fun stricterChangesNeedNoPasswordLooserOnesDo() {
        assertFalse(evening.copy(days = 0b1111111).isLooserThan(evening)) // più giorni
        assertFalse(evening.copy(durationMinutes = 90).isLooserThan(evening)) // più lunga
        assertTrue(evening.copy(enabled = false).isLooserThan(evening))
        assertTrue(evening.copy(days = 0b0000111).isLooserThan(evening)) // giorni tolti
        assertTrue(evening.copy(durationMinutes = 30).isLooserThan(evening))
        assertTrue(evening.copy(startMinuteOfDay = 23 * 60 + 30).isLooserThan(evening)) // spostata
        assertTrue(evening.copy(profileId = 2).isLooserThan(evening))
        assertTrue(evening.copy(skipUntil = 1L).isLooserThan(evening)) // "non stasera"
    }

    @Test
    fun schedulesAreSavedAndReadBack() {
        val context: Context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("calm_otter_schedules", Context.MODE_PRIVATE).edit().clear().commit()
        ScheduleManager.resetInstanceForTests()
        val manager = ScheduleManager.getInstance(context)

        val saved = manager.save(evening.copy(id = 0))
        assertEquals(1, saved.id)
        val second = manager.save(evening.copy(id = 0, days = 0b1000000, startMinuteOfDay = 9 * 60))
        assertEquals(2, second.id)
        assertEquals(listOf(saved, second), manager.all())

        manager.delete(1)
        assertEquals(listOf(second), manager.all())
    }
}
