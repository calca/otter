package com.calmotter.app.ui.screens

import android.content.Context
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.calmotter.app.R
import com.calmotter.app.ScheduledPausesActivity
import com.calmotter.app.installFakeAndroidKeyStore
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Quello che l'utente ha scritto o scelto sopravvive a una ricreazione
 * dell'Activity: tema scuro, testo più grande, lingua, e su tablet la
 * rotazione (da targetSdk 36 il blocco in verticale lì è ignorato). Prima
 * era tutto `remember` e si perdeva.
 */
@RunWith(AndroidJUnit4::class)
class StateRestorationTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() = installFakeAndroidKeyStore()

    @Test
    fun theClosingNoteAndMoodSurviveRecreation() {
        val tester = StateRestorationTester(compose)
        tester.setContent { ClosingMomentScreen(effectiveMinutes = 60, onDone = { _, _ -> }, onSkip = {}) }

        compose.onNodeWithText(context.getString(R.string.mood_calm)).performClick()
        compose.onNodeWithText(context.getString(R.string.closing_note_hint)).performTextInput("letto al parco")

        tester.emulateSavedInstanceStateRestore()

        compose.onNodeWithText("letto al parco").assertExists()
        compose.onNodeWithText(context.getString(R.string.mood_calm)).assertIsSelected()
    }
}

/** L'editor di una pausa programmata resta aperto, non torna all'elenco. */
@RunWith(AndroidJUnit4::class)
class ScheduledPausesRecreationTest {

    // Regola vuota e Activity lanciata a mano: il finto Keystore va
    // installato prima che l'Activity parta (PasswordManager in onCreate).
    @get:Rule
    val compose = createEmptyComposeRule()

    @Before
    fun setUp() = installFakeAndroidKeyStore()

    @Test
    fun theOpenEditorSurvivesRecreation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ActivityScenario.launch(ScheduledPausesActivity::class.java).use { scenario ->
            compose.onNodeWithText(context.getString(R.string.schedule_add)).performClick()
            compose.onNodeWithText(context.getString(R.string.save)).assertExists()

            scenario.recreate()

            compose.onNodeWithText(context.getString(R.string.save)).assertExists()
        }
    }
}
