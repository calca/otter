package com.calmotter.app

import org.junit.Assert.assertEquals
import org.junit.Test

/** Tempo passato insieme, per persona (specs/together-history/). */
class CompanionStatsTest {

    private fun pause(start: Long, minutes: Int, companions: String, activityId: Int = 0, group: Boolean = true) =
        SessionRecord(
            startTimeMs = start, plannedMinutes = minutes, effectiveMinutes = minutes,
            completedNaturally = true, isGroupSession = group, companions = companions, activityId = activityId,
        )

    @Test
    fun sumsTimePerPersonMostTimeFirst() {
        val totals = companionTotals(
            listOf(
                pause(1_000, 60, "Marta"),
                pause(2_000, 30, "Luca"),
                pause(3_000, 45, "Marta\nLuca"),
            )
        )
        assertEquals(listOf("Marta", "Luca"), totals.map { it.name })
        assertEquals(105, totals[0].minutes)
        assertEquals(2, totals[0].pauses)
        assertEquals(75, totals[1].minutes) // una pausa a due conta per intero per ciascuno
    }

    @Test
    fun namesGroupIgnoringCaseAndSpacesButNotSpelling() {
        val totals = companionTotals(
            listOf(
                pause(1_000, 10, " marta "),
                pause(2_000, 10, "Marta"),
                pause(3_000, 10, "Martina"),
            )
        )
        assertEquals(2, totals.size)
        assertEquals("Marta", totals.first { it.minutes == 20 }.name) // la grafia più recente
    }

    @Test
    fun soloPausesAndOtherPeriodsDoNotCount() {
        val totals = companionTotals(
            listOf(
                pause(1_000, 60, "Marta", group = false),
                pause(500, 60, "Marta"),
                pause(5_000, 30, "Marta"),
            ),
            fromMs = 1_000,
        )
        assertEquals(30, totals.single().minutes)
    }

    @Test
    fun topCategoriesAreTheMostFrequentUpToThree() {
        val totals = companionTotals(
            listOf(
                pause(1_000, 60, "Marta", activityId = 16), // fuori
                pause(2_000, 60, "Marta", activityId = 7),  // fuori
                pause(3_000, 60, "Marta", activityId = 17), // a tavola
                pause(4_000, 60, "Marta", activityId = 10), // giochi
                pause(5_000, 60, "Marta", activityId = 9),  // con calma (la più recente fra le singole)
                pause(6_000, 60, "Marta"),                  // senza attività: non conta
            )
        )
        assertEquals(
            listOf(TogetherCategory.OUTSIDE, TogetherCategory.SLOW, TogetherCategory.GAMES),
            totals.single().topCategories,
        )
    }
}
