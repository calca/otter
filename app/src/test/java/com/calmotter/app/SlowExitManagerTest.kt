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

/** Impostazioni dell'uscita lenta (specs/slow-exit/). */
@RunWith(RobolectricTestRunner::class)
class SlowExitManagerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        context.getSharedPreferences("calm_otter_slow_exit", Context.MODE_PRIVATE).edit().clear().commit()
        SlowExitManager.resetInstanceForTests()
    }

    @Test
    fun onByDefaultWithTenMinutes() {
        val manager = SlowExitManager.getInstance(context)
        assertTrue(manager.isEnabled())
        assertEquals(10, manager.waitMinutes())
    }

    @Test
    fun thePasswordHolderCanSwitchItOffOrChangeTheWait() {
        val manager = SlowExitManager.getInstance(context)
        manager.update(enabled = true, waitMinutes = 15)
        assertEquals(15, manager.waitMinutes())
        manager.update(enabled = false)
        assertFalse(manager.isEnabled())
        assertEquals(15, manager.waitMinutes()) // l'attesa scelta resta per quando si riaccende
    }

    @Test
    fun aWaitThatIsNotOfferedFallsBackToTheDefault() {
        context.getSharedPreferences("calm_otter_slow_exit", Context.MODE_PRIVATE).edit().putInt("wait_minutes", 7).commit()
        assertEquals(SlowExitManager.DEFAULT_WAIT_MINUTES, SlowExitManager.getInstance(context).waitMinutes())
    }
}
