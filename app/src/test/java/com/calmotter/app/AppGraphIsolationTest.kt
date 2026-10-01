package com.calmotter.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Le istanze dell'app vivono nell'[AppGraph] dell'Application, e Robolectric
 * crea un'Application nuova per ogni test: nessun test deve più ricordarsi di
 * resettare i gestori. Questi due test lo verificano a vicenda — qualunque
 * sia l'ordine, il secondo troverebbe la pausa del primo se lo stato
 * passasse da un test all'altro.
 */
@RunWith(RobolectricTestRunner::class)
class AppGraphIsolationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun startsCleanAndLeavesAPauseRunning() {
        val session = SessionManager.getInstance(context)
        assertFalse("stato ereditato da un altro test", session.isSessionActive())
        assertTrue(SessionHistoryManager.getInstance(context).getAll().isEmpty())
        session.startSession(60)
        session.endSession()
        session.startSession(60)
        assertTrue(session.isSessionActive())
    }

    @Test
    fun first() = startsCleanAndLeavesAPauseRunning()

    @Test
    fun second() = startsCleanAndLeavesAPauseRunning()

    @Test
    fun theApplicationIsOurs() {
        assertTrue(context is CalmOtterApplication)
    }
}
