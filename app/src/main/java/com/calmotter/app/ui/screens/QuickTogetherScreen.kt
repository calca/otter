package com.calmotter.app.ui.screens

import com.calmotter.app.ui.theme.muted
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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

/**
 * "Avvicina i telefoni" (specs/nfc-quick-together/), lato di chi propone:
 * il telefono è in ascolto finché questa pagina è a schermo. Una pagina e
 * non un pannello dal basso, per coerenza con il resto di Tempo insieme
 * (freccia in alto, contenuto centrato). Nessun bottone: l'azione è il
 * tocco fra i telefoni.
 *
 * Tre blocchi distinti: il gesto (stagno, titolo e una riga su cosa serve
 * all'altro telefono, che di solito ha l'app chiusa), la proposta che
 * partirà col tocco (durata e attività, in una card), e in fondo, sempre
 * nello stesso punto, il link agli altri modi di Tempo insieme (QR,
 * codice, Bluetooth): dalla Home, con l'NFC, si arriva solo da qui.
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
    CalmScreenColumn(contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.Top) {
        Row(modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        CenteredInRemainingSpace(modifier = Modifier.padding(horizontal = 8.dp)) {
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
                text = stringResource(R.string.quick_together_hint),
                color = MaterialTheme.colorScheme.muted(0.75f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
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
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                ) {
                    Text(
                        text = stringResource(R.string.quick_together_subtitle, durationPillLabel(durationMinutes)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    TogetherActivityCard(activityId = activityId, onAnother = onAnotherActivity)
                }
            }
        }
        CalmLinkRow(
            text = stringResource(R.string.quick_together_other_ways),
            onClick = onOtherWays,
            modifier = Modifier.padding(top = 16.dp),
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
