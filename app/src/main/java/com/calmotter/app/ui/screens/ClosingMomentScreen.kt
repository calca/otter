package com.calmotter.app.ui.screens

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.calmotter.app.R
import com.calmotter.app.durationPillLabel
import com.calmotter.app.ui.mascot.OtterZenMark

/** Lunghezza massima della nota: sta su una riga della Cronologia senza troncarsi. */
const val CLOSING_NOTE_MAX = 30

/**
 * Il momento di chiusura (specs/closing-moment/): dopo una pausa arrivata
 * in fondo, una domanda gentile — com'è andata? — con tre risposte a parole
 * e una nota facoltativa. Mai dopo un'uscita anticipata, mai serie, obiettivi
 * o confronti: è un riconoscimento del tempo passato lontano dal telefono,
 * non un punteggio. "Salta" chiude e non richiede più.
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
    var mood by remember { mutableStateOf<Int?>(null) }
    var note by remember { mutableStateOf("") }

    // Domanda e risposte centrate nello spazio sopra; "Salta" e "Fatto" fissi
    // in fondo (regola della CTA in CLAUDE.md) invece che a metà pagina con
    // mezza pagina vuota sotto — come nella lobby di Tempo insieme.
    CalmScreenColumn(contentPadding = PaddingValues(32.dp), verticalArrangement = Arrangement.Top) {
        CenteredInRemainingSpace {
            OtterZenMark(markSize = 96.dp)
            Text(
                text = stringResource(R.string.closing_title, durationPillLabel(effectiveMinutes.coerceAtLeast(1))),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                text = stringResource(R.string.closing_question),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
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
            CalmTextField(
                value = note,
                onValueChange = { note = it.replace('\n', ' ').take(CLOSING_NOTE_MAX) },
                label = stringResource(R.string.closing_note_hint),
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            )
            CalmLinkRow(
                text = stringResource(R.string.closing_share),
                onClick = onShare,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text(stringResource(R.string.closing_skip))
            }
            Button(
                onClick = { onDone(mood, note.trim()) },
                enabled = mood != null || note.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text(stringResource(R.string.closing_done))
            }
        }
    }
}
