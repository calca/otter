package com.calmotter.app

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [SessionRecord::class], version = 2, exportSchema = false)
abstract class CalmOtterDatabase : RoomDatabase() {
    abstract fun sessionRecordDao(): SessionRecordDao

    companion object {
        // v1 → v2: aggiunge SessionRecord.isGroupSession (specs/group-pause/).
        // DEFAULT 0 dà `false` a tutte le righe già esistenti — nessuna
        // sessione registrata prima di questa versione era di gruppo, quindi
        // è anche il valore corretto, non solo un placeholder.
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN isGroupSession INTEGER NOT NULL DEFAULT 0")
            }
        }

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
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }

        @VisibleForTesting
        internal fun resetInstanceForTests() {
            instance?.close()
            instance = null
        }
    }
}
