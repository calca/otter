package com.calmotter.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Il catalogo delle attività insieme (specs/together-activity/). */
class TogetherActivitiesTest {

    @Test
    fun idsAreUniqueAndNeverZero() {
        val ids = TogetherActivities.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.none { it == TogetherActivities.NONE })
        assertTrue(ids.all { it in 1..255 }) // un byte nella ricetta
    }

    @Test
    fun everyOfferedDurationHasSomethingToProposeAndTheNothingEntry() {
        for (minutes in SESSION_DURATION_OPTIONS) {
            val fitting = TogetherActivities.fitting(minutes)
            assertTrue("$minutes min", fitting.isNotEmpty())
            assertTrue("$minutes min: manca 'stiamo assieme'", fitting.any { it.slug == "together" })
        }
    }

    @Test
    fun anotherOneRespectsTheDurationAndAvoidsRepeats() {
        val random = Random(1)
        val seen = mutableSetOf<Int>()
        val fitting = TogetherActivities.fitting(60)
        repeat(fitting.size) {
            val next = TogetherActivities.next(60, seen, random = random)
            assertTrue(next.fits(60))
            assertTrue("ripetuta prima di averle viste tutte", next.id !in seen)
            seen += next.id
        }
        assertEquals(fitting.map { it.id }.toSet(), seen)
    }

    @Test
    fun anotherOnePrefersADifferentCategory() {
        repeat(20) { seed ->
            val next = TogetherActivities.next(60, lastCategory = TogetherCategory.OUTSIDE, random = Random(seed))
            assertNotEquals(TogetherCategory.OUTSIDE, next.category)
        }
    }
}
