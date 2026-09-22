package com.calmotter.app.ui.screens

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TODO.md "2.2": "ogni schermata di primo livello applica
 * `Modifier.safeDrawingPadding()` sulla propria radice" era finora
 * imposto solo da un paragrafo in CLAUDE.md e dal ricordarselo — su
 * targetSdk 37 l'edge-to-edge è obbligatorio su Android 15+, quindi
 * dimenticarlo vuol dire contenuto disegnato sotto la barra di stato/la
 * tacca/la barra di navigazione su hardware vero (invisibile su qualunque
 * emulatore Android ≤14, per questo passa facilmente inosservato).
 *
 * Non un test Compose/Robolectric: un controllo statico sul sorgente. Le
 * alternative — comporre ogni schermata reale con `createComposeRule` —
 * trascinerebbero dentro Room, il Keystore e i permessi di sistema per
 * schermate che non ne hanno bisogno per questa singola verifica (vedi lo
 * stesso ragionamento in `OtterAnchoredScreenTest`, che per lo stesso
 * motivo testa il componente condiviso invece delle schermate vere), e
 * `safeDrawingPadding()` non lascia comunque una traccia nell'albero di
 * semantica che un test di quel tipo potrebbe interrogare.
 *
 * [screens] è l'elenco delle vere schermate di primo livello — quelle
 * passate a `setContent {}` da un'Activity, non un composable interno —
 * ricavato leggendo ogni `setContent {}` dell'app (vedi
 * `*Activity.kt`). È l'unico punto da aggiornare a mano quando ne arriva
 * una nuova: il test la vuole elencata esplicitamente piuttosto che
 * provare a scoprirla da solo, per restare leggibile come lista invece
 * che come euristica.
 */
class TopLevelScreenSafeDrawingPaddingTest {

    private data class TopLevelScreen(
        val composableName: String,
        /**
         * File in cui cercare `safeDrawingPadding()` — di norma il file
         * stesso della schermata, ma per un router che non disegna alcuna
         * radice propria (vedi i commenti sui singoli casi) sono invece i
         * file a cui delega per intero, verificati a mano una volta qui.
         */
        val verifyIn: List<String>,
    )

    private val screens = listOf(
        TopLevelScreen("AllowedAppsScreen", listOf("AllowedAppsScreen.kt")),
        // Nessuna chiamata propria a safeDrawingPadding(): delega interamente
        // a OtterAnchoredScreen (vedi il suo corpo, e MainActivity.kt per il
        // motivo — Home e BlockScreen condividono l'otter come elemento
        // animato in un unico setContent{}).
        TopLevelScreen("BlockScreen", listOf("OtterAnchoredScreen.kt")),
        TopLevelScreen("ChangePasswordScreen", listOf("ChangePasswordScreen.kt")),
        // Puro router (`when` su HostFlowStep): nessuna radice propria,
        // delega a GroupPauseBluetoothLobbyHostScreen o, tramite la
        // GroupPauseQrShareScreen privata nello stesso file, a
        // GroupPauseCountdownScreen — entrambe verificate qui.
        TopLevelScreen(
            "GroupPauseHostScreen",
            listOf("GroupPauseBluetoothLobbyHostScreen.kt", "GroupPauseCountdownScreen.kt"),
        ),
        TopLevelScreen("GroupPauseChooserScreen", listOf("GroupPauseChooserScreen.kt")),
        // Applica anche la propria copia diretta (vedi GroupPauseCodeEntryScreen
        // nello stesso file), oltre a delegare a GroupPauseBluetoothLobbyJoinScreen
        // e (tramite lo stesso router) a GroupPauseCountdownScreen.
        TopLevelScreen("GroupPauseJoinScreen", listOf("GroupPauseJoinScreen.kt")),
        TopLevelScreen("HistoryScreen", listOf("HistoryScreen.kt")),
        TopLevelScreen("OnboardingScreen", listOf("OnboardingScreen.kt")),
        // Stesso caso di BlockScreen qui sopra, stesso motivo.
        TopLevelScreen("MainScreen", listOf("OtterAnchoredScreen.kt")),
        TopLevelScreen("SettingsScreen", listOf("SettingsScreen.kt")),
    )

    private val screensDir = File("src/main/java/com/calmotter/app/ui/screens")

    private fun sourceOf(fileName: String): String {
        val file = File(screensDir, fileName)
        assertTrue(
            "File atteso non trovato: ${file.path} (working directory sbagliata per " +
                "questo test? Deve essere la cartella `app/`)",
            file.exists(),
        )
        return file.readText()
    }

    @Test
    fun everyTopLevelScreenIsCoveredBySafeDrawingPaddingSomewhereInItsChain() {
        for (screen in screens) {
            val covered = screen.verifyIn.any { sourceOf(it).contains("safeDrawingPadding") }
            assertTrue(
                "${screen.composableName} non risulta coperta da safeDrawingPadding() in " +
                    "nessuno dei file verificati (${screen.verifyIn}) — su targetSdk 37 il " +
                    "contenuto finisce sotto la barra di stato/la tacca su Android 15+.",
                covered,
            )
        }
    }
}
