package com.calmotter.app

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SessionRecordDao {
    @Query("SELECT * FROM sessions ORDER BY startTimeMs DESC")
    fun getAll(): List<SessionRecord>

    @Insert
    fun insert(record: SessionRecord): Long

    @Query("SELECT * FROM sessions WHERE id = :id")
    fun byId(id: Long): SessionRecord?

    @Query("UPDATE sessions SET mood = :mood, note = :note WHERE id = :id")
    fun updateReflection(id: Long, mood: Int?, note: String)

    @Query("DELETE FROM sessions")
    fun clear()
}
