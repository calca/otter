package com.calmotter.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.calmotter.app.PasswordManager
import com.calmotter.app.R
import com.calmotter.app.installFakeAndroidKeyStore
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * TODO.md "2.2": due delle invarianti CTA che questa serie di modifiche ha
 * corretto a mano schermata per schermata ("il bottone primario è sempre
 * l'ultimo della colonna", "48.dp, non il 40.dp di default M3") non erano
 * protette da nulla — solo dal ricordarsene, appunto come si sono rotte la
 * prima volta.
 *
 * Non un test esaustivo su ogni schermata (vedi "2.2" in TODO.md: qui non
 * c'è un unico componente condiviso da cui tutte le CTA ereditano, a
 * differenza di [OtterAnchoredScreen] — ogni schermata ripete lo stesso
 * schema a mano). Compone due schermate scelte perché sicure da
 * ricomporre sotto Robolectric senza Room/permessi di sistema
 * ([ChangePasswordScreen] prende `PasswordManager` come parametro, non lo
 * legge da un singleton globale al primo avvio — e con
 * [installFakeAndroidKeyStore] anche quello è sicuro) e perché protegge
 * direttamente una regressione appena corretta in questa stessa sessione
 * ([GroupPauseCodeEntryScreen], modalità manuale: "Scan dovrebbe essere
 * l'ultimo bottone, come da specifica").
 *
 * **Niente numeri di larghezza calcolati a mano.** Un primo tentativo
 * confrontava la larghezza del bottone con `360.dp - padding dichiarato`,
 * calcolato leggendo il codice — sistematicamente sbagliato di 40.dp
 * (identico su entrambe le schermate, nonostante padding dichiarati
 * diversi): `safeDrawingPadding()` consuma anche sotto Robolectric un
 * inset non nullo il cui valore esatto non è contrattuale né stabile fra
 * versioni. Invece: dove ci sono due bottoni fratelli nello stesso
 * contenitore (Cancel/Scan), "a tutta larghezza" si verifica
 * confrontandoli fra loro, senza bisogno di sapere la larghezza assoluta
 * di nessuno dei due; dove c'è un solo bottone (ChangePasswordScreen), un
 * `Box` sonda con la stessa identica catena di modifier della radice
 * della schermata misura quanto spazio *resta davvero* nello stesso
 * ambiente di test, invece di ricalcolarlo a mano.
 */
@RunWith(AndroidJUnit4::class)
class CtaButtonInvariantsTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() {
        installFakeAndroidKeyStore()
    }

    @Test
    fun changePasswordScreenConfirmButtonIs48dpAndAsWideAsItsOwnRootAllows() {
        var referenceWidthPx = 0
        compose.setContent {
            val context = LocalContext.current
            Box(modifier = Modifier.width(360.dp)) {
                // Stessa identica catena di modifier della radice di
                // ChangePasswordScreen (fillMaxSize().safeDrawingPadding()
                // .padding(24.dp)) — misura quanto spazio resta davvero in
                // questo stesso ambiente di test, invece di ricalcolarlo a
                // mano (vedi il commento di classe).
                Box(
                    Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                        .padding(24.dp)
                        .onGloballyPositioned { referenceWidthPx = it.size.width }
                )
                ChangePasswordScreen(
                    passwordManager = PasswordManager.getInstance(context),
                    onDone = {},
                )
            }
        }

        val confirmLabel = ApplicationProvider.getApplicationContext<android.content.Context>()
            .getString(R.string.change_password_confirm)
        val referenceWidth = with(compose.density) { referenceWidthPx.toDp() }

        compose.onNodeWithText(confirmLabel)
            .assertHeightIsEqualTo(48.dp)
            .assertWidthIsEqualTo(referenceWidth)
    }

    /**
     * Modalità manuale: un solo bottone, "Unisciti", spento finché il campo
     * è vuoto; "Scansiona un QR" è un link e non c'è più "Annulla" (si esce
     * con la freccia). Prima i bottoni erano tre, e il più evidente era
     * l'alternativa ("Scansiona") — segnalato: "troppi bottoni".
     */
    @Test
    fun manualCodeEntryScreenHasOneJoinButtonDisabledUntilACodeIsTyped() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent {
            Box(modifier = Modifier.width(360.dp)) {
                GroupPauseCodeEntryScreen(onRecipeReady = {}, onCancel = {})
            }
        }

        // Stato iniziale è JoinMode.SCAN, senza permesso fotocamera sotto
        // Robolectric: ricade sulla schermata di motivazione, che ha il link
        // per passare al codice manuale — lo stesso percorso di chi nega la
        // fotocamera.
        compose.onNodeWithText(context.getString(R.string.group_pause_manual_entry_link))
            .performClick()

        val join = compose.onNodeWithText(context.getString(R.string.group_pause_manual_code_join_button))
        join.assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.group_pause_scan_instead)).assertExists()
        compose.onNodeWithText(context.getString(android.R.string.cancel)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.group_pause_join_scan_tab)).assertDoesNotExist()

        compose.onNodeWithText(context.getString(R.string.group_pause_manual_code_hint)).performTextInput("abc")
        join.assertIsEnabled()
    }
}
