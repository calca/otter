package com.calmotter.app

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Rappresenta una sessione di pausa completata (o interrotta).
 *
 * Entità Room persistita nella tabella "sessions".
 *
 * @param id                chiave primaria autogenerata da Room
 * @param startTimeMs       timestamp inizio sessione (ms epoch)
 * @param plannedMinutes    durata scelta dall'utente (es. 30, 60…)
 * @param effectiveMinutes  durata effettiva — uguale a [plannedMinutes] se
 *                          scaduta naturalmente, inferiore se terminata prima
 *                          con la password dall'accountability partner
 * @param completedNaturally true = scadenza automatica, false = sblocco anticipato
 */
@Entity(tableName = "sessions")
data class SessionRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTimeMs: Long,
    val plannedMinutes: Int,
    val effectiveMinutes: Int,
    val completedNaturally: Boolean
)
