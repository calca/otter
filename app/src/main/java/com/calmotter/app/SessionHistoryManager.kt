package com.calmotter.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persiste la cronologia delle sessioni come array JSON in SharedPreferences.
 * Nessuna libreria di serializzazione aggiuntiva: JSON semplice con le API
 * standard di Android. Le sessioni sono ordinate dalla più recente.
 *
 * Formato di ogni entry:
 * {
 *   "startTimeMs": 1234567890,
 *   "plannedMinutes": 60,
 *   "effectiveMinutes": 42,
 *   "completedNaturally": false
 * }
 */
class SessionHistoryManager private constructor(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAll(): List<SessionRecord> {
        val json = prefs.getString(KEY_HISTORY, "[]") ?: "[]"
        val array = JSONArray(json)
        return (0 until array.length())
            .map { array.getJSONObject(it).toRecord() }
    }

    fun add(record: SessionRecord) {
        val array = JSONArray(prefs.getString(KEY_HISTORY, "[]") ?: "[]")
        // Inserisce in testa così l'ordine è sempre più recente → più vecchio
        val newArray = JSONArray()
        newArray.put(record.toJson())
        for (i in 0 until array.length()) newArray.put(array.getJSONObject(i))
        prefs.edit().putString(KEY_HISTORY, newArray.toString()).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    // ── Serializzazione ────────────────────────────────────────────────────

    private fun SessionRecord.toJson() = JSONObject().apply {
        put("startTimeMs", startTimeMs)
        put("plannedMinutes", plannedMinutes)
        put("effectiveMinutes", effectiveMinutes)
        put("completedNaturally", completedNaturally)
    }

    private fun JSONObject.toRecord() = SessionRecord(
        startTimeMs        = getLong("startTimeMs"),
        plannedMinutes     = getInt("plannedMinutes"),
        effectiveMinutes   = getInt("effectiveMinutes"),
        completedNaturally = getBoolean("completedNaturally")
    )

    companion object {
        private const val PREFS_NAME = "calm_otter_history"
        private const val KEY_HISTORY = "sessions"

        @Volatile private var instance: SessionHistoryManager? = null

        fun getInstance(context: Context): SessionHistoryManager =
            instance ?: synchronized(this) {
                instance ?: SessionHistoryManager(context.applicationContext).also { instance = it }
            }
    }
}
