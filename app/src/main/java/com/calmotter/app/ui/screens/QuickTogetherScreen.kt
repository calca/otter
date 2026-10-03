package com.calmotter.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
 */
@Composable
fun QuickTogetherReaderScreen(
    durationMinutes: Int,
    activityId: Int,
    message: String?,
    onAnotherActivity: () -> Unit,
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
            OtterRingIllustration(dashed = true, pulsing = true) { OtterTapMark(markSize = 88.dp) }
            Text(
                text = stringResource(R.string.quick_together_title),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                text = stringResource(R.string.quick_together_subtitle, durationPillLabel(durationMinutes)),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (message != null) {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            TogetherActivityCard(activityId = activityId, onAnother = onAnotherActivity)
        }
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
