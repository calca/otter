package com.calmotter.app

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionStreakTest {

    /** "Adesso": un giovedì pomeriggio fisso, lontano da confini di mese/anno. */
    private fun fixedNow(): Calendar = Calendar.getInstance().apply {
        set(2024, Calendar.JUNE, 20, 14, 30, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private fun daysAgo(base: Calendar, days: Int, hour: Int = 10): Long {
        val cal = base.clone() as Calendar
        cal.add(Calendar.DATE, -days)
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun record(startTimeMs: Long): SessionRecord = SessionRecord(
        startTimeMs = startTimeMs,
        plannedMinutes = 30,
        effectiveMinutes = 30,
        completedNaturally = true
    )

    @Test
    fun noSessionsMeansZeroStreak() {
        val now = fixedNow().timeInMillis
        assertEquals(0, SessionStreak.currentStreakDays(emptyList(), now))
    }

    @Test
    fun onlyTodaySessionMeansStreakOfOne() {
        val base = fixedNow()
        val now = base.timeInMillis
        val sessions = listOf(record(daysAgo(base, 0)))
        assertEquals(1, SessionStreak.currentStreakDays(sessions, now))
    }

    @Test
    fun todayYesterdayAndDayBeforeMeansStreakOfThree() {
        val base = fixedNow()
        val now = base.timeInMillis
        val sessions = listOf(
            record(daysAgo(base, 0)),
            record(daysAgo(base, 1)),
            record(daysAgo(base, 2))
        )
        assertEquals(3, SessionStreak.currentStreakDays(sessions, now))
    }

    @Test
    fun yesterdayAndDayBeforeButNotTodayStillCountsAsTwo() {
        val base = fixedNow()
        val now = base.timeInMillis
        val sessions = listOf(
            record(daysAgo(base, 1)),
            record(daysAgo(base, 2))
        )
        assertEquals(2, SessionStreak.currentStreakDays(sessions, now))
    }

    @Test
    fun gapBeforeTodayAndYesterdayMeansZeroStreak() {
        val base = fixedNow()
        val now = base.timeInMillis
        // Solo una sessione 2 giorni fa: né oggi né ieri hanno sessioni,
        // quindi la serie è considerata interrotta.
        val sessions = listOf(record(daysAgo(base, 2)))
        assertEquals(0, SessionStreak.currentStreakDays(sessions, now))
    }

    @Test
    fun gapInTheMiddleStopsTheStreakAtOne() {
        val base = fixedNow()
        val now = base.timeInMillis
        val sessions = listOf(
            record(daysAgo(base, 0)),
            record(daysAgo(base, 3))
        )
        assertEquals(1, SessionStreak.currentStreakDays(sessions, now))
    }
}
