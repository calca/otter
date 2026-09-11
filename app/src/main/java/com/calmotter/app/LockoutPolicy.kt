package com.calmotter.app

/**
 * Logica pura di rate-limiting sui tentativi di password: 5 tentativi
 * falliti consecutivi -> lockout di 30 secondi. Separata da PasswordManager
 * per essere testabile senza dipendere da EncryptedSharedPreferences/Keystore.
 */
object LockoutPolicy {
    const val MAX_ATTEMPTS = 5
    const val LOCKOUT_DURATION_MS = 30_000L

    data class State(val failedAttempts: Int, val lockoutUntilMs: Long)

    fun afterAttempt(current: State, success: Boolean, now: Long): State = when {
        success -> State(0, 0L)
        current.failedAttempts + 1 >= MAX_ATTEMPTS -> State(0, now + LOCKOUT_DURATION_MS)
        else -> State(current.failedAttempts + 1, current.lockoutUntilMs)
    }

    fun isLockedOut(state: State, now: Long): Boolean = state.lockoutUntilMs > now

    fun lockoutRemainingSeconds(state: State, now: Long): Int {
        val remainingMs = state.lockoutUntilMs - now
        return if (remainingMs <= 0) 0 else ((remainingMs + 999) / 1000).toInt()
    }
}
