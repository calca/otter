package com.calmotter.app

import android.content.Context
import androidx.annotation.VisibleForTesting

/**
 * Persiste la cronologia delle sessioni tramite Room (tabella "sessions").
 * Le sessioni sono ordinate dalla più recente.
 */
class SessionHistoryManager private constructor(context: Context) {

    private val dao = CalmOtterDatabase.getInstance(context).sessionRecordDao()

    fun getAll(): List<SessionRecord> = dao.getAll()

    fun add(record: SessionRecord) = dao.insert(record)

    fun clear() = dao.clear()

    /**
     * L'ultima pausa condivisa di cui si conoscano i nomi, per la scorciatoia
     * "Di nuovo con…" in Home. `getAll()` è già ordinata dalla più recente, e
     * il dataset è di poche righe: nessuna query dedicata.
     *
     * Ignora le pause di gruppo senza nomi (percorso QR/codice): senza un nome
     * la scorciatoia non avrebbe niente da dire.
     */
    fun lastCompanionSession(): SessionRecord? =
        dao.getAll().firstOrNull { it.isGroupSession && it.companions.isNotBlank() }

    companion object {
        @Volatile private var instance: SessionHistoryManager? = null

        fun getInstance(context: Context): SessionHistoryManager =
            instance ?: synchronized(this) {
                instance ?: SessionHistoryManager(context.applicationContext).also { instance = it }
            }

        @VisibleForTesting
        internal fun resetInstanceForTests() {
            instance = null
        }
    }
}
