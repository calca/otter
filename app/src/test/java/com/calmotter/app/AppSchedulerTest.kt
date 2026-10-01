package com.calmotter.app

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AppSchedulerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        context.getSharedPreferences("calm_otter_schedules", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun alarmRequestCodesNeverCollide() {
        val fixed = listOf(AlarmIds.SESSION_EXPIRY, AlarmIds.SLOW_EXIT, AlarmIds.WEEKLY_NOTE)
        val schedules = (1..500).flatMap { listOf(AlarmIds.schedule(it, start = false), AlarmIds.schedule(it, start = true)) }
        val all = fixed + schedules
        assertEquals(all.size, all.toSet().size)
    }

    @Test
    fun aTimeZoneChangeReArmsTheLocalTimeAlarms() {
        ScheduleManager.getInstance(context).save(
            ScheduledPause(id = 0, days = 0b1111111, startMinuteOfDay = 21 * 60, durationMinutes = 60)
        )
        val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
        assertTrue(alarms.scheduledAlarms.isEmpty())

        BootReceiver().onReceive(context, Intent(Intent.ACTION_TIMEZONE_CHANGED))

        // Nota della domenica + avviso e partenza della pausa programmata
        // (l'avviso solo se mancano più di 5 minuti: alle 21:00 di oggi o domani c'è sempre).
        assertTrue(alarms.scheduledAlarms.size >= 2)
    }
}
