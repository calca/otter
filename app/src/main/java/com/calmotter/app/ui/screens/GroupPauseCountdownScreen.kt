package com.calmotter.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calmotter.app.R
import kotlinx.coroutines.delay

/**
 * Ultimo passo comune a chi crea e chi si unisce a una pausa di gruppo
 * (vedi specs/group-pause/design.md, "handshake poi autonomia"): una volta
 * che questo dispositivo conosce durata+orario di inizio concordati, non
 * serve più nessuna connessione — questo composable si limita a contare
 * alla rovescia in locale (`LaunchedEffect`/`delay`, stesso idioma del
 * conto alla rovescia di lockout in ChangePasswordScreen.kt) e, allo scadere,
 * chiama [onReady] una sola volta (avvia la sessione — SessionManager
 * resta del tutto invariato, vedi startSession(isGroupSession = true)).
 *
 * [header] ospita contenuto specifico del chiamante sopra al conto alla
 * rovescia — il QR/codice da condividere per GroupPauseHostScreen, nulla
 * per GroupPauseJoinScreen (che l'ha già usato per arrivare qui).
 *
 * Limite noto e documentato (specs/group-pause/requirements.md): se il
 * processo viene ucciso mentre questa schermata è in background durante
 * l'attesa, il conto alla rovescia si ferma — nessun AlarmManager di
 * riserva, scelta deliberata per restare nel perimetro di questa fase
 * (finestra di pochi minuti, rischio accettato).
 */
@Composable
fun GroupPauseCountdownScreen(
    durationMinutes: Int,
    startAtEpochMillis: Long,
    onReady: () -> Unit,
    onCancel: () -> Unit,
    header: @Composable () -> Unit = {},
) {
    var remainingMillis by remember {
        mutableLongStateOf((startAtEpochMillis - System.currentTimeMillis()).coerceAtLeast(0L))
    }

    LaunchedEffect(startAtEpochMillis) {
        while (true) {
            val remaining = startAtEpochMillis - System.currentTimeMillis()
            remainingMillis = remaining.coerceAtLeast(0L)
            if (remaining <= 0L) {
                onReady()
                break
            }
            delay(200L)
        }
    }

    val totalSeconds = (remainingMillis / 1000L).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60

    Column(
        modifier = Modifier
            .fillMaxSize()
            .calmBackground()
            .safeDrawingPadding()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        header()

        Text(
            text = stringResource(R.string.group_pause_countdown_label, minutes, seconds),
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 8.dp)
        )
        Text(
            text = stringResource(R.string.group_pause_duration_reminder, durationMinutes),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        )

        OutlinedButton(onClick = onCancel) {
            Text(stringResource(android.R.string.cancel))
        }
    }
}
