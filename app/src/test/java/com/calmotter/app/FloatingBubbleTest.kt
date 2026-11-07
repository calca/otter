package com.calmotter.app

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingBubbleTest {
    private val now = 1_000_000L

    private fun state(
        enabled: Boolean = true,
        own: Boolean = false,
        active: Boolean = false,
        ends: Long = 0L,
        upcoming: Long = 0L,
    ) = bubbleState(enabled, own, active, ends, upcoming, now)

    @Test fun offByDefaultSetting() = assertEquals(BubbleState.Hidden, state(enabled = false, active = true, ends = now + 60_000))
    @Test fun hiddenInsideTheApp() = assertEquals(BubbleState.Hidden, state(own = true, active = true, ends = now + 60_000))
    @Test fun activeSession() = assertEquals(BubbleState.Active(now + 60_000), state(active = true, ends = now + 60_000))
    @Test fun endedSessionHides() = assertEquals(BubbleState.Hidden, state(active = true, ends = now - 1))
    @Test fun upcomingPause() = assertEquals(BubbleState.Upcoming(now + 120_000), state(upcoming = now + 120_000))
    @Test fun upcomingPassedHides() = assertEquals(BubbleState.Hidden, state(upcoming = now - 1))
    @Test fun activeWinsOverUpcoming() = assertEquals(BubbleState.Active(now + 5_000), state(active = true, ends = now + 5_000, upcoming = now + 1_000))
    @Test fun nothingToShow() = assertEquals(BubbleState.Hidden, state())

    @Test fun minutesRoundUpAndNeverZero() {
        assertEquals(1, bubbleMinutes(now + 1, now))
        assertEquals(1, bubbleMinutes(now + 60_000, now))
        assertEquals(2, bubbleMinutes(now + 60_001, now))
        assertEquals(5, bubbleMinutes(now + 5 * 60_000, now))
        assertEquals(1, bubbleMinutes(now - 5, now))
    }
}
