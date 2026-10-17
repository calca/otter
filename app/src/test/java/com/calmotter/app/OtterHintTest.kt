package com.calmotter.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/** Le regole del fumetto sopra l'otter (specs/otter-daily-hint/). */
@RunWith(RobolectricTestRunner::class)
class OtterHintTest {

    private lateinit var context: Context
    private val zone = ZoneId.of("Europe/Rome")
    private val today = LocalDate.of(2026, 10, 17)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        OtterHint.random = Random(1)
    }

    private fun pauseOn(day: LocalDate) = SessionRecord(
        startTimeMs = day.atTime(9, 0).atZone(zone).toInstant().toEpochMilli(),
        plannedMinutes = 30,
        effectiveMinutes = 30,
        completedNaturally = true,
    )

    private fun pick(history: List<SessionRecord>, day: LocalDate = today, hasSchedules: Boolean = true) =
        OtterHint.pick(context, history, hasSchedules, day, zone)

    @Test
    fun beforeTheFirstPauseItIsTheFirstHint() {
        val hint = pick(emptyList())
        assertEquals(context.getString(R.string.otter_hint), hint?.text)
        assertFalse(hint!!.daily)
    }

    @Test
    fun aDailyPhraseOnceADay() {
        val history = listOf(pauseOn(today.minusDays(3)))

        val first = pick(history)
        assertNotNull(first)
        assertTrue(first!!.daily)
        assertTrue(first.text in context.resources.getStringArray(R.array.otter_daily_phrases))
        // Riaprendo la Home lo stesso giorno: niente.
        assertNull(pick(history))
        // Il giorno dopo torna.
        assertNotNull(pick(history, today.plusDays(1)))
    }

    @Test
    fun nothingIfThereWasAlreadyAPauseToday() {
        assertNull(pick(listOf(pauseOn(today))))
    }

    @Test
    fun theSwitchTurnsItOff() {
        OtterHint.setDailyEnabled(context, false)
        assertNull(pick(listOf(pauseOn(today.minusDays(1)))))
    }

    @Test
    fun theSchedulesPhraseOnlyToWhoHasNone() {
        val history = listOf(pauseOn(today.minusDays(1)))
        val withSchedules = (0 until 40).mapNotNull { pick(history, today.plusDays(it.toLong()), hasSchedules = true) }
        assertTrue(withSchedules.none { it.opensSchedules })

        val withoutSchedules = (40 until 80).mapNotNull { pick(history, today.plusDays(it.toLong()), hasSchedules = false) }
        val schedulesHints = withoutSchedules.filter { it.opensSchedules }
        assertTrue(schedulesHints.isNotEmpty())
        assertTrue(schedulesHints.all { it.text == context.getString(R.string.otter_hint_schedules) })
    }
}
