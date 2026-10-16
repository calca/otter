package com.calmotter.app.ui.screens

import android.content.Context
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.calmotter.app.AppTheme
import com.calmotter.app.PasswordManager
import com.calmotter.app.PhraseManager
import com.calmotter.app.R
import com.calmotter.app.installFakeAndroidKeyStore
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Le etichette delle righe di Impostazioni partono tutte dallo stesso punto,
 * qualunque sia il tipo di riga. È già successo due volte che la card dei
 * permessi avesse 12dp in più delle altre (prima l'icona, poi il testo).
 */
@RunWith(AndroidJUnit4::class)
class SettingsAlignmentTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() = installFakeAndroidKeyStore()

    @Test
    fun permissionLabelsLineUpWithTheOtherRows() {
        compose.setContent {
            SettingsScreen(
                currentTheme = AppTheme.SAGE,
                partnerName = null,
                passwordManager = PasswordManager.getInstance(context),
                phraseManager = PhraseManager.getInstance(context),
                resumeSignal = 0,
                isAccessibilityServiceEnabled = { false },
                isDndAccessGranted = { true },
                onGrantAccessibility = {},
                onGrantDnd = {},
                onPickTheme = {},
                onManageAppsVerified = {},
                onChangePassword = {},
                onOpenSource = {},
            )
        }

        fun labelLeft(res: Int) = compose.onNodeWithText(context.getString(res), useUnmergedTree = true)
            .performScrollTo()
            .getUnclippedBoundsInRoot().left

        val other = labelLeft(R.string.change_password)
        assertEquals(other, labelLeft(R.string.permission_row_accessibility))
        assertEquals(other, labelLeft(R.string.permission_row_dnd))
    }
}
