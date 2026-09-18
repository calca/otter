package com.calmotter.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions

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

/**
 * Card tinta arrotondata per raggruppare il contenuto di una schermata —
 * stesso linguaggio visivo di `WeekOverviewCard` in HistoryScreen.kt
 * (`Surface` con `RoundedCornerShape(20.dp)` e `primary` al 6% di opacità),
 * qui reso condiviso perché richiesto per le schermate di Tempo Insieme
 * (lobby host/join, pagina QR, conto alla rovescia) — non un'ennesima
 * duplicazione privata per file come altrove in questo codebase, dato che
 * qui l'obiettivo esplicito è la coerenza visiva fra più schermate, non uno
 * stile solo simile.
 */
@Composable
fun CalmCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

/**
 * Azione secondaria "invitante": un `FilledTonalButton` tinta di palette,
 * per le scorciatoie che prima erano `TextButton` nudi e si leggevano come
 * testo invece che come qualcosa da toccare (segnalato: "le CTA secondarie
 * sembrano poco invitanti").
 *
 * Stessa ricetta del bottone dell'obiettivo settimanale in
 * `HistoryScreen.kt`, qui condivisa invece che ricopiata: i colori sono
 * **espliciti** perché `ButtonDefaults.filledTonalButtonColors()` userebbe
 * `secondaryContainer`/`onSecondaryContainer`, ruoli che
 * `CalmOtterTheme.kt` non personalizza per palette e che cadrebbero sul
 * viola di base di Material3 qualunque tema sia scelto (stessa trappola di
 * `surfaceVariant` documentata in CLAUDE.md). Con cinque punti d'uso, quel
 * dettaglio è meglio che viva in un posto solo.
 *
 * Resta comunque *sotto* all'azione primaria della schermata, che è un
 * `Button` pieno: il contenitore qui è al 14% di `primary`, non pieno.
 */
@Composable
fun CalmSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
            contentColor = MaterialTheme.colorScheme.primary,
        ),
    ) {
        if (leadingIcon != null) {
            leadingIcon()
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text = text)
    }
}

/**
 * Il campo di testo dell'app, uno solo per tutti i punti in cui si scrive:
 * password, nome della persona di fiducia, codice invito, ricerca fra le
 * app consentite.
 *
 * Prima ogni punto usava `OutlinedTextField` con i valori di serie di
 * Material 3 — contorno sottile, angoli appena smussati, etichetta che
 * galleggia dentro la tacca del bordo. Il design del redesign li vuole
 * diversi, ed è stato segnalato: **contenitore pieno, angoli da 16, e un
 * segnaposto al posto dell'etichetta flottante** (nel mockup la `<label>` è
 * marcata `sr-only`, cioè esiste solo per i lettori di schermo).
 *
 * Le tinte non sono gli hex del mockup ma ruoli di tema, come per ogni
 * altro pezzo condiviso: `#f3f4f0` e `#dce3dc` resterebbero grigio-verdi in
 * tutte e otto le combinazioni di palette e tema. Il contenitore è
 * `primary` al 6% — appena più tenue del 8% delle pastiglie non
 * selezionate, perché un campo vuoto non deve competere con un bottone — e
 * il bordo passa da `primary` al 18% a `primary` pieno quando il campo ha
 * il fuoco, che è il modo del mockup di dire "stai scrivendo qui".
 *
 * **L'etichetta resta, ma per chi non vede.** Un segnaposto sparisce appena
 * si digita: chi usa TalkBack si ritroverebbe un campo muto a metà
 * compilazione. Qui [label] fa da segnaposto *e* da `contentDescription`
 * del campo, quindi il lettore di schermo continua ad annunciarlo anche a
 * campo pieno.
 */
@Composable
fun CalmTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        // Niente `label`: al suo posto il segnaposto, come nel mockup.
        placeholder = { Text(label) },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        shape = RoundedCornerShape(16.dp),
        // Colori passati a uno a uno: i valori di serie pescano da
        // `surfaceVariant`/`onSurfaceVariant`, ruoli che CalmOtterTheme non
        // personalizza e che comparirebbero quindi nel viola di serie di
        // Material 3, indipendentemente dalla palette scelta (la stessa
        // trappola documentata in CLAUDE.md).
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = primary.copy(alpha = 0.06f),
            unfocusedContainerColor = primary.copy(alpha = 0.06f),
            disabledContainerColor = primary.copy(alpha = 0.04f),
            focusedBorderColor = primary,
            unfocusedBorderColor = primary.copy(alpha = 0.18f),
            disabledBorderColor = primary.copy(alpha = 0.10f),
            focusedTextColor = onSurface,
            unfocusedTextColor = onSurface,
            focusedPlaceholderColor = onSurface.copy(alpha = 0.45f),
            unfocusedPlaceholderColor = onSurface.copy(alpha = 0.45f),
            focusedLeadingIconColor = primary,
            unfocusedLeadingIconColor = onSurface.copy(alpha = 0.55f),
            focusedTrailingIconColor = primary,
            unfocusedTrailingIconColor = onSurface.copy(alpha = 0.55f),
            cursorColor = primary,
        ),
        modifier = modifier.semantics { contentDescription = label },
    )
}
