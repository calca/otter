package com.calmotter.app

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SessionRecord::class], version = 1, exportSchema = false)
abstract class CalmOtterDatabase : RoomDatabase() {
    abstract fun sessionRecordDao(): SessionRecordDao

    companion object {
        @Volatile private var instance: CalmOtterDatabase? = null

        fun getInstance(context: Context): CalmOtterDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CalmOtterDatabase::class.java,
                    "calm_otter.db"
                )
                    // Dataset locale piccolo (poche sessioni al giorno) e chiamato
                    // in modo sincrono da BroadcastReceiver senza coroutine scope:
                    // query sul main thread accettabili qui.
                    .allowMainThreadQueries()
                    .build()
                    .also { instance = it }
            }
    }
}
