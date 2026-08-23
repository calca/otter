package com.calmotter.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockoutPolicyTest {

    private val now = 1_700_000_000_000L

    @Test
    fun fourWrongAttemptsStayUnlockedWithEscalatingCount() {
        var state = LockoutPolicy.State(0, 0L)
        for (expectedAttempts in 1..4) {
            state = LockoutPolicy.afterAttempt(state, success = false, now = now)
            assertEquals(expectedAttempts, state.failedAttempts)
            assertEquals(0L, state.lockoutUntilMs)
            assertFalse(LockoutPolicy.isLockedOut(state, now))
        }
    }

    @Test
    fun fifthWrongAttemptTriggersLockoutAndResetsCounter() {
        var state = LockoutPolicy.State(0, 0L)
        repeat(4) {
            state = LockoutPolicy.afterAttempt(state, success = false, now = now)
        }
        assertEquals(4, state.failedAttempts)

        state = LockoutPolicy.afterAttempt(state, success = false, now = now)

        assertEquals(0, state.failedAttempts)
        assertEquals(now + LockoutPolicy.LOCKOUT_DURATION_MS, state.lockoutUntilMs)
        assertTrue(LockoutPolicy.isLockedOut(state, now))
    }

    @Test
    fun successfulAttemptResetsBothFieldsRegardlessOfPriorFailures() {
        val current = LockoutPolicy.State(failedAttempts = 3, lockoutUntilMs = now + 12_345L)

        val next = LockoutPolicy.afterAttempt(current, success = true, now = now)

        assertEquals(0, next.failedAttempts)
        assertEquals(0L, next.lockoutUntilMs)
    }

    @Test
    fun isLockedOutReflectsFutureLockout() {
        val state = LockoutPolicy.State(0, lockoutUntilMs = now + 10_000L)
        assertTrue(LockoutPolicy.isLockedOut(state, now))
        assertEquals(10, LockoutPolicy.lockoutRemainingSeconds(state, now))
    }

    @Test
    fun isLockedOutReflectsPastLockout() {
        val state = LockoutPolicy.State(0, lockoutUntilMs = now - 1_000L)
        assertFalse(LockoutPolicy.isLockedOut(state, now))
        assertEquals(0, LockoutPolicy.lockoutRemainingSeconds(state, now))
    }

    @Test
    fun isLockedOutBoundaryIsExclusive() {
        // lockoutUntilMs == now: non è più "in lockout" (strict >, non >=).
        val state = LockoutPolicy.State(0, lockoutUntilMs = now)
        assertFalse(LockoutPolicy.isLockedOut(state, now))
        assertEquals(0, LockoutPolicy.lockoutRemainingSeconds(state, now))
    }

    @Test
    fun lockoutRemainingSecondsRoundsUpToTheCeiling() {
        // 30001ms rimanenti -> 31s, non 30s (arrotondamento per eccesso).
        val state = LockoutPolicy.State(0, lockoutUntilMs = now + 30_001L)
        assertEquals(31, LockoutPolicy.lockoutRemainingSeconds(state, now))

        // Caso esatto: 30000ms rimanenti -> 30s, nessun arrotondamento spurio.
        val exact = LockoutPolicy.State(0, lockoutUntilMs = now + 30_000L)
        assertEquals(30, LockoutPolicy.lockoutRemainingSeconds(exact, now))
    }
}
