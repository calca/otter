package com.calmotter.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.calmotter.app.ui.screens.HomeHeaderHeight
import com.calmotter.app.ui.screens.OtterAnchoredScreen
import com.calmotter.app.ui.screens.OtterSlotTestTag
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * **L'otter deve stare nello stesso punto in Home e in pausa.**
 *
 * È l'unica invariante grafica di questa app che si è rotta da sola più di
 * una volta — due, stando a `OtterAnchoredScreen.kt` — e sempre allo stesso
 * modo: una modifica fatta altrove, in perfetta buona fede (un refactor
 * dello scroll, un `Arrangement` cambiato), e l'otter che scivola all'avvio
 * della pausa. Se ne accorgeva solo qualcuno guardando lo schermo.
 *
 * Le due schermate differiscono in due modi che *potrebbero* spostare
 * l'otter, ed è esattamente da questi due che il test lo difende:
 *
 * 1. la Home ha un'intestazione, la schermata di blocco no;
 * 2. sotto l'otter hanno contenuti di altezza molto diversa (chip e
 *    riepilogo da una parte; conto alla rovescia, frase e azioni
 *    dall'altra).
 *
 * Il test non ricompone `MainScreen` e `BlockScreen` vere: quelle
 * trascinerebbero dentro Room, il Keystore e i permessi di sistema, e un
 * test che fallisce per quei motivi smette presto di essere letto. Verifica
 * invece il contratto di [OtterAnchoredScreen], che è il posto dove
 * l'invariante vive davvero e l'unico da cui entrambe le schermate la
 * ereditano.
 */
@RunWith(AndroidJUnit4::class)
// Viewport da telefono, dichiarato: con lo schermo predefinito di
// Robolectric (molto più corto) scatta il caso limite documentato in
// [OtterAnchoredScreen] — lo spazio sopra l'otter va a zero e
// l'intestazione torna a spostarlo. Fissato qui sotto in un test suo.
@Config(qualifiers = "w411dp-h891dp")
class OtterAnchoredScreenTest {

    @get:Rule
    val compose = createComposeRule()

    /**
     * Una composizione sola per test — `createComposeRule` non ne ammette
     * due — e le due configurazioni ottenute cambiando uno stato: è anche
     * più fedele a ciò che succede davvero, dove la schermata cambia sotto
     * l'otter senza essere ricreata da zero.
     */
    /** Densità della composizione, per confrontare px con dp senza indovinarla. */
    private var densita = 1f

    private fun misura(
        primo: Pair<Dp, Dp>,
        secondo: Pair<Dp, Dp>,
    ): Pair<Rect, Rect> {
        val configurazione = mutableStateOf(primo)
        compose.setContent {
            densita = LocalDensity.current.density
            val (headerHeight, belowHeight) = configurazione.value
            OtterAnchoredScreen(
                headerHeight = headerHeight,
                header = { Spacer(modifier = Modifier.fillMaxWidth().height(headerHeight)) },
                otter = { Box(modifier = Modifier.height(1.dp)) },
                below = { Spacer(modifier = Modifier.fillMaxWidth().height(belowHeight)) },
            )
        }
        val a = compose.onNodeWithTag(OtterSlotTestTag).fetchSemanticsNode().boundsInRoot
        configurazione.value = secondo
        compose.waitForIdle()
        val b = compose.onNodeWithTag(OtterSlotTestTag).fetchSemanticsNode().boundsInRoot
        return a to b
    }

    @Test
    fun `lo slot dell'otter cade nello stesso punto con e senza intestazione`() {
        val (conIntestazione, senzaIntestazione) = misura(
            primo = HomeHeaderHeight to 200.dp,
            secondo = 0.dp to 200.dp,
        )

        assertEquals(
            "L'intestazione della Home non deve spostare l'otter: è la differenza " +
                "numero uno fra le due schermate.",
            conIntestazione.top.toDouble(),
            senzaIntestazione.top.toDouble(),
            0.5,
        )
    }

    @Test
    fun `lo slot dell'otter non si sposta al variare del contenuto sotto`() {
        val (pocoSotto, moltoSotto) = misura(
            primo = 0.dp to 80.dp,
            secondo = 0.dp to 320.dp,
        )

        assertEquals(
            "L'altezza del contenuto sotto l'otter non entra nel calcolo della sua " +
                "posizione, apposta: è la differenza numero due fra le due schermate.",
            pocoSotto.top.toDouble(),
            moltoSotto.top.toDouble(),
            0.5,
        )
    }

    /**
     * Il caso limite, messo per iscritto invece che lasciato come sorpresa.
     *
     * Su uno schermo così corto che lo spazio sopra l'otter andrebbe
     * negativo, [OtterAnchoredScreen] lo azzera e la colonna si impila
     * dall'alto: lì l'intestazione **torna** a spostare l'otter, e le due
     * schermate si disallineano proprio di quella. È scritto nel commento di
     * quel file; questo test lo rende una cosa che si nota se cambia, e
     * spiega anche perché gli altri due dichiarano un viewport da telefono.
     */
    @Test
    @Config(qualifiers = "w411dp-h400dp")
    fun `su uno schermo troppo corto l'intestazione torna a spostare l'otter`() {
        val (conIntestazione, senzaIntestazione) = misura(
            primo = HomeHeaderHeight to 200.dp,
            secondo = 0.dp to 200.dp,
        )

        assertEquals(
            "Degradato: la colonna si impila dall'alto e l'otter scende " +
                "dell'altezza dell'intestazione.",
            (conIntestazione.top - senzaIntestazione.top).toDouble(),
            (HomeHeaderHeight.value * densita).toDouble(),
            2.0,
        )
    }
}
