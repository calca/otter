package com.calmotter.app

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Serializza la cronologia sessioni in CSV per l'export/condivisione.
 * Puro e testabile: nessuna dipendenza da Context o I/O.
 */
object SessionCsvExporter {

    // Le ultime quattro colonne vengono da Time together e dal momento di
    // chiusura (specs/together-activity/, specs/closing-moment/): vuote quando
    // la pausa non le ha. L'attività è il suo identificativo testuale (slug),
    // non la frase, così il file non dipende dalla lingua dell'app.
    private const val HEADER = "Data,Ora,Minuti pianificati,Minuti effettivi,Esito,Con chi,Attività,Com'è andata,Nota"

    fun toCsv(records: List<SessionRecord>): String {
        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.ITALY)
        val timeFmt = SimpleDateFormat("HH:mm", Locale.ITALY)

        val builder = StringBuilder(HEADER).append('\n')
        for (record in records) {
            val date = Date(record.startTimeMs)
            val outcome = when {
                record.completedNaturally -> "naturale"
                record.endReason == EndReason.SLOW_EXIT -> "senza password"
                else -> "anticipata"
            }
            val mood = when (record.mood) {
                Mood.CALM -> "calma"
                Mood.ORDINARY -> "normale"
                Mood.HARD -> "faticosa"
                else -> ""
            }
            val row = listOf(
                dateFmt.format(date),
                timeFmt.format(date),
                record.plannedMinutes.toString(),
                record.effectiveMinutes.toString(),
                outcome,
                record.companions.split("\n").filter { it.isNotBlank() }.joinToString(", "),
                TogetherActivities.byId(record.activityId)?.slug.orEmpty(),
                mood,
                record.note,
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
