package com.calmotter.app

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionCsvExporterTest {

    private val header = "Data,Ora,Minuti pianificati,Minuti effettivi,Esito"

    private fun ms(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    /** Righe non vuote, stesso ordine del CSV prodotto. */
    private fun lines(csv: String): List<String> = csv.trimEnd('\n').split("\n")

    @Test
    fun emptyListProducesOnlyHeader() {
        val csv = SessionCsvExporter.toCsv(emptyList())
        val rows = lines(csv)
        assertEquals(1, rows.size)
        assertEquals(header, rows[0])
    }

    @Test
    fun rowCountMatchesRecordCountPlusHeader() {
        val records = listOf(
            SessionRecord(startTimeMs = ms(2024, Calendar.JUNE, 15, 9, 5), plannedMinutes = 30, effectiveMinutes = 30, completedNaturally = true),
            SessionRecord(startTimeMs = ms(2024, Calendar.JUNE, 16, 18, 45), plannedMinutes = 60, effectiveMinutes = 20, completedNaturally = false),
            SessionRecord(startTimeMs = ms(2024, Calendar.JUNE, 17, 7, 0), plannedMinutes = 15, effectiveMinutes = 15, completedNaturally = true)
        )

        val csv = SessionCsvExporter.toCsv(records)
        val rows = lines(csv)

        assertEquals(records.size + 1, rows.size)
        assertEquals(header, rows[0])
    }

    @Test
    fun rowsContainExpectedDatePlannedEffectiveAndOutcome() {
        val records = listOf(
            SessionRecord(startTimeMs = ms(2024, Calendar.JUNE, 15, 9, 5), plannedMinutes = 30, effectiveMinutes = 30, completedNaturally = true),
            SessionRecord(startTimeMs = ms(2024, Calendar.JUNE, 16, 18, 45), plannedMinutes = 60, effectiveMinutes = 20, completedNaturally = false)
        )

        val csv = SessionCsvExporter.toCsv(records)
        val rows = lines(csv)

        val naturalRow = rows[1]
        assertTrue(naturalRow.contains("2024-06-15"))
        assertTrue(naturalRow.contains("09:05"))
        assertTrue(naturalRow.contains(",30,30,naturale"))

        val earlyRow = rows[2]
        assertTrue(earlyRow.contains("2024-06-16"))
        assertTrue(earlyRow.contains("18:45"))
        assertTrue(earlyRow.contains(",60,20,anticipata"))
    }
}
