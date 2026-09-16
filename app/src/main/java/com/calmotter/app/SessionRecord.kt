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
 * @param isGroupSession    true se avviata da GroupPauseHostActivity/
 *                          GroupPauseJoinActivity (vedi specs/group-pause/)
 *                          invece che dal tap sull'otter in Home — solo
 *                          un'etichetta per Cronologia, default `false` per
 *                          restare compatibile con le righe già esistenti
 *                          (vedi CalmOtterDatabase.MIGRATION_1_2)
 * @param companions        nomi di chi ha condiviso questa pausa, separati da
 *                          "\n" — vuoto sia per le sessioni in solitaria sia
 *                          per quelle di gruppo nate dal percorso QR/codice,
 *                          che non ha alcun canale da cui apprendere un nome
 *                          (vedi specs/group-pause/). Un'unica colonna di
 *                          testo invece di una tabella a parte: sono due o tre
 *                          nomi per riga, mai interrogati singolarmente.
 */
@Entity(tableName = "sessions")
data class SessionRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTimeMs: Long,
    val plannedMinutes: Int,
    val effectiveMinutes: Int,
    val completedNaturally: Boolean,
    val isGroupSession: Boolean = false,
    val companions: String = "",
)
