package com.calmotter.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Profili di app consentite (specs/allowed-app-profiles/). */
@RunWith(RobolectricTestRunner::class)
class AllowedAppsManagerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var manager: AllowedAppsManager

    @Before
    fun setUp() {
        context.getSharedPreferences("calm_otter_allowed_apps", Context.MODE_PRIVATE).edit().clear().commit()
        manager = AllowedAppsManager.getInstance(context)
    }

    @Test
    fun theDefaultProfileAlwaysExistsAndKeepsTheOldList() {
        // La lista unica di prima stava sotto "allowed_packages": diventa il predefinito.
        context.getSharedPreferences("calm_otter_allowed_apps", Context.MODE_PRIVATE)
            .edit().putStringSet("allowed_packages", setOf("com.maps")).commit()
        val profiles = manager.profiles()
        assertEquals(1, profiles.size)
        assertTrue(profiles[0].isDefault)
        assertEquals(setOf("com.maps"), profiles[0].packages)
    }

    @Test
    fun eachProfileHasItsOwnApps() {
        val evening = manager.createProfile("Sera")!!
        manager.setPackages(AllowedAppsManager.DEFAULT_PROFILE_ID, setOf("com.maps"))
        manager.setPackages(evening.id, emptySet())
        val work = manager.createProfile("  Lavoro ")!!
        manager.setPackages(work.id, setOf("com.slack"))

        assertEquals(setOf("com.maps"), manager.packagesFor(AllowedAppsManager.DEFAULT_PROFILE_ID))
        assertEquals(emptySet<String>(), manager.packagesFor(evening.id))
        assertEquals(setOf("com.slack"), manager.packagesFor(work.id))
        assertEquals("Lavoro", manager.profile(work.id).name)
        assertEquals(listOf(0, evening.id, work.id), manager.profiles().map { it.id })
    }

    @Test
    fun atMostFourProfilesBesidesTheDefault() {
        repeat(AllowedAppsManager.MAX_EXTRA_PROFILES) { assertTrue(manager.createProfile("P$it") != null) }
        assertFalse(manager.canCreateProfile())
        assertNull(manager.createProfile("troppi"))
    }

    @Test
    fun deletingTheSelectedProfileFallsBackToTheDefault() {
        val evening = manager.createProfile("Sera")!!
        manager.selectProfile(evening.id)
        assertEquals(evening.id, manager.selectedProfileId())

        manager.deleteProfile(evening.id)
        assertEquals(AllowedAppsManager.DEFAULT_PROFILE_ID, manager.selectedProfileId())
        assertEquals(1, manager.profiles().size)
    }

    @Test
    fun theDefaultProfileCannotBeDeletedOrRenamed() {
        manager.deleteProfile(AllowedAppsManager.DEFAULT_PROFILE_ID)
        manager.renameProfile(AllowedAppsManager.DEFAULT_PROFILE_ID, "Altro")
        assertEquals(1, manager.profiles().size)
        assertEquals(context.getString(R.string.allowed_profile_default_name), manager.profiles()[0].name)
    }

    @Test
    fun anUnknownProfileReadsAsTheDefault() {
        manager.setPackages(AllowedAppsManager.DEFAULT_PROFILE_ID, setOf("com.maps"))
        assertEquals(setOf("com.maps"), manager.packagesFor(42))
    }
}
