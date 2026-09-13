package com.calmotter.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calmotter.app.GroupPauseRecipe
import com.calmotter.app.R
import com.calmotter.app.encode
import com.calmotter.app.generateQrCodeBitmap
import kotlin.random.Random

private val DURATION_OPTIONS = listOf(15, 30, 60, 90, 120)
private val DELAY_OPTIONS = listOf(1, 2, 5)

private enum class PairingMode { QR_CODE, BLUETOOTH }

private sealed class HostFlowStep {
    data object Setup : HostFlowStep()
    data class BluetoothLobby(val durationMinutes: Int) : HostFlowStep()
    data class Countdown(val recipe: GroupPauseRecipe, val showShareHeader: Boolean) : HostFlowStep()
}

/**
 * Crea una pausa di gruppo: sceglie modalità di abbinamento, durata (+ tra
 * quanto iniziare, solo per QR/codice) poi mostra o il QR/codice da
 * condividere (QR/codice) o la lobby dal vivo (Bluetooth, Fase 2 — vedi
 * specs/group-pause/design.md), infine il conto alla rovescia condiviso
 * (GroupPauseCountdownScreen). Nessuno stato persistito prima che il conto
 * alla rovescia arrivi a zero: uscire da questa schermata prima (onCancel)
 * non lascia nulla in sospeso.
 */
@Composable
fun GroupPauseHostScreen(onStarted: (durationMinutes: Int) -> Unit, onCancel: () -> Unit) {
    var step by remember { mutableStateOf<HostFlowStep>(HostFlowStep.Setup) }

    when (val current = step) {
        HostFlowStep.Setup -> GroupPauseSetupScreen(
            onCreateQr = { durationMinutes, delayMinutes ->
                step = HostFlowStep.Countdown(
                    recipe = GroupPauseRecipe(
                        durationMinutes = durationMinutes,
                        startAtEpochMillis = System.currentTimeMillis() + delayMinutes * 60_000L,
                        groupTag = Random.nextInt(0, 65536),
                    ),
                    showShareHeader = true,
                )
            },
            onCreateBluetooth = { durationMinutes -> step = HostFlowStep.BluetoothLobby(durationMinutes) },
            onCancel = onCancel,
        )
        is HostFlowStep.BluetoothLobby -> GroupPauseBluetoothLobbyHostScreen(
            durationMinutes = current.durationMinutes,
            onRecipeReady = { recipe -> step = HostFlowStep.Countdown(recipe, showShareHeader = false) },
            onCancel = onCancel,
        )
        is HostFlowStep.Countdown -> GroupPauseCountdownScreen(
            durationMinutes = current.recipe.durationMinutes,
            startAtEpochMillis = current.recipe.startAtEpochMillis,
            onReady = { onStarted(current.recipe.durationMinutes) },
            onCancel = onCancel,
            header = {
                if (current.showShareHeader) GroupPauseShareHeader(code = current.recipe.encode())
            },
        )
    }
}

@Composable
private fun GroupPauseSetupScreen(
    onCreateQr: (durationMinutes: Int, delayMinutes: Int) -> Unit,
    onCreateBluetooth: (durationMinutes: Int) -> Unit,
    onCancel: () -> Unit,
) {
    var pairingMode by remember { mutableStateOf(PairingMode.QR_CODE) }
    var selectedDuration by remember { mutableIntStateOf(30) }
    var selectedDelay by remember { mutableIntStateOf(1) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .calmBackground()
            .safeDrawingPadding()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.group_pause_host_title),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Text(
            text = stringResource(R.string.group_pause_host_intro),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 20.dp)) {
            PairingModePill(
                label = stringResource(R.string.group_pause_pairing_mode_qr),
                selected = pairingMode == PairingMode.QR_CODE,
                onClick = { pairingMode = PairingMode.QR_CODE },
            )
            PairingModePill(
                label = stringResource(R.string.group_pause_pairing_mode_bluetooth),
                selected = pairingMode == PairingMode.BLUETOOTH,
                onClick = { pairingMode = PairingMode.BLUETOOTH },
            )
        }

        SetupLabel(stringResource(R.string.group_pause_duration_label))
        MinutePillRow(
            options = DURATION_OPTIONS,
            selected = selectedDuration,
            onSelect = { selectedDuration = it },
            labelFor = { minutesLabel(it) },
        )

        if (pairingMode == PairingMode.QR_CODE) {
            SetupLabel(stringResource(R.string.group_pause_start_in_label), topPadding = 24.dp)
            MinutePillRow(
                options = DELAY_OPTIONS,
                selected = selectedDelay,
                onSelect = { selectedDelay = it },
                labelFor = { minutesLabel(it) },
            )
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 32.dp)) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            ) {
                Text(stringResource(android.R.string.cancel))
            }
            Button(
                onClick = {
                    if (pairingMode == PairingMode.QR_CODE) {
                        onCreateQr(selectedDuration, selectedDelay)
                    } else {
                        onCreateBluetooth(selectedDuration)
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    stringResource(
                        if (pairingMode == PairingMode.QR_CODE) {
                            R.string.group_pause_create_button
                        } else {
                            R.string.group_pause_bt_open_lobby_button
                        }
                    )
                )
            }
        }
    }
}

@Composable
private fun PairingModePill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primary.copy(alpha = if (selected) 0.22f else 0.08f),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (selected) 1f else 0.65f),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun SetupLabel(text: String, topPadding: androidx.compose.ui.unit.Dp = 0.dp) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topPadding, bottom = 8.dp),
        textAlign = TextAlign.Center,
    )
}

private fun minutesLabel(minutes: Int): String {
    if (minutes < 60) return "${minutes}m"
    val hours = minutes / 60
    val remainder = minutes % 60
    return if (remainder == 0) "${hours}h" else "${hours}h${remainder}"
}

/**
 * Riga di pillole generica per un valore in minuti — stesso stile a bassa
 * opacità di `DurationChipRow`/`GoalChipRow` (privati ai rispettivi file,
 * da cui la duplicazione qui, stessa convenzione già seguita altrove in
 * questo codebase).
 */
@Composable
private fun MinutePillRow(
    options: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
    labelFor: (Int) -> String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { value ->
            val isSelected = value == selected
            Surface(
                onClick = { onSelect(value) },
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primary.copy(alpha = if (isSelected) 0.22f else 0.08f),
            ) {
                Text(
                    text = labelFor(value),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isSelected) 1f else 0.65f),
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

/** QR + codice manuale da condividere — mostrato sopra al conto alla rovescia condiviso. */
@Composable
private fun GroupPauseShareHeader(code: String) {
    val qrBitmap = remember(code) { generateQrCodeBitmap(code, sizePx = 512) }

    Text(
        text = stringResource(R.string.group_pause_share_hint),
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(bottom = 16.dp)
    )
    Image(
        bitmap = qrBitmap.asImageBitmap(),
        contentDescription = null,
        modifier = Modifier.size(220.dp)
    )
    Text(
        text = code,
        fontFamily = FontFamily.Monospace,
        fontSize = 16.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp)
    )
}
