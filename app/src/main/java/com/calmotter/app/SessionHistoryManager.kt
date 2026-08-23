package com.calmotter.app

import android.content.Context

/**
 * Persiste la cronologia delle sessioni tramite Room (tabella "sessions").
 * Le sessioni sono ordinate dalla più recente.
 */
class SessionHistoryManager private constructor(context: Context) {

    private val dao = CalmOtterDatabase.getInstance(context).sessionRecordDao()

    fun getAll(): List<SessionRecord> = dao.getAll()

    fun add(record: SessionRecord) = dao.insert(record)

    fun clear() = dao.clear()

    companion object {
        @Volatile private var instance: SessionHistoryManager? = null

        fun getInstance(context: Context): SessionHistoryManager =
            instance ?: synchronized(this) {
                instance ?: SessionHistoryManager(context.applicationContext).also { instance = it }
            }
    }
}
