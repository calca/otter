package com.calmotter.app.ui.screens

import com.calmotter.app.ui.theme.muted
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calmotter.app.R
import com.calmotter.app.durationPillLabel
import com.calmotter.app.ui.mascot.OtterTapMark
import com.calmotter.app.ui.mascot.TogetherMark

/**
 * "Avvicina i telefoni" (specs/nfc-quick-together/), lato di chi propone:
 * il telefono è in ascolto finché questa pagina è a schermo. Una pagina e
 * non un pannello dal basso, per coerenza con il resto di Tempo insieme
 * (freccia in alto, contenuto centrato). Nessun bottone: l'azione è il
 * tocco fra i telefoni.
 *
 * Un gesto e una proposta, nient'altro: lo stagno, il titolo e una riga
 * con la durata e cosa serve all'altro telefono (che di solito ha l'app
 * chiusa), poi l'attività che partirà col tocco, in corsivo, con "↻ Un'altra
 * idea". In fondo, sempre nello stesso punto, gli altri modi di Tempo
 * insieme (QR, codice, Bluetooth, bottone tinto): dalla Home, con l'NFC,
 * si arriva solo da qui. Fino al 2026-10-16 la proposta stava in una card
 * con sottotitolo ("Una pausa insieme di 1 h") ed etichetta ("COSA
 * FACCIAMO?"): tre livelli di testo in un riquadro, pagina affollata
 * (segnalato).
 */
@Composable
fun QuickTogetherReaderScreen(
    durationMinutes: Int,
    activityId: Int,
    message: String?,
    onAnotherActivity: () -> Unit,
    onOtherWays: () -> Unit,
    onBack: () -> Unit,
) {
    CalmScreenColumn(contentPadding = PaddingValues(horizontal = PageMargin, vertical = 16.dp), verticalArrangement = Arrangement.Top) {
        Row(modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        CenteredInRemainingSpace {
            // 200dp come la ricerca di "Unisciti": è la stessa attesa.
            OtterRingIllustration(dashed = true, pulsing = true, size = 200.dp) { OtterTapMark(markSize = 96.dp) }
            Text(
                text = stringResource(R.string.quick_together_title),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 20.dp),
            )
            Text(
                text = stringResource(R.string.quick_together_hint, durationPillLabel(durationMinutes)),
                color = MaterialTheme.colorScheme.muted(0.75f),
                textAlign = TextAlign.Center,
                // Prosa centrata su più righe: più stretta, vedi [CenteredTextInset].
                modifier = Modifier.padding(start = CenteredTextInset, end = CenteredTextInset, top = 6.dp),
            )
            if (message != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                ) {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
            // Cosa parte col tocco: arriva identico sull'altro telefono.
            TogetherActivityCard(
                activityId = activityId,
                onAnother = onAnotherActivity,
                showLabel = false,
                modifier = Modifier.padding(top = 28.dp),
            )
        }
        // Un bottone tinto a tutta larghezza, non un link: come link nudo
        // si perdeva (segnalato), ed è l'unica strada dalla Home verso QR,
        // codice e Bluetooth. Il fondo pagina è libero perché qui l'azione
        // principale è il tocco fra i telefoni, non un bottone.
        CalmSecondaryButton(
            text = stringResource(R.string.quick_together_other_ways),
            onClick = onOtherWays,
            leadingIcon = { TogetherMark(markSize = 20.dp) },
            contentPadding = CtaPadding,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .heightIn(min = 48.dp),
        )
    }
}

/** Il conto alla rovescia dopo il tocco, uguale sui due telefoni. */
@Composable
fun QuickTogetherCountdownScreen(
    companion: String,
    durationMinutes: Int,
    activityId: Int,
    startAt: Long,
    onReady: () -> Unit,
    onCancel: () -> Unit,
) {
    GroupPauseCountdownScreen(
        durationMinutes = durationMinutes,
        startAtEpochMillis = startAt,
        onReady = onReady,
        onCancel = onCancel,
        header = { _, _ ->
            Text(
                text = stringResource(R.string.quick_together_with, companion),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            TogetherActivityCard(activityId = activityId)
        },
    )
}
