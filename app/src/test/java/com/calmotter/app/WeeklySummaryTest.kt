package com.calmotter.app

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDateTime
import java.time.ZoneId

/** La nota della domenica sera (specs/weekly-summary/). */
@RunWith(RobolectricTestRunner::class)
class WeeklySummaryTest {

    private val rome = ZoneId.of("Europe/Rome")
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) =
        LocalDateTime.of(y, mo, d, h, mi).atZone(rome).toInstant().toEpochMilli()

    @Test
    fun nextSundayAtEightPm() {
        // Giovedì 1 ottobre 2026 -> domenica 4 alle 20:00
        assertEquals(at(2026, 10, 4, 20, 0), WeeklySummary.nextSundayEvening(at(2026, 10, 1, 9, 0), rome))
    }

    @Test
    fun onSundayAfterEightItIsNextWeek() {
        assertEquals(at(2026, 10, 11, 20, 0), WeeklySummary.nextSundayEvening(at(2026, 10, 4, 20, 0), rome))
        assertEquals(at(2026, 10, 4, 20, 0), WeeklySummary.nextSundayEvening(at(2026, 10, 4, 19, 59), rome))
    }

    @Test
    fun countsOnlyThisWeekAndNothingMeansNoNote() {
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        val sessions = listOf(
            SessionRecord(startTimeMs = now - 1 * day, plannedMinutes = 30, effectiveMinutes = 30, completedNaturally = true),
            SessionRecord(startTimeMs = now - 2 * day, plannedMinutes = 30, effectiveMinutes = 5, completedNaturally = false),
            SessionRecord(startTimeMs = now - 20 * day, plannedMinutes = 30, effectiveMinutes = 30, completedNaturally = true),
        )
        assertEquals(2, WeeklySummary.pauseCount(sessions))
        assertEquals(0, WeeklySummary.pauseCount(emptyList()))
    }

    @Test
    fun theAlarmPostsOneNoteWithThePauseCountOnly() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        org.robolectric.Shadows.shadowOf(context as android.app.Application)
            .grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        SessionHistoryManager.getInstance(context).clear()
        val now = System.currentTimeMillis()
        repeat(3) {
            SessionHistoryManager.getInstance(context).add(
                SessionRecord(startTimeMs = now - it * 3_600_000L, plannedMinutes = 60, effectiveMinutes = 60, completedNaturally = true)
            )
        }

        WeeklySummary.onAlarm(context)

        val nm = context.getSystemService(android.app.NotificationManager::class.java)
        val posted = org.robolectric.Shadows.shadowOf(nm).allNotifications
        assertEquals(1, posted.size)
        val text = posted[0].extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString()
        assertEquals(context.resources.getQuantityString(R.plurals.weekly_note_pauses, 3, 3), text)
    }
}
