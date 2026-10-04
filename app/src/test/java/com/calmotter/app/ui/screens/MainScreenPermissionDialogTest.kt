package com.calmotter.app.ui.screens

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.calmotter.app.R
import com.calmotter.app.SessionHistoryManager
import com.calmotter.app.SessionManager
import com.calmotter.app.installFakeAndroidKeyStore
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Senza permessi, il tocco sull'otter (e "Con un tocco", "Tempo insieme", il
 * widget) apre la spiegazione dei permessi. Il blocco che la mostra in
 * MainScreen era sparito per errore durante un refactor: i tocchi non
 * facevano più nulla su un'installazione pulita, e nessun test se n'era
 * accorto (le build di debug saltano il controllo dei permessi).
 */
@RunWith(AndroidJUnit4::class)
class MainScreenPermissionDialogTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() = installFakeAndroidKeyStore()

    @Test
    fun thePermissionDialogIsShownWhenAsked() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MainScreen(
                resumeSignal = 0,
                sessionManager = SessionManager.getInstance(context),
                sessionHistoryManager = SessionHistoryManager.getInstance(context),
                isAccessibilityServiceEnabled = { false },
                isDndAccessGranted = { false },
                onGrantAccessibility = {},
                onGrantDnd = {},
                onHistory = {},
                onSettings = {},
                selectedDurationMinutes = 60,
                onSelectDuration = {},
                showPermissionDialog = true,
                onDismissPermissionDialog = {},
            )
        }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText(context.getString(R.string.home_permission_dialog_title)).assertExists()
    }
}
