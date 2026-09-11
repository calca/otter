package com.calmotter.app

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Serializza la cronologia sessioni in CSV per l'export/condivisione.
 * Puro e testabile: nessuna dipendenza da Context o I/O.
 */
object SessionCsvExporter {

    private const val HEADER = "Data,Ora,Minuti pianificati,Minuti effettivi,Esito"

    fun toCsv(records: List<SessionRecord>): String {
        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.ITALY)
        val timeFmt = SimpleDateFormat("HH:mm", Locale.ITALY)

        val builder = StringBuilder(HEADER).append('\n')
        for (record in records) {
            val date = Date(record.startTimeMs)
            val outcome = if (record.completedNaturally) "naturale" else "anticipata"
            val row = listOf(
                dateFmt.format(date),
                timeFmt.format(date),
                record.plannedMinutes.toString(),
                record.effectiveMinutes.toString(),
                outcome
            )
            builder.append(row.joinToString(",") { escapeCsvField(it) }).append('\n')
        }
        return builder.toString()
    }

    /**
     * Quoting CSV standard (RFC 4180): un campo viene racchiuso tra
     * virgolette se contiene una virgola, una virgoletta o un a-capo, e ogni
     * virgoletta interna viene raddoppiata. Nessuna delle colonne attuali
     * può contenerli, ma la funzione resta corretta in generale.
     */
    private fun escapeCsvField(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }
}
