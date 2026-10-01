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

    /** Inserisce la sessione e ne restituisce l'id. */
    fun add(record: SessionRecord): Long = dao.insert(record)

    fun byId(id: Long): SessionRecord? = dao.byId(id)

    /** Salva la risposta del momento di chiusura (specs/closing-moment/). */
    fun saveReflection(id: Long, mood: Int?, note: String) = dao.updateReflection(id, mood, note)

    fun clear() = dao.clear()


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
