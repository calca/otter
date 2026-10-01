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
    // Attività proposta in una pausa di gruppo (TogetherActivities), 0 =
    // nessuna — vedi specs/together-activity/.
    val activityId: Int = 0,
    // Risposta al momento di chiusura (Mood), null = nessuna risposta —
    // vedi specs/closing-moment/.
    val mood: Int? = null,
    // Nota del momento di chiusura, al massimo 30 caratteri; "" = nessuna.
    val note: String = "",
    // Come è finita la pausa (EndReason). completedNaturally resta perché
    // serie e obiettivi lo leggono — vedi specs/slow-exit/.
    val endReason: String = EndReason.UNKNOWN,
)

/** Come è finita una pausa — colonna `endReason` di [SessionRecord]. */
object EndReason {
    const val NATURAL = "natural"
    const val PASSWORD = "password"
    const val NFC_RELEASE = "nfc_release"
    const val SLOW_EXIT = "slow_exit"
    const val UNKNOWN = "unknown"
}

/** Risposte del momento di chiusura (specs/closing-moment/). */
object Mood {
    const val CALM = 0
    const val ORDINARY = 1
    const val HARD = 2
}
