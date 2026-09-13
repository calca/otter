package com.calmotter.app.ui.screens

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.nfc.NfcAdapter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calmotter.app.GroupPauseRecipe
import com.calmotter.app.R
import com.calmotter.app.bluetooth.GroupPauseBluetoothHost
import com.calmotter.app.bluetooth.bluetoothAdapterOrNull
import com.calmotter.app.bluetooth.groupPauseLobbyNameMarker
import com.calmotter.app.encode
import com.calmotter.app.groupPauseBluetoothRuntimePermissions
import com.calmotter.app.hasGroupPauseBluetoothPermissions
import com.calmotter.app.nfc.GroupPauseHceService
import kotlin.random.Random

private enum class HostLobbyStep { PERMISSIONS, ENABLE_BLUETOOTH, DISCOVERABLE, ACTIVE }

/**
 * Lobby dal vivo lato host (Fase 2, vedi specs/group-pause/design.md):
 * permessi → Bluetooth acceso → dispositivo visibile → lista partecipanti
 * dal vivo con "Avvia" bloccato finché non c'è almeno un partecipante.
 * A differenza del percorso QR/codice (Fase 1), qui non viene chiesta una
 * durata "tra quanto iniziare": l'avvio è un'azione dal vivo dell'host, non
 * pianificata.
 */
@Composable
fun GroupPauseBluetoothLobbyHostScreen(
    durationMinutes: Int,
    onRecipeReady: (GroupPauseRecipe) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    var hasPermissions by remember { mutableStateOf(hasGroupPauseBluetoothPermissions(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results -> hasPermissions = results.values.all { it } }

    val adapter = remember { bluetoothAdapterOrNull(context) }
    var bluetoothEnabled by remember { mutableStateOf(adapter?.isEnabled == true) }
    val enableBtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { bluetoothEnabled = adapter?.isEnabled == true }

    var discoverable by remember { mutableStateOf(false) }
    val discoverableLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> discoverable = result.resultCode != Activity.RESULT_CANCELED }

    val step = when {
        !hasPermissions -> HostLobbyStep.PERMISSIONS
        !bluetoothEnabled -> HostLobbyStep.ENABLE_BLUETOOTH
        !discoverable -> HostLobbyStep.DISCOVERABLE
        else -> HostLobbyStep.ACTIVE
    }

    when (step) {
        HostLobbyStep.PERMISSIONS -> BluetoothStepScreen(
            message = stringResource(R.string.group_pause_bt_permission_rationale),
            actionLabel = stringResource(R.string.permission_action_grant),
            onAction = { permissionLauncher.launch(groupPauseBluetoothRuntimePermissions()) },
            onCancel = onCancel,
        )
        HostLobbyStep.ENABLE_BLUETOOTH -> BluetoothStepScreen(
            message = stringResource(R.string.group_pause_bt_enable_prompt),
            actionLabel = stringResource(R.string.group_pause_bt_enable_button),
            onAction = { enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) },
            onCancel = onCancel,
        )
        HostLobbyStep.DISCOVERABLE -> BluetoothStepScreen(
            message = stringResource(R.string.group_pause_bt_discoverable_prompt),
            actionLabel = stringResource(R.string.group_pause_bt_discoverable_button),
            onAction = {
                val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                    .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
                discoverableLauncher.launch(intent)
            },
            onCancel = onCancel,
        )
        HostLobbyStep.ACTIVE -> BluetoothLobbyHostActive(
            durationMinutes = durationMinutes,
            onRecipeReady = onRecipeReady,
            onCancel = onCancel,
        )
    }
}

@Composable
private fun BluetoothLobbyHostActive(
    durationMinutes: Int,
    onRecipeReady: (GroupPauseRecipe) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val groupTag = remember { Random.nextInt(0, 65536) }
    val markerName = remember(groupTag) { groupPauseLobbyNameMarker(groupTag) }
    val host = remember { GroupPauseBluetoothHost(context) }
    var nfcEnabled by remember { mutableStateOf(false) }
    val nfcAvailable = remember { NfcAdapter.getDefaultAdapter(context) != null }

    DisposableEffect(markerName) {
        host.start(markerName)
        onDispose {
            host.stop()
            GroupPauseHceService.pendingMarker = null
        }
    }

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
            text = stringResource(R.string.group_pause_bt_lobby_title),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (host.participantNames.isEmpty()) {
            Text(
                text = stringResource(R.string.group_pause_bt_participants_none),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 20.dp)
            )
        } else {
            Text(
                text = stringResource(R.string.group_pause_bt_participants_count, host.participantNames.size),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            host.participantNames.forEach { name ->
                Text(text = name, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
            }
        }

        if (nfcAvailable) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 20.dp, bottom = 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.group_pause_bt_nfc_toggle),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(end = 12.dp)
                )
                Switch(
                    checked = nfcEnabled,
                    onCheckedChange = { checked ->
                        nfcEnabled = checked
                        GroupPauseHceService.pendingMarker = if (checked) markerName else null
                    }
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            ) {
                Text(stringResource(android.R.string.cancel))
            }
            Button(
                onClick = {
                    val recipe = GroupPauseRecipe(
                        durationMinutes = durationMinutes,
                        startAtEpochMillis = System.currentTimeMillis() + 5_000L,
                        groupTag = groupTag,
                    )
                    host.broadcastRecipeAndClose(recipe.encode())
                    onRecipeReady(recipe)
                },
                enabled = host.participantNames.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.group_pause_bt_start_button))
            }
        }
    }
}

/** Schermata condivisa per i passi "serve fare qualcosa prima di continuare" (permessi/Bluetooth/visibilità). */
@Composable
private fun BluetoothStepScreen(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    onCancel: () -> Unit,
) {
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
            text = message,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 20.dp)
        )
        Button(onClick = onAction) { Text(actionLabel) }
        OutlinedButton(onClick = onCancel, modifier = Modifier.padding(top = 12.dp)) {
            Text(stringResource(android.R.string.cancel))
        }
    }
}
