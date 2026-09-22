package com.calmotter.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * TODO.md "1.4": prima nulla verificava che MIGRATION_1_2/MIGRATION_2_3
 * (CalmOtterDatabase.kt) applicassero davvero lo schema atteso — una
 * migrazione sbagliata sarebbe stata scoperta solo da un utente vero con
 * dati vecchi, con la cronologia come unica cosa persistita localmente
 * senza backend a fare da rete di sicurezza.
 *
 * Non usa `MigrationTestHelper` con schema JSON storici: `exportSchema`
 * è stato `false` fino a questa stessa modifica, quindi non esiste (e non
 * è ricostruibile a ritroso) uno snapshot per le versioni 1 e 2 da cui
 * quell'helper avrebbe bisogno di partire. Il file v1 qui sotto è quindi
 * scritto a mano — lo schema esatto è ricavabile senza ambiguità dalle due
 * `ALTER TABLE` già presenti in MIGRATION_1_2/MIGRATION_2_3, che elencano
 * esplicitamente le sole due colonne aggiunte rispetto a v1, più il
 * confronto diretto con `SessionRecord.kt` per i tipi delle colonne
 * originarie.
 *
 * Passa lo stesso identico `Room.databaseBuilder().addMigrations(...)`
 * usato in produzione (`CalmOtterDatabase.getInstance`): Room valida da
 * sé lo schema risultante rispetto a quello compilato dalle entità
 * correnti a ogni apertura post-migrazione, indipendentemente da
 * `exportSchema` — se una migrazione lascia colonne, tipi o vincoli
 * sbagliati, apre lanciando un'eccezione a prescindere da qualunque file
 * JSON. Questo è quindi un test reale sul comportamento delle migrazioni,
 * non solo sulla loro presenza.
 */
@RunWith(RobolectricTestRunner::class)
class CalmOtterDatabaseMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "migration-test.db"

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    /**
     * Scrive un file .db con esattamente lo schema v1 (prima di
     * isGroupSession e companions) e una riga di dati preesistenti — la
     * situazione reale di chi aggiorna l'app da prima che queste due
     * colonne esistessero.
     */
    private fun seedVersion1Database(): SQLiteDatabase {
        val dbFile = context.getDatabasePath(dbName)
        val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        db.execSQL(
            "CREATE TABLE sessions (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "startTimeMs INTEGER NOT NULL, " +
                "plannedMinutes INTEGER NOT NULL, " +
                "effectiveMinutes INTEGER NOT NULL, " +
                "completedNaturally INTEGER NOT NULL)"
        )
        db.execSQL(
            "INSERT INTO sessions (startTimeMs, plannedMinutes, effectiveMinutes, completedNaturally) " +
                "VALUES (1700000000000, 30, 28, 0)"
        )
        db.version = 1
        return db
    }

    @Test
    fun migratingFromVersion1BackfillsNewColumnsWithTheirDocumentedDefaults() {
        seedVersion1Database().close()

        val db = Room.databaseBuilder(context, CalmOtterDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .addMigrations(CalmOtterDatabase.MIGRATION_1_2, CalmOtterDatabase.MIGRATION_2_3)
            .build()

        try {
            val rows = db.sessionRecordDao().getAll()
            assertEquals(1, rows.size)
            val migrated = rows[0]

            // I valori di prima non devono muoversi durante la migrazione.
            assertEquals(1700000000000L, migrated.startTimeMs)
            assertEquals(30, migrated.plannedMinutes)
            assertEquals(28, migrated.effectiveMinutes)
            assertEquals(false, migrated.completedNaturally)

            // DEFAULT documentati in MIGRATION_1_2/MIGRATION_2_3: nessuna riga
            // precedente a queste due colonne poteva essere una pausa di
            // gruppo con nomi noti, quindi false/"" sono i valori corretti,
            // non un semplice ripiego tecnico.
            assertEquals(false, migrated.isGroupSession)
            assertEquals("", migrated.companions)
        } finally {
            db.close()
        }
    }

    /**
     * Una migrazione che "sembra" corretta riga per riga ma lascia lo schema
     * finale diverso da quello compilato dalle entità (colonna mancante,
     * tipo sbagliato, `NOT NULL` perso) fa fallire l'apertura del database
     * con un'eccezione di Room a runtime — non serve un confronto esplicito
     * con un JSON di schema per accorgersene. Verificato qui aprendo e
     * scrivendo attraverso lo stesso Dao usato in produzione, sullo schema
     * ottenuto risalendo da v1 fino a v3.
     */
    @Test
    fun databaseMigratedFromVersion1AcceptsNewWritesAfterward() {
        seedVersion1Database().close()

        val db = Room.databaseBuilder(context, CalmOtterDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .addMigrations(CalmOtterDatabase.MIGRATION_1_2, CalmOtterDatabase.MIGRATION_2_3)
            .build()

        try {
            db.sessionRecordDao().insert(
                SessionRecord(
                    startTimeMs = 1700003600000L,
                    plannedMinutes = 60,
                    effectiveMinutes = 60,
                    completedNaturally = true,
                    isGroupSession = true,
                    companions = "Alex\nSam",
                )
            )

            val rows = db.sessionRecordDao().getAll()
            assertEquals(2, rows.size)
            assertTrue(rows.any { it.isGroupSession && it.companions == "Alex\nSam" })
        } finally {
            db.close()
        }
    }
}
