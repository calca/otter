package com.calmotter.app

import android.content.Context

/**
 * Persiste la cronologia delle sessioni tramite Room (tabella "sessions").
 * Le sessioni sono ordinate dalla più recente.
 */
class SessionHistoryManager internal constructor(context: Context) {

    private val dao = CalmOtterDatabase.getInstance(context).sessionRecordDao()

    fun getAll(): List<SessionRecord> = dao.getAll()

    /** Inserisce la sessione e ne restituisce l'id. */
    fun add(record: SessionRecord): Long = dao.insert(record)

    fun byId(id: Long): SessionRecord? = dao.byId(id)

    /** Salva la risposta del momento di chiusura (specs/closing-moment/). */
    fun saveReflection(id: Long, mood: Int?, note: String) = dao.updateReflection(id, mood, note)

    fun clear() = dao.clear()


    companion object {
        /** L'istanza dell'app: vive in [AppGraph], una per Application (vedi CalmOtterApplication). */
        fun getInstance(context: Context): SessionHistoryManager = context.appGraph.sessionHistoryManager
    }
}
