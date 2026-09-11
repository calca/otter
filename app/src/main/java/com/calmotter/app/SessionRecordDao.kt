package com.calmotter.app

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SessionRecordDao {
    @Query("SELECT * FROM sessions ORDER BY startTimeMs DESC")
    fun getAll(): List<SessionRecord>

    @Insert
    fun insert(record: SessionRecord)

    @Query("DELETE FROM sessions")
    fun clear()
}
