package com.calmotter.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek

class CompactDaysTest {

    private fun label(vararg days: DayOfWeek): String {
        val mask = days.fold(0) { acc, d -> acc or (1 shl (d.value - 1)) }
        return compactDays(mask) { it.name.take(3).lowercase().replaceFirstChar(Char::uppercase) }
    }

    @Test
    fun treOPiuGiorniDiFilaDiventanoUnIntervallo() {
        assertEquals("Mon–Sat", label(*DayOfWeek.entries.take(6).toTypedArray()))
        assertEquals("Mon–Wed, Fri", label(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
    }

    @Test
    fun dueGiorniDiFilaEGiorniSparsiRestanoElencati() {
        assertEquals("Tue, Thu", label(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY))
        assertEquals("Fri, Sat", label(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY))
        assertEquals("Wed", label(DayOfWeek.WEDNESDAY))
    }

    @Test
    fun laSettimanaNonSiRichiudeSullaDomenica() {
        assertEquals("Mon, Fri–Sun", label(DayOfWeek.MONDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))
    }
}
