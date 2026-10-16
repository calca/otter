package com.calmotter.app

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * I due allarmi di una pausa: la fine naturale e la fine dell'attesa
 * dell'uscita lenta. Sono indipendenti: annullarne uno non tocca l'altro.
 */
@RunWith(RobolectricTestRunner::class)
class SessionAlarmsTest {

    private lateinit var context: Context
    private lateinit var am: AlarmManager
    private lateinit var alarms: SessionAlarms

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        am = context.getSystemService(AlarmManager::class.java)
        alarms = SessionAlarms(context)
    }

    @Test
    fun theExpiryIsArmedAtTheEndOfThePauseAndWakesThePhone() {
        val end = System.currentTimeMillis() + 60 * 60_000L

        alarms.scheduleExpiry(end)

        val alarm = shadowOf(am).scheduledAlarms.single()
        assertEquals(end, alarm.triggerAtTime)
        assertEquals(AlarmManager.RTC_WAKEUP, alarm.type)
        // Arriva al receiver della scadenza, senza azione: è la fine naturale.
        val intent = shadowOf(alarm.operation).savedIntent
        assertEquals(SessionExpiryReceiver::class.java.name, intent.component?.className)
        assertNull(intent.action)
    }

    @Test
    fun armingTheExpiryTwiceKeepsOneAlarm() {
        alarms.scheduleExpiry(System.currentTimeMillis() + 60_000L)
        alarms.scheduleExpiry(System.currentTimeMillis() + 120_000L)

        assertEquals(1, shadowOf(am).scheduledAlarms.size)
    }

    @Test
    fun theSlowExitHasItsOwnAlarmAndCancellingOneLeavesTheOther() {
        val end = System.currentTimeMillis() + 60 * 60_000L
        val deadline = System.currentTimeMillis() + 10 * 60_000L

        alarms.scheduleExpiry(end)
        alarms.scheduleSlowExit(deadline)
        assertEquals(2, shadowOf(am).scheduledAlarms.size)

        alarms.cancelSlowExit()
        assertEquals(end, shadowOf(am).scheduledAlarms.single().triggerAtTime)

        alarms.scheduleSlowExit(deadline)
        alarms.cancelExpiry()
        val left = shadowOf(am).scheduledAlarms.single()
        assertEquals(deadline, left.triggerAtTime)
        assertEquals(SessionExpiryReceiver.ACTION_SLOW_EXIT, shadowOf(left.operation).savedIntent.action)
    }
}
