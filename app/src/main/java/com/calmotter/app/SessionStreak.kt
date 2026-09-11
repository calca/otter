package com.calmotter.app

import java.util.Calendar

/**
 * Calcola la "serie" (streak) di giorni consecutivi con almeno una sessione
 * di pausa, fino a oggi — o fino a ieri se oggi non ha ancora una sessione
 * (la giornata non è ancora finita, quindi la serie non è "rotta").
 *
 * Puro e testabile: nessuna dipendenza da Context o storage.
 */
object SessionStreak {

    fun currentStreakDays(sessions: List<SessionRecord>, now: Long = System.currentTimeMillis()): Int {
        val daysWithSessions = sessions.mapTo(HashSet()) { dayStart(it.startTimeMs) }

        val cursor = Calendar.getInstance().apply { timeInMillis = dayStart(now) }

        if (cursor.timeInMillis !in daysWithSessions) {
            // Oggi non ha ancora una sessione: la serie potrebbe comunque
            // essere "in corso" se ieri ne aveva una.
            cursor.add(Calendar.DATE, -1)
            if (cursor.timeInMillis !in daysWithSessions) return 0
        }

        var streak = 0
        while (cursor.timeInMillis in daysWithSessions) {
            streak++
            cursor.add(Calendar.DATE, -1)
        }
        return streak
    }

    /** Normalizza un timestamp alla mezzanotte locale dello stesso giorno. */
    private fun dayStart(timeMs: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = timeMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}
