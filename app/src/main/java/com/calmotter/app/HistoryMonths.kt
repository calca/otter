package com.calmotter.app

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** Le sessioni di un mese, per l'elenco della Cronologia (specs/history-by-month/). */
data class MonthGroup(val month: YearMonth, val sessions: List<SessionRecord>)

/** I mesi da mostrare e se ce ne sono di più vecchi da caricare. */
data class HistoryMonthsView(val months: List<MonthGroup>, val hasEarlier: Boolean)

/**
 * Quali mesi mostrare nell'elenco: sempre il mese corrente e il precedente
 * (anche vuoti, così il 1° del mese non sparisce tutto), più [extraMonths]
 * mesi più vecchi fra quelli che hanno sessioni, uno per ogni "Mostra mesi
 * precedenti". Limita solo ciò che si compone: le statistiche in cima
 * contano comunque tutto. Pura, per i test.
 */
fun historyMonths(
    sessions: List<SessionRecord>,
    now: Long,
    extraMonths: Int,
    zone: ZoneId = ZoneId.systemDefault(),
): HistoryMonthsView {
    val byMonth = sessions
        .groupBy { YearMonth.from(Instant.ofEpochMilli(it.startTimeMs).atZone(zone)) }
        .mapValues { (_, list) -> list.sortedByDescending { it.startTimeMs } }
    val current = YearMonth.from(Instant.ofEpochMilli(now).atZone(zone))
    val previous = current.minusMonths(1)
    val recent = listOf(current, previous).map { MonthGroup(it, byMonth[it].orEmpty()) }
    val older = byMonth.keys.filter { it < previous }.sortedDescending()
    val shownOlder = older.take(extraMonths).map { MonthGroup(it, byMonth.getValue(it)) }
    return HistoryMonthsView(recent + shownOlder, hasEarlier = older.size > extraMonths)
}
