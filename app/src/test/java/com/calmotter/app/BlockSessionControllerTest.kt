package com.calmotter.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.calmotter.app.bluetooth.groupPauseUnlockToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Le decisioni della schermata di blocco (BlockSessionController): chi può
 * rilasciare chi e come finisce la pausa in ogni caso. Prima vivevano nel
 * composable e non erano provabili.
 */
@RunWith(RobolectricTestRunner::class)
class BlockSessionControllerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var session: SessionManager

    @Before
    fun setUp() {
        context.getSharedPreferences("calm_otter_slow_exit", Context.MODE_PRIVATE).edit().clear().commit()
        session = SessionManager.getInstance(context)
        session.endSession()
    }

    private fun controller(nfc: Boolean = true) =
        BlockSessionController(session, SlowExitManager.getInstance(context), nfcAvailable = nfc)

    private fun lastRecord() = SessionHistoryManager.getInstance(context).getAll().first()

    @Test
    fun theHostWhoUnlocksEarlyIsOfferedToReleaseTheOthers() {
        session.startSession(60, isGroupSession = true, companions = listOf("Marta"), groupTag = 7, isHost = true)
        val c = controller()
        assertTrue(c.canReleaseOthers)
        assertFalse(c.canBeReleasedByNfc)
        assertTrue(c.unlockWithPassword())
        assertEquals(EndReason.PASSWORD, lastRecord().endReason)
    }

    @Test
    fun aHostWithoutKnownCompanionsHasNobodyToRelease() {
        // Percorso QR/codice: non si sa con chi si era.
        session.startSession(60, isGroupSession = true, groupTag = 7, isHost = true)
        assertFalse(controller().unlockWithPassword())
    }

    @Test
    fun withoutNfcThereIsNoReleaseEitherWay() {
        session.startSession(60, isGroupSession = true, companions = listOf("Marta"), groupTag = 7, isHost = true)
        assertFalse(controller(nfc = false).canReleaseOthers)
        session.endSession()
        session.startSession(60, isGroupSession = true, companions = listOf("Luca"), groupTag = 7, isHost = false)
        assertFalse(controller(nfc = false).canBeReleasedByNfc)
    }

    @Test
    fun aGuestIsReleasedOnlyByTheTokenOfThisPause() {
        session.startSession(60, isGroupSession = true, companions = listOf("Luca"), groupTag = 7, isHost = false)
        val c = controller()
        assertTrue(c.canBeReleasedByNfc)
        assertFalse(c.canReleaseOthers)

        assertFalse(c.releaseByNfc(groupPauseUnlockToken(8)))
        assertTrue(session.isSessionActive())

        assertTrue(c.releaseByNfc(groupPauseUnlockToken(7)))
        assertFalse(session.isSessionActive())
        assertEquals(EndReason.NFC_RELEASE, lastRecord().endReason)
    }

    @Test
    fun aSoloPauseCannotBeReleasedByNfc() {
        session.startSession(60)
        val c = controller()
        assertFalse(c.canBeReleasedByNfc)
        assertFalse(c.releaseByNfc(groupPauseUnlockToken(0)))
        assertFalse(c.unlockWithPassword())
    }

    @Test
    fun theNaturalEndIsCompleteAndNeverOffersARelease() {
        session.startSession(60, isGroupSession = true, companions = listOf("Marta"), groupTag = 7, isHost = true)
        controller().finishNaturally()
        val record = lastRecord()
        assertTrue(record.completedNaturally)
        assertEquals(EndReason.NATURAL, record.endReason)
    }

    @Test
    fun theSlowExitWaitsThenEndsWithoutPassword() {
        session.startSession(60, isGroupSession = true, companions = listOf("Marta"), groupTag = 7, isHost = true)
        val c = controller()
        assertTrue(c.slowExitAvailable)
        val deadline = c.startSlowExit()
        assertTrue(kotlin.math.abs(deadline - System.currentTimeMillis() - 10 * 60_000L) < 2_000L)
        assertEquals(0.5f, c.slowExitProgress(deadline, now = deadline - 5 * 60_000L), 0.01f)

        assertTrue(c.finishSlowExit()) // l'host esce prima: gli altri vanno rilasciati
        assertEquals(EndReason.SLOW_EXIT, lastRecord().endReason)
    }

    @Test
    fun progressAndBreathingFollowTheDuration() {
        session.startSession(10)
        val c = controller()
        assertTrue(c.isBreathing)
        assertEquals(0.5f, c.progress(remainingMillis = 5 * 60_000L), 0.01f)
        session.endSession()
        session.startSession(60)
        assertFalse(controller().isBreathing)
    }
}
