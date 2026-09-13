package com.calmotter.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SessionManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        // SessionHistoryManager/CalmOtterDatabase vanno resettati anche qui:
        // endSession() passa attraverso SessionHistoryManager -> Room, quindi
        // senza reset erediterebbero un'istanza legata al contesto (e allo
        // storage) di un test precedente.
        SessionManager.resetInstanceForTests()
        SessionHistoryManager.resetInstanceForTests()
        CalmOtterDatabase.resetInstanceForTests()
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun startSessionActivatesSessionWithExpectedDuration() {
        val manager = SessionManager.getInstance(context)

        manager.startSession(60)

        assertTrue(manager.isSessionActive())
        val remaining = manager.remainingMillis()
        assertTrue(remaining > 0)
        assertTrue(remaining <= 60 * 60_000L)
        assertEquals(60 * 60_000L, manager.totalMillis())
    }

    @Test
    fun endSessionDeactivatesAndRecordsHistory() {
        val manager = SessionManager.getInstance(context)
        manager.startSession(60)

        manager.endSession()

        assertFalse(manager.isSessionActive())
        val history = SessionHistoryManager.getInstance(context).getAll()
        assertEquals(1, history.size)
        assertEquals(60, history[0].plannedMinutes)
    }

    /**
     * L'icona di Cronologia (vedi HistoryScreen.kt/OtterHistoryIcon) dipende
     * interamente da questo campo arrivando correttamente fino alla riga
     * salvata — prima di questo test non c'era nessuna copertura automatica
     * sul round-trip isGroupSession, solo verifica manuale su device.
     */
    @Test
    fun groupSessionFlagIsPersistedThroughToHistory() {
        val manager = SessionManager.getInstance(context)

        manager.startSession(45, isGroupSession = true)
        assertTrue(manager.isGroupSession())

        manager.endSession()

        val history = SessionHistoryManager.getInstance(context).getAll()
        assertEquals(1, history.size)
        assertTrue(history[0].isGroupSession)
    }

    @Test
    fun soloSessionIsNotFlaggedAsGroup() {
        val manager = SessionManager.getInstance(context)

        manager.startSession(45)
        assertFalse(manager.isGroupSession())

        manager.endSession()

        val history = SessionHistoryManager.getInstance(context).getAll()
        assertFalse(history[0].isGroupSession)
    }

    /**
     * Rete di sicurezza: se il tempo di fine è già passato (l'allarme di
     * sistema non è scattato), isSessionActive() si auto-chiude.
     *
     * Nota implementativa: NON usiamo startSession(-1) per simularlo, perché
     * durationMinutes viene salvato as-is in KEY_PLANNED_MINUTES, e
     * endSession() registra nello storico solo se `plannedMinutes > 0`
     * (vedi SessionManager.endSession) — con -1 il guard fallisce e nessuna
     * riga verrebbe scritta, il che non è il caso reale che questo safety
     * net gestisce (una sessione con durata valida il cui allarme non è
     * scattato in tempo). Simuliamo quindi lo stesso identico stato che
     * produrrebbe un allarme mancato scrivendo direttamente su KEY_END_TIME,
     * la stessa SharedPreferences key che startSession() stesso userebbe,
     * dopo aver avviato una sessione con durata realistica.
     */
    @Test
    fun expiredSessionSelfClosesAndRecordsHistoryAsSafetyNet() {
        val manager = SessionManager.getInstance(context)
        manager.startSession(30)

        val prefs = context.getSharedPreferences("calm_otter_session", Context.MODE_PRIVATE)
        prefs.edit().putLong("session_end_time", System.currentTimeMillis() - 1_000L).apply()

        assertFalse(manager.isSessionActive())

        val history = SessionHistoryManager.getInstance(context).getAll()
        assertEquals(1, history.size)
        assertTrue(history[0].completedNaturally)
        assertEquals(30, history[0].plannedMinutes)
    }
}
