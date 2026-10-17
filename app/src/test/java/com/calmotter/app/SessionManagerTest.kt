package com.calmotter.app

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class SessionManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        // SessionHistoryManager/CalmOtterDatabase vanno resettati anche qui:
        // endSession() passa attraverso SessionHistoryManager -> Room, quindi
        // senza reset erediterebbero un'istanza legata al contesto (e allo
        // storage) di un test precedente.
        // startSession() salva il profilo di app consentite scelto.
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

    /**
     * Segnalato direttamente ("mi trovo in home page senza info della
     * fine"): se la pausa scade mentre l'utente non c'è (qui simulato con la
     * stessa rete di sicurezza del test sopra), la Home deve poter mostrare
     * un riepilogo una tantum — consumePendingBackgroundSummary() deve
     * restituirlo la prima volta, e null da lì in poi.
     */
    @Test
    fun expiredSessionSelfCloseMarksPendingBackgroundSummary() {
        val manager = SessionManager.getInstance(context)
        manager.startSession(30)

        val prefs = context.getSharedPreferences("calm_otter_session", Context.MODE_PRIVATE)
        prefs.edit().putLong("session_end_time", System.currentTimeMillis() - 1_000L).apply()

        assertFalse(manager.isSessionActive())

        // effectiveMinutes qui è ~0, non plannedMinutes (30): il test avvia
        // e chiude la sessione nello stesso istante, sposta solo
        // KEY_END_TIME nel passato — vedi il commento sul test gemello sopra
        // per perché è questo il modo giusto di simulare un allarme mancato.
        // Quel che conta qui è solo che il riepilogo esista e si consumi
        // una volta sola.
        val summary = manager.consumePendingBackgroundSummary()
        assertTrue(summary != null)
        assertTrue(manager.consumePendingBackgroundSummary() == null)
    }

    /**
     * Contrario del test sopra: se è BlockScreen (l'utente con lo schermo
     * davanti) a chiudere per primo la sessione, senza passare da
     * markBackgroundSummary, la guardia di idempotenza rende la successiva
     * chiamata di SessionExpiryReceiver un no-op — il riepilogo non deve
     * comparire, l'utente c'era già.
     */
    @Test
    fun endSessionSeenLiveDoesNotMarkPendingBackgroundSummary() {
        val manager = SessionManager.getInstance(context)
        manager.startSession(60)

        manager.endSession(completedNaturally = true)
        manager.endSession(completedNaturally = true, markBackgroundSummary = true)

        assertTrue(manager.consumePendingBackgroundSummary() == null)
    }

    /**
     * TODO.md "1.1": a scadenza naturale sia SessionExpiryReceiver (l'allarme
     * di sistema) sia il loop del conto alla rovescia di BlockScreen
     * chiamano endSession(), indipendentemente l'uno dall'altro — avere lo
     * schermo di blocco aperto proprio alla scadenza è il caso comune, non
     * un caso limite. Senza la guardia di idempotenza in endSession(), la
     * stessa pausa finiva registrata due volte.
     */
    @Test
    fun endSessionCalledTwiceRecordsHistoryOnlyOnce() {
        val manager = SessionManager.getInstance(context)
        manager.startSession(60)

        manager.endSession(completedNaturally = true)
        manager.endSession(completedNaturally = true)

        val history = SessionHistoryManager.getInstance(context).getAll()
        assertEquals(1, history.size)
    }

    /**
     * Da Android 15 (il livello su cui girano questi test) la pausa accende
     * una modalità Non disturbare *dell'app*, separata da quella
     * dell'utente: a fine pausa deve spegnere la propria e basta. Prima
     * ripristinava il filtro catturato all'inizio, e se l'utente aveva già
     * il suo DND acceso la modalità dell'app restava ACCESA dopo la pausa
     * (segnalato). Il ripristino della policy catturata resta solo per
     * Android 14 e precedenti, dove il Non disturbare era uno solo.
     */
    @Test
    fun endSessionSwitchesOffTheAppsOwnDndMode() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        shadowOf(nm).setNotificationPolicyAccessGranted(true)
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
        nm.setNotificationPolicy(NotificationManager.Policy(NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS, 0, 0))

        val manager = SessionManager.getInstance(context)
        manager.startSession(60)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, nm.currentInterruptionFilter)

        manager.endSession()

        // La modalità dell'app spenta, non riaccesa con il filtro di prima.
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, nm.currentInterruptionFilter)
    }

    /**
     * Segnalato: durante una pausa programmata arrivavano le notifiche. Se
     * qualcosa spegne Non disturbare a pausa in corso (una routine Samsung,
     * un tocco nella tendina), il controllo di ogni minuto lo riaccende.
     */
    @Test
    fun dndSwitchedOffDuringAPauseIsSwitchedBackOn() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        shadowOf(nm).setNotificationPolicyAccessGranted(true)
        val manager = SessionManager.getInstance(context)
        manager.startSession(60)
        assertFalse(manager.ensureDndApplied()) // già a posto: niente da fare

        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        assertTrue(manager.ensureDndApplied())
        assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, nm.currentInterruptionFilter)

        manager.endSession()
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        // Senza pausa non tocca nulla.
        assertFalse(manager.ensureDndApplied())
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, nm.currentInterruptionFilter)
    }

    // --- specs/closing-moment, specs/slow-exit, specs/together-activity ----

    private fun lastRecord(): SessionRecord =
        SessionHistoryManager.getInstance(context).getAll().first()

    @Test
    fun aNaturallyCompletedPauseLeavesAPendingReflection() {
        val manager = SessionManager.getInstance(context)
        manager.startSession(30)
        manager.endSession(completedNaturally = true)

        val record = lastRecord()
        assertEquals(EndReason.NATURAL, record.endReason)
        assertEquals(record.id, manager.pendingReflectionId())

        manager.clearPendingReflection()
        assertEquals(0L, manager.pendingReflectionId())
    }

    @Test
    fun anEarlyEndNeverAsksHowItWent() {
        val manager = SessionManager.getInstance(context)
        manager.clearPendingReflection()
        manager.startSession(30)
        manager.endSession()

        assertEquals(EndReason.PASSWORD, lastRecord().endReason)
        assertEquals(0L, manager.pendingReflectionId())
    }

    @Test
    fun theNfcReleaseIsRecordedAsSuch() {
        val manager = SessionManager.getInstance(context)
        manager.startSession(30, isGroupSession = true, groupTag = 7)
        manager.endSession(reason = EndReason.NFC_RELEASE)
        assertEquals(EndReason.NFC_RELEASE, lastRecord().endReason)
    }

    @Test
    fun theSlowExitEndsThePauseWithoutPasswordOnceTheWaitIsOver() {
        val manager = SessionManager.getInstance(context)
        manager.startSession(60)
        manager.startSlowExit(waitMinutes = 10)
        assertTrue(manager.slowExitDeadline() > System.currentTimeMillis())
        assertTrue(manager.isSessionActive())

        // L'attesa è finita (l'allarme è inesatto: la rete di sicurezza è isSessionActive()).
        context.getSharedPreferences("calm_otter_session", Context.MODE_PRIVATE).edit()
            .putLong("session_slow_exit_deadline", System.currentTimeMillis() - 1).commit()

        assertFalse(manager.isSessionActive())
        val record = lastRecord()
        assertEquals(EndReason.SLOW_EXIT, record.endReason)
        assertFalse(record.completedNaturally)
        assertEquals(0L, manager.slowExitDeadline())
    }

    @Test
    fun cancellingTheSlowExitKeepsThePauseGoing() {
        val manager = SessionManager.getInstance(context)
        manager.startSession(60)
        manager.startSlowExit(waitMinutes = 10)
        manager.cancelSlowExit()
        assertEquals(0L, manager.slowExitDeadline())
        assertTrue(manager.isSessionActive())
    }

    @Test
    fun theTogetherActivityEndsUpInHistory() {
        val manager = SessionManager.getInstance(context)
        manager.startSession(60, isGroupSession = true, activityId = 7)
        assertEquals(7, manager.groupActivityId())
        manager.endSession(completedNaturally = true)
        assertEquals(7, lastRecord().activityId)
        assertEquals(0, manager.groupActivityId())
    }

    // --- Origine della pausa (PauseOrigin) ----------------------------------

    @Test
    fun aManualPauseKeepsTheGenericLabelAndNoWelcome() {
        val manager = SessionManager.getInstance(context)
        manager.startSession(30)

        assertEquals(PauseOrigin.MANUAL, manager.origin())
        assertEquals(null, manager.originLabel())
        assertFalse(manager.consumeArrivalWelcome())
    }

    @Test
    fun anAutomaticPauseSaysWhereItCameFromAndWelcomesOnce() {
        val manager = SessionManager.getInstance(context)
        manager.startSession(60, origin = PauseOrigin.SCHEDULED, originDetail = 21 * 60)

        assertEquals(context.getString(R.string.pause_origin_scheduled, "21:00"), manager.originLabel())
        assertTrue(manager.consumeArrivalWelcome())
        // La schermata si riapre a ogni app bloccata: il benvenuto no.
        assertFalse(manager.consumeArrivalWelcome())
    }

    @Test
    fun theUsageLabelCarriesTheRuleMinutes() {
        val manager = SessionManager.getInstance(context)
        manager.startSession(10, origin = PauseOrigin.USAGE, originDetail = 45)

        assertEquals(context.getString(R.string.pause_origin_usage, 45), manager.originLabel())
    }

    @Test
    fun theOriginDoesNotOutliveThePause() {
        val manager = SessionManager.getInstance(context)
        manager.startSession(60, origin = PauseOrigin.SURPRISE)
        manager.endSession()
        manager.startSession(30)

        assertEquals(PauseOrigin.MANUAL, manager.origin())
        assertFalse(manager.consumeArrivalWelcome())
    }
}
