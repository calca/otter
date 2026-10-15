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
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.random.Random

/** Pause a sorpresa (specs/surprise-pauses/). */
@RunWith(RobolectricTestRunner::class)
class SurprisePausesTest {

    private val rome = ZoneId.of("Europe/Rome")
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) =
        LocalDateTime.of(y, mo, d, h, mi).atZone(rome).toInstant().toEpochMilli()

    private val weekdays = 0b0011111 // lun-ven
    // 14:00–18:00, 30 minuti: la partenza cade fra le 14:00 e le 17:30.
    private val afternoon = ScheduledPause(
        id = 1, days = weekdays, startMinuteOfDay = 14 * 60, durationMinutes = 30,
        windowEndMinuteOfDay = 18 * 60,
    )

    @Test
    fun theStartFallsInsideTheWindowAndThePauseEndsInsideIt() {
        val now = at(2026, 10, 1, 9, 0) // giovedì mattina
        repeat(200) { seed ->
            val start = nextSurpriseStart(afternoon, now, rome, Random(seed))!!
            assertTrue(start >= at(2026, 10, 1, 14, 0))
            assertTrue(start <= at(2026, 10, 1, 17, 30))
            assertEquals(0L, start % 60_000L) // al minuto intero
        }
    }

    @Test
    fun theSameSeedGivesTheSameStart() {
        val now = at(2026, 10, 1, 9, 0)
        assertEquals(
            nextSurpriseStart(afternoon, now, rome, Random(7)),
            nextSurpriseStart(afternoon, now, rome, Random(7)),
        )
    }

    @Test
    fun insideTheWindowItLeavesRoomForTheHeadsUp() {
        val now = at(2026, 10, 1, 16, 0)
        repeat(100) { seed ->
            val start = nextSurpriseStart(afternoon, now, rome, Random(seed))!!
            assertTrue(start >= now + 6 * 60_000L)
            assertTrue(start <= at(2026, 10, 1, 17, 30))
        }
    }

    @Test
    fun aWindowAlreadyOverMovesToTheNextDay() {
        // Venerdì 2 ottobre alle 17:40: oggi non c'è più spazio, il weekend non conta -> lunedì 5.
        val start = nextSurpriseStart(afternoon, at(2026, 10, 2, 17, 40), rome, Random(1))!!
        assertTrue(start >= at(2026, 10, 5, 14, 0) && start <= at(2026, 10, 5, 17, 30))
    }

    @Test
    fun aDayAlreadyUsedDoesNotGetASecondPause() {
        // La pausa di oggi è partita alle 14:20; alle 15:00 si arma la prossima: domani.
        val used = afternoon.copy(drawnAt = at(2026, 10, 1, 14, 20))
        val start = nextSurpriseStart(used, at(2026, 10, 1, 15, 0), rome, Random(3))!!
        assertTrue(start >= at(2026, 10, 2, 14, 0) && start <= at(2026, 10, 2, 17, 30))
    }

    @Test
    fun skippingSkipsTheWholeWindowOfThatDay() {
        val skipped = afternoon.copy(skipUntil = surpriseWindowEndOn(afternoon, at(2026, 10, 1, 15, 0), rome))
        val start = nextSurpriseStart(skipped, at(2026, 10, 1, 9, 0), rome, Random(4))!!
        assertTrue(start >= at(2026, 10, 2, 14, 0))
    }

    @Test
    fun followsDaylightSavingTime() {
        // Domenica 25 ottobre 2026 finisce l'ora legale: la finestra resta 14–18 locale.
        val sundays = afternoon.copy(days = 0b1000000)
        repeat(50) { seed ->
            val start = nextSurpriseStart(sundays, at(2026, 10, 24, 12, 0), rome, Random(seed))!!
            assertTrue(start >= at(2026, 10, 25, 14, 0) && start <= at(2026, 10, 25, 17, 30))
        }
    }

    @Test
    fun aFixedTimeScheduleHasNoSurprise() {
        assertNull(nextSurpriseStart(afternoon.copy(windowEndMinuteOfDay = -1), at(2026, 10, 1, 9, 0), rome))
    }

    @Test
    fun aWindowOutsideTheDayDrawsNothingInsteadOfCrashing() {
        assertNull(nextSurpriseStart(afternoon.copy(windowEndMinuteOfDay = 24 * 60 + 27), at(2026, 10, 1, 9, 0), rome))
    }

    @Test
    fun theWindowIsAtLeastThePausePlusAnHour() {
        assertEquals(14 * 60 to 15 * 60 + 30, surpriseWindow(14 * 60, 14 * 60 + 30, 30)) // "a" alzato
        assertEquals(14 * 60 to 18 * 60, surpriseWindow(14 * 60, 18 * 60, 30)) // già valida
        // 4 ore + 1 dalle 21:00 sforerebbe la mezzanotte: scende l'inizio.
        assertEquals(LAST_MINUTE_OF_DAY - 300 to LAST_MINUTE_OF_DAY, surpriseWindow(21 * 60, 23 * 60, 240))
    }

    @Test
    fun loosenedSurprisesNeedThePasswordWiderDoNot() {
        assertFalse(afternoon.copy(startMinuteOfDay = 13 * 60).isLooserThan(afternoon)) // più larga
        assertFalse(afternoon.copy(windowEndMinuteOfDay = 19 * 60).isLooserThan(afternoon))
        assertTrue(afternoon.copy(startMinuteOfDay = 15 * 60).isLooserThan(afternoon)) // più stretta
        assertTrue(afternoon.copy(windowEndMinuteOfDay = 17 * 60).isLooserThan(afternoon))
        assertTrue(afternoon.copy(windowEndMinuteOfDay = -1).isLooserThan(afternoon)) // torna a ora fissa
        assertTrue(afternoon.copy(windowEndMinuteOfDay = -1).let { afternoon.isLooserThan(it) }) // da fissa a sorpresa
        assertFalse(afternoon.copy(drawnAt = 123L).isLooserThan(afternoon)) // l'estrazione non è una modifica
    }

    @Test
    fun editingTheWindowOrDurationInvalidatesTheDraw() {
        assertTrue(afternoon.copy(windowEndMinuteOfDay = 19 * 60).drawChangedFrom(afternoon))
        assertTrue(afternoon.copy(durationMinutes = 60).drawChangedFrom(afternoon))
        assertFalse(afternoon.copy(locked = true, drawnAt = 5L).drawChangedFrom(afternoon))
    }

    @Test
    fun surprisesAreSavedAndOldLinesStillRead() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val prefs = context.getSharedPreferences("calm_otter_schedules", Context.MODE_PRIVATE)
        prefs.edit().putString("schedules", "1|31|1260|60|1|1|0|1").commit() // riga a 8 campi
        val manager = ScheduleManager(context)
        val old = manager.all().single()
        assertFalse(old.isSurprise)
        assertTrue(old.locked)

        val saved = manager.save(afternoon.copy(id = 0, drawnAt = at(2026, 10, 1, 15, 12)))
        assertEquals(saved, ScheduleManager(context).byId(saved.id))
    }
}
