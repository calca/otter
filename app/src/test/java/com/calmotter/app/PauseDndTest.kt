package com.calmotter.app

import android.app.NotificationManager
import android.app.NotificationManager.Policy
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * [PauseDnd] da solo: cosa lascia passare la pausa, cosa fa senza permesso,
 * e il ripristino della policy dell'utente prima di Android 15 (da 15 in
 * su lo copre SessionManagerTest.endSessionSwitchesOffTheAppsOwnDndMode).
 */
@RunWith(RobolectricTestRunner::class)
class PauseDndTest {

    private lateinit var context: Context
    private lateinit var nm: NotificationManager
    private lateinit var dnd: PauseDnd

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        nm = context.getSystemService(NotificationManager::class.java)
        dnd = PauseDnd(context, context.getSharedPreferences("pause_dnd_test", Context.MODE_PRIVATE))
    }

    @Test
    fun thePauseLetsCallsAlarmsAndMediaThroughAndHidesTheRest() {
        shadowOf(nm).setNotificationPolicyAccessGranted(true)

        dnd.apply()

        assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, nm.currentInterruptionFilter)
        val policy = nm.notificationPolicy
        val allowed = Policy.PRIORITY_CATEGORY_CALLS or Policy.PRIORITY_CATEGORY_ALARMS or Policy.PRIORITY_CATEGORY_MEDIA
        assertEquals(allowed, policy.priorityCategories)
        // Chiamate da chiunque, non solo dai contatti preferiti.
        assertEquals(Policy.PRIORITY_SENDERS_ANY, policy.priorityCallSenders)
        val hidden = Policy.SUPPRESSED_EFFECT_BADGE or Policy.SUPPRESSED_EFFECT_NOTIFICATION_LIST or
            Policy.SUPPRESSED_EFFECT_STATUS_BAR
        assertEquals(hidden, policy.suppressedVisualEffects and hidden)
    }

    @Test
    fun withoutPermissionNothingIsTouched() {
        shadowOf(nm).setNotificationPolicyAccessGranted(false)
        val before = nm.currentInterruptionFilter

        dnd.capture()
        dnd.apply()
        dnd.restore()

        assertEquals(before, nm.currentInterruptionFilter)
    }

    /** Prima di Android 15 il DND è quello dell'utente: va restituito com'era. */
    @Test
    @Config(sdk = [34])
    fun beforeAndroid15TheUsersOwnPolicyComesBack() {
        shadowOf(nm).setNotificationPolicyAccessGranted(true)
        val users = Policy(Policy.PRIORITY_CATEGORY_ALARMS, 0, 0)
        nm.setNotificationPolicy(users)
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)

        dnd.capture()
        dnd.apply()
        assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, nm.currentInterruptionFilter)
        dnd.restore()

        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALARMS, nm.currentInterruptionFilter)
        assertEquals(Policy.PRIORITY_CATEGORY_ALARMS, nm.notificationPolicy.priorityCategories)
    }

    /** Nulla catturato (permesso arrivato a pausa iniziata): tutte le notifiche. */
    @Test
    @Config(sdk = [34])
    fun beforeAndroid15WithNothingCapturedEverythingIsLetThrough() {
        shadowOf(nm).setNotificationPolicyAccessGranted(true)
        dnd.apply()

        dnd.restore()

        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, nm.currentInterruptionFilter)
    }
}
