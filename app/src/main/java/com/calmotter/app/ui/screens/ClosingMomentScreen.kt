package com.calmotter.app.ui.screens

import com.calmotter.app.durationWords
import androidx.compose.ui.platform.LocalContext
import com.calmotter.app.ui.theme.muted
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calmotter.app.Mood
import androidx.compose.animation.AnimatedVisibility
import com.calmotter.app.R
import com.calmotter.app.ui.mascot.OtterZenMark

/** Lunghezza massima della nota: sta su una riga della Cronologia senza troncarsi. */
const val CLOSING_NOTE_MAX = 30

/**
 * Il momento di chiusura (specs/closing-moment/): dopo una pausa arrivata
 * in fondo, una domanda gentile — com'è andata? — con tre risposte a parole
 * e una nota facoltativa. Mai dopo un'uscita anticipata, mai serie, obiettivi
 * o confronti: è un riconoscimento del tempo passato lontano dal telefono,
 * non un punteggio. "Salta" chiude e non richiede più.
 *
 * Leggera di proposito (segnalato: "troppo ricca"): all'inizio solo i tre
 * umori; il campo della nota compare dopo averne scelto uno. In fondo un
 * solo bottone: "Salta" (tinto) finché non si è risposto, "Fatto" (pieno)
 * dopo. Prima erano sempre visibili la nota, "Salta" e un "Fatto" spento.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClosingMomentScreen(
    effectiveMinutes: Int,
    onDone: (mood: Int?, note: String) -> Unit,
    onSkip: () -> Unit,
    // Condividere la pausa appena finita (vedi Share): un link discreto
    // sotto la nota, per scelta di chi l'ha fatta. Vuoto negli screenshot.
    onShare: () -> Unit = {},
) {
    var mood by rememberSaveable { mutableStateOf<Int?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    val answered = mood != null || note.isNotBlank()

    // Domanda e risposte centrate nello spazio sopra; "Salta" e "Fatto" fissi
    // in fondo (regola della CTA in CLAUDE.md) invece che a metà pagina con
    // mezza pagina vuota sotto — come nella lobby di Tempo insieme.
    CalmScreenColumn(contentPadding = PaddingValues(horizontal = PageMargin, vertical = 32.dp), verticalArrangement = Arrangement.Top) {
        CenteredInRemainingSpace {
            OtterZenMark(markSize = 96.dp)
            Text(
                text = stringResource(R.string.closing_title, durationWords(LocalContext.current, effectiveMinutes.coerceAtLeast(1))),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                text = stringResource(R.string.closing_question),
                color = MaterialTheme.colorScheme.muted(0.75f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, bottom = 20.dp),
            )
            // FlowRow e non Row: con i caratteri grandi "Faticosa" finiva
            // tagliata in "Faticos" (trovato nel giro a caratteri al 130%).
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    Mood.CALM to R.string.mood_calm,
                    Mood.ORDINARY to R.string.mood_ordinary,
                    Mood.HARD to R.string.mood_hard,
                ).forEach { (value, label) ->
                    CalmPill(
                        label = stringResource(label),
                        selected = mood == value,
                        // Un secondo tocco sulla stessa toglie la scelta.
                        onClick = { mood = if (mood == value) null else value },
                    )
                }
            }
            // La nota solo dopo un umore (o se ha già del testo, per non
            // farla sparire togliendo la scelta).
            AnimatedVisibility(visible = mood != null || note.isNotEmpty()) {
                CalmTextField(
                    value = note,
                    onValueChange = { note = it.replace('\n', ' ').take(CLOSING_NOTE_MAX) },
                    label = stringResource(R.string.closing_note_hint),
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                )
            }
            CalmLinkRow(
                text = stringResource(R.string.closing_share),
                onClick = onShare,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        // Un solo bottone, che cambia con la risposta.
        val buttonModifier = Modifier.fillMaxWidth().padding(top = 20.dp).heightIn(min = 48.dp)
        if (answered) {
            Button(
                contentPadding = CtaPadding,
                onClick = { onDone(mood, note.trim()) },
                modifier = buttonModifier,
            ) {
                Text(stringResource(R.string.closing_done))
            }
        } else {
            CalmSecondaryButton(
                text = stringResource(R.string.closing_skip),
                onClick = onSkip,
                contentPadding = CtaPadding,
                modifier = buttonModifier,
            )
        }
    }
}
