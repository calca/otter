package com.calmotter.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

/** Cronologia per mese (specs/history-by-month/). */
class HistoryMonthsTest {

    private val rome = ZoneId.of("Europe/Rome")
    private fun at(y: Int, mo: Int, d: Int) = LocalDateTime.of(y, mo, d, 12, 0).atZone(rome).toInstant().toEpochMilli()
    private fun pause(time: Long) = SessionRecord(startTimeMs = time, plannedMinutes = 30, effectiveMinutes = 30, completedNaturally = true)

    private val sessions = listOf(
        pause(at(2026, 10, 1)), pause(at(2026, 9, 20)), pause(at(2026, 9, 5)),
        pause(at(2026, 7, 10)), pause(at(2026, 3, 2)),
    )

    @Test
    fun opensOnThisMonthAndThePreviousOne() {
        val view = historyMonths(sessions, at(2026, 10, 15), extraMonths = 0, zone = rome)
        assertEquals(listOf(YearMonth.of(2026, 10), YearMonth.of(2026, 9)), view.months.map { it.month })
        assertEquals(2, view.months[1].sessions.size)
        assertTrue(view.hasEarlier)
    }

    @Test
    fun eachTapLoadsOneMoreMonthWithSessions() {
        val one = historyMonths(sessions, at(2026, 10, 15), extraMonths = 1, zone = rome)
        assertEquals(YearMonth.of(2026, 7), one.months.last().month) // agosto vuoto: saltato
        assertTrue(one.hasEarlier)
        val two = historyMonths(sessions, at(2026, 10, 15), extraMonths = 2, zone = rome)
        assertEquals(YearMonth.of(2026, 3), two.months.last().month)
        assertFalse(two.hasEarlier)
    }

    @Test
    fun anEmptyCurrentMonthStillShows() {
        val view = historyMonths(sessions, at(2026, 11, 1), extraMonths = 0, zone = rome)
        assertEquals(YearMonth.of(2026, 11), view.months[0].month)
        assertTrue(view.months[0].sessions.isEmpty())
    }
}
