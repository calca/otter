package com.calmotter.app

/**
 * Rappresenta una sessione di pausa completata (o interrotta).
 *
 * @param startTimeMs       timestamp inizio sessione (ms epoch)
 * @param plannedMinutes    durata scelta dall'utente (es. 30, 60…)
 * @param effectiveMinutes  durata effettiva — uguale a [plannedMinutes] se
 *                          scaduta naturalmente, inferiore se terminata prima
 *                          con la password dall'accountability partner
 * @param completedNaturally true = scadenza automatica, false = sblocco anticipato
 */
data class SessionRecord(
    val startTimeMs: Long,
    val plannedMinutes: Int,
    val effectiveMinutes: Int,
    val completedNaturally: Boolean
)
