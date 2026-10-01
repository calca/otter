package com.calmotter.app

/** Il tempo passato in pausa con una persona (specs/together-history/). */
data class CompanionTotal(
    val name: String,
    val minutes: Int,
    val pauses: Int,
    /** Fino a tre categorie di attività, le più frequenti, ognuna una volta. */
    val topCategories: List<TogetherCategory>,
)

/**
 * Somma, per ogni persona, il tempo delle pause fatte insieme fra [fromMs]
 * (incluso) e [toMs] (escluso); null = senza limite. Regole:
 * - i nomi si raggruppano ignorando maiuscole e spazi attorno; grafie diverse
 *   della stessa persona restano separate (niente indovinelli). Si mostra la
 *   grafia della pausa più recente;
 * - una pausa con due persone conta per intero per ciascuna, con il tempo
 *   effettivo (anche se finita prima);
 * - le categorie vengono dall'attività proposta; le pause senza attività non
 *   contano; a parità di frequenza vince la più recente.
 * Ordinate dalla persona con più tempo.
 */
fun companionTotals(
    sessions: List<SessionRecord>,
    fromMs: Long? = null,
    toMs: Long? = null,
): List<CompanionTotal> {
    class Acc(val name: String) {
        var minutes = 0
        var pauses = 0
        val categoryCount = mutableMapOf<TogetherCategory, Int>()
        val categoryLast = mutableMapOf<TogetherCategory, Long>()
    }
    val byKey = linkedMapOf<String, Acc>()
    sessions
        .filter { it.isGroupSession }
        .filter { fromMs == null || it.startTimeMs >= fromMs }
        .filter { toMs == null || it.startTimeMs < toMs }
        .sortedByDescending { it.startTimeMs }
        .forEach { session ->
            val category = TogetherActivities.byId(session.activityId)?.category
            session.companions.split("\n")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinctBy { it.lowercase() }
                .forEach { name ->
                    val acc = byKey.getOrPut(name.lowercase()) { Acc(name) }
                    acc.minutes += session.effectiveMinutes
                    acc.pauses += 1
                    if (category != null) {
                        acc.categoryCount[category] = (acc.categoryCount[category] ?: 0) + 1
                        acc.categoryLast.putIfAbsent(category, session.startTimeMs)
                    }
                }
        }
    return byKey.values
        .map { acc ->
            CompanionTotal(
                name = acc.name,
                minutes = acc.minutes,
                pauses = acc.pauses,
                topCategories = acc.categoryCount.keys
                    .sortedWith(
                        compareByDescending<TogetherCategory> { acc.categoryCount[it] }
                            .thenByDescending { acc.categoryLast[it] }
                    )
                    .take(3),
            )
        }
        .sortedByDescending { it.minutes }
}
