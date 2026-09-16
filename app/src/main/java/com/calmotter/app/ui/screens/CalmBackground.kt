package com.calmotter.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Brush

/**
 * Velo tenue di "primary" sullo sfondo (più percepibile in alto, sfuma verso
 * il neutro scendendo). Dà un accenno di identità di marca (segue la
 * palette Sage/Lavender/Terracotta scelta) senza introdurre un blocco di
 * colore saturo: coerente con la scelta di tenere il resto della UI su
 * tinte tenui (vedi specs/home-and-settings "Living Pond redesign").
 *
 * Usato da Home, Onboarding, BlockScreen, e le schermate di Pausa di gruppo
 * (GroupPauseHostScreen/GroupPauseJoinScreen/GroupPauseCountdownScreen e,
 * dalla Fase 2, GroupPauseBluetoothLobbyHostScreen/
 * GroupPauseBluetoothLobbyJoinScreen — vedi specs/group-pause/) — stessa
 * categoria "di rito"/apertura di una pausa, non amministrazione.
 * Deliberatamente NON usato su Settings/Cronologia:
 * quelle restano schermate di navigazione/amministrazione, non "schermate
 * di apertura" o di sessione attiva.
 */
@Composable
fun Modifier.calmBackground(): Modifier {
    val primary = MaterialTheme.colorScheme.primary
    return this.background(
        brush = Brush.verticalGradient(
            colors = listOf(primary.copy(alpha = 0.09f), primary.copy(alpha = 0.02f))
        )
    )
}

/**
 * Contenitore standard per una schermata a pagina intera dell'app.
 *
 * Esiste perché le schermate erano `Column(fillMaxSize)` senza scorrimento:
 * quando il contenuto non ci stava — dimensione carattere di sistema
 * ingrandita, schermo corto — veniva semplicemente tagliato via, **in
 * silenzio**. Sulla Home a `font_scale 1.5` sparivano il bottone "Tempo
 * insieme" e la scorciatoia "Di nuovo con…", senza comparire nemmeno
 * nell'albero delle semantiche: nessun segno che mancasse qualcosa.
 *
 * `heightIn(min = maxHeight)` insieme allo scorrimento è ciò che permette di
 * non cambiare l'aspetto nel caso normale: finché il contenuto ci sta, la
 * colonna è alta quanto il viewport e [verticalArrangement] lo dispone come
 * prima (di solito centrato); quando non ci sta, cresce e scorre.
 *
 * **`maxHeight` va misurato dentro l'area sicura, non fuori.** Il
 * [BoxWithConstraints] sta perciò *dentro* `safeDrawingPadding()` e non
 * attorno: misurandolo fuori, `maxHeight` includeva barra di stato e barra
 * di navigazione, cioè spazio che la colonna non ha, e
 * `heightIn(min = maxHeight)` la rendeva più alta del suo viewport esattamente
 * di quegli inset. Conseguenze osservate (bug reale, targetSdk 37 impone
 * l'edge-to-edge quindi gli inset non sono mai zero): ogni schermata restava
 * scorrevole di quei ~70-100dp anche con contenuto che ci stava
 * comodamente, e [verticalArrangement] aveva quello spazio in più da
 * distribuire — sulla Home finiva tutto nell'unico spazio fra "Tempo insieme"
 * e la card della cronologia, e lo scorrimento residuo spostava l'otter dal
 * suo ancoraggio fisso, rimettendo in moto la transizione condivisa verso
 * BlockScreen (vedi [OtterAnchoredScreen]).
 *
 * `calmBackground()` resta invece sul Box esterno a schermo pieno: il velo
 * di colore deve continuare a passare sotto le barre di sistema.
 *
 * Nota per chi la usa: dentro uno scorrimento verticale **non si può usare
 * `Modifier.weight`** — l'altezza disponibile è infinita. Per distribuire
 * spazio, [verticalArrangement] (`Center`, `SpaceBetween`) fa quel lavoro.
 */
@Composable
fun CalmScreenColumn(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    verticalArrangement: Arrangement.Vertical = Arrangement.Center,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize().calmBackground()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(contentPadding),
                horizontalAlignment = horizontalAlignment,
                verticalArrangement = verticalArrangement,
                content = content,
            )
        }
    }
}
