package com.calmotter.app

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// exportSchema = true (TODO.md "1.4"): prima non lasciava alcuna traccia
// verificabile dello schema effettivo di ciascuna versione — un'entità
// cambiata senza bump di versione o senza la migrazione giusta non veniva
// segnalata da nessuna parte finché non si rompeva su un device reale con
// dati vecchi. Da ora KSP scrive uno snapshot JSON per versione in
// app/schemas/ (vedi `room.schemaLocation` in app/build.gradle.kts),
// committato: cambiare un'entità senza aggiornare versione+migrazione fa
// fallire la build al prossimo giro. Le versioni 1 e 2 restano senza
// snapshot — non sono mai state esportate quando questa opzione era ancora
// `false`, e non sono ricostruibili a ritroso da qui: quello che si può
// verificare ora in avanti a ogni nuova versione parte da questa.
@Database(entities = [SessionRecord::class], version = 3, exportSchema = true)
abstract class CalmOtterDatabase : RoomDatabase() {
    abstract fun sessionRecordDao(): SessionRecordDao

    companion object {
        // v1 → v2: aggiunge SessionRecord.isGroupSession (specs/group-pause/).
        // DEFAULT 0 dà `false` a tutte le righe già esistenti — nessuna
        // sessione registrata prima di questa versione era di gruppo, quindi
        // è anche il valore corretto, non solo un placeholder.
        //
        // `internal`, non `private`: CalmOtterDatabaseMigrationTest le
        // esercita direttamente contro un file .db seminato a mano con lo
        // schema v1 (vedi TODO.md "1.4" — niente snapshot JSON storico da
        // cui generare questo test con MigrationTestHelper, quindi si passa
        // dalla stessa Room.databaseBuilder() + addMigrations() usata in
        // produzione, che valida comunque lo schema risultante alla stessa
        // maniera).
        @VisibleForTesting
        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN isGroupSession INTEGER NOT NULL DEFAULT 0")
            }
        }

        // v2 → v3: aggiunge SessionRecord.companions. DEFAULT '' è il valore
        // corretto e non un ripiego: nessuna riga precedente poteva conoscere
        // i nomi dei partecipanti, perché non viaggiavano oltre la lobby.
        @VisibleForTesting
        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN companions TEXT NOT NULL DEFAULT ''")
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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
