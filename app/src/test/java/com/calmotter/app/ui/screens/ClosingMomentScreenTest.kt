package com.calmotter.app.ui.screens

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.calmotter.app.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Il momento di chiusura resta leggero: all'inizio solo gli umori e
 * "Salta"; la nota e "Fatto" arrivano dopo una risposta (segnalato: la
 * pagina era "troppo ricca").
 */
@RunWith(AndroidJUnit4::class)
class ClosingMomentScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun theNoteAndDoneAppearOnlyAfterAnAnswer() {
        compose.setContent { ClosingMomentScreen(effectiveMinutes = 60, onDone = { _, _ -> }, onSkip = {}) }

        compose.onNodeWithText(context.getString(R.string.closing_note_hint)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.closing_skip)).assertExists()
        compose.onNodeWithText(context.getString(R.string.closing_done)).assertDoesNotExist()

        compose.onNodeWithText(context.getString(R.string.mood_calm)).performClick()

        compose.onNodeWithText(context.getString(R.string.closing_note_hint)).assertExists()
        compose.onNodeWithText(context.getString(R.string.closing_done)).assertExists()
        compose.onNodeWithText(context.getString(R.string.closing_skip)).assertDoesNotExist()

        // Togliendo la scelta senza aver scritto nulla si torna a "Salta".
        compose.onNodeWithText(context.getString(R.string.mood_calm)).performClick()
        compose.onNodeWithText(context.getString(R.string.closing_skip)).assertExists()
    }
}
