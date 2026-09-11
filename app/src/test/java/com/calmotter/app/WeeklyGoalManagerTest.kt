package com.calmotter.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WeeklyGoalManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        WeeklyGoalManager.resetInstanceForTests()
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun defaultGoalIsNull() {
        val manager = WeeklyGoalManager.getInstance(context)
        assertNull(manager.getGoal())
    }

    @Test
    fun setGoalRoundTrips() {
        val manager = WeeklyGoalManager.getInstance(context)

        manager.setGoal(WeeklyGoal(GoalType.SESSIONS, 5))
        val goal = manager.getGoal()

        assertNotNull(goal)
        assertEquals(GoalType.SESSIONS, goal!!.type)
        assertEquals(5, goal.target)
    }

    @Test
    fun zeroTargetIsTreatedAsNoGoalSet() {
        val manager = WeeklyGoalManager.getInstance(context)

        // setGoal con target 0 tramite l'API pubblica: getGoal() applica il
        // guard `target <= 0 -> null`, quindi simula uno stato "corrotto"/di
        // default senza dover manipolare le SharedPreferences a mano.
        manager.setGoal(WeeklyGoal(GoalType.MINUTES, 0))

        assertNull(manager.getGoal())
    }
}
