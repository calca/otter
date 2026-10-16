package com.calmotter.app

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Il launcher "vero" a cui [MainActivity] inoltra il tasto Home fuori dalla
 * pausa. Sbagliarlo crea un ciclo di istanze che si rilanciano a vicenda
 * (vedi il commento di [LauncherManager]): non vanno mai salvati Calm
 * Otter stessa né i fallback di sistema.
 */
@RunWith(RobolectricTestRunner::class)
class LauncherManagerTest {

    private lateinit var context: Context
    private val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Robolectric elenca già la HomeActivity di Calm Otter (dal
        // manifest): si parte da zero e si aggiungono i launcher del test.
        shadowOf(context.packageManager).removeResolveInfosForIntent(homeIntent, context.packageName)
    }

    private fun addHome(pkg: String) {
        val info = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = pkg
                name = "$pkg.Launcher"
            }
        }
        shadowOf(context.packageManager).addResolveInfoForIntent(homeIntent, info)
    }

    @Test
    fun theDefaultLauncherIsRemembered() {
        addHome("com.example.launcher")

        LauncherManager(context).refreshOriginalLauncherPackage()

        assertEquals("com.example.launcher", LauncherManager(context).getOriginalLauncherPackage())
    }

    @Test
    fun calmOtterAndTheSystemFallbackAreNeverRemembered() {
        addHome(context.packageName)
        addHome("com.android.settings")

        LauncherManager(context).refreshOriginalLauncherPackage()

        assertNull(LauncherManager(context).getOriginalLauncherPackage())
    }

    /** Calm Otter è l'app Home in questo momento: il valore buono già noto resta. */
    @Test
    fun aKnownLauncherIsKeptWhileCalmOtterIsHome() {
        addHome("com.example.launcher")
        LauncherManager(context).refreshOriginalLauncherPackage()

        shadowOf(context.packageManager).removeResolveInfosForIntent(homeIntent, "com.example.launcher")
        addHome(context.packageName)
        addHome("com.other.launcher")
        LauncherManager(context).refreshOriginalLauncherPackage()

        assertEquals("com.example.launcher", LauncherManager(context).getOriginalLauncherPackage())
    }

    /** Il primo salvataggio in assoluto può usare l'elenco, saltando sé stessa. */
    @Test
    fun theFirstTimeTheListIsUsedSkippingCalmOtter() {
        addHome(context.packageName)
        addHome("com.android.settings")
        addHome("com.example.launcher")

        LauncherManager(context).refreshOriginalLauncherPackage()

        assertEquals("com.example.launcher", LauncherManager(context).getOriginalLauncherPackage())
    }
}
