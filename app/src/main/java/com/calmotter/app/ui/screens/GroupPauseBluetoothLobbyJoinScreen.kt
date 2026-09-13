package com.calmotter.app.ui.screens

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import com.calmotter.app.bluetooth.GroupPauseBluetoothJoin
import com.calmotter.app.bluetooth.bluetoothAdapterOrNull
import com.calmotter.app.decodeGroupPauseRecipe
import com.calmotter.app.groupPauseBluetoothRuntimePermissions
import com.calmotter.app.hasGroupPauseBluetoothPermissions
import com.calmotter.app.nfc.GroupPauseNfcReader

private enum class JoinLobbyStep { PERMISSIONS, ENABLE_BLUETOOTH, ACTIVE }

private sealed class JoinLobbyState {
    data object ChoosingMode : JoinLobbyState()
    data object WaitingForNfcTap : JoinLobbyState()
    data object Scanning : JoinLobbyState()
    data object Connecting : JoinLobbyState()
    data object WaitingForHost : JoinLobbyState()
    data class Error(val message: String) : JoinLobbyState()
}

/**
 * Lobby dal vivo lato joiner (Fase 2, vedi specs/group-pause/design.md):
 * NFC (avvicina il telefono dell'host, poi si collega da solo al primo
 * dispositivo Bluetooth che annuncia il nome letto via NFC) oppure ricerca
 * manuale Bluetooth (elenco dei dispositivi trovati, tocca quello giusto).
 * Una volta connesso, resta in attesa del messaggio RECIPE che l'host
 * invia quando tocca "Avvia" — nessuna azione richiesta qui nel frattempo.
 */
@Composable
fun GroupPauseBluetoothLobbyJoinScreen(
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

    val step = when {
        !hasPermissions -> JoinLobbyStep.PERMISSIONS
        !bluetoothEnabled -> JoinLobbyStep.ENABLE_BLUETOOTH
        else -> JoinLobbyStep.ACTIVE
    }

    when (step) {
        JoinLobbyStep.PERMISSIONS -> BluetoothJoinStepScreen(
            message = stringResource(R.string.group_pause_bt_permission_rationale),
            actionLabel = stringResource(R.string.permission_action_grant),
            onAction = { permissionLauncher.launch(groupPauseBluetoothRuntimePermissions()) },
            onCancel = onCancel,
        )
        JoinLobbyStep.ENABLE_BLUETOOTH -> BluetoothJoinStepScreen(
            message = stringResource(R.string.group_pause_bt_enable_prompt),
            actionLabel = stringResource(R.string.group_pause_bt_enable_button),
            onAction = { enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) },
            onCancel = onCancel,
        )
        JoinLobbyStep.ACTIVE -> BluetoothLobbyJoinActive(onRecipeReady = onRecipeReady, onCancel = onCancel)
    }
}

@Composable
private fun BluetoothLobbyJoinActive(
    onRecipeReady: (GroupPauseRecipe) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as Activity
    val localName = remember { localBluetoothDisplayName(context) }
    val join = remember { GroupPauseBluetoothJoin(context) }
    val nfcReader = remember { GroupPauseNfcReader(activity) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var state by remember { mutableStateOf<JoinLobbyState>(JoinLobbyState.ChoosingMode) }
    val invalidCodeText = stringResource(R.string.group_pause_invalid_code)
    val connectionLostText = stringResource(R.string.group_pause_bt_join_error)

    DisposableEffect(Unit) {
        onDispose {
            join.stop()
            nfcReader.stop()
        }
    }

    fun onRecipeCode(code: String) {
        val recipe = decodeGroupPauseRecipe(code)
        if (recipe != null) onRecipeReady(recipe) else state = JoinLobbyState.Error(invalidCodeText)
    }

    fun connect(device: android.bluetooth.BluetoothDevice) {
        state = JoinLobbyState.Connecting
        join.connectTo(
            device = device,
            localDisplayName = localName,
            onConnected = { mainHandler.post { state = JoinLobbyState.WaitingForHost } },
            onRecipe = { code -> mainHandler.post { onRecipeCode(code) } },
            onError = { mainHandler.post { state = JoinLobbyState.Error(connectionLostText) } },
        )
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
            text = stringResource(R.string.group_pause_bt_join_title),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        when (val current = state) {
            JoinLobbyState.ChoosingMode -> {
                Button(
                    onClick = {
                        state = JoinLobbyState.WaitingForNfcTap
                        nfcReader.start { marker ->
                            mainHandler.post {
                                state = JoinLobbyState.Scanning
                                join.startDiscovery(
                                    autoConnectToNameMarker = marker,
                                    onAutoMatch = { device -> connect(device) },
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                ) {
                    Text(stringResource(R.string.group_pause_bt_join_nfc_button))
                }
                Button(
                    onClick = {
                        state = JoinLobbyState.Scanning
                        join.startDiscovery()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.group_pause_bt_join_scan_button))
                }
            }
            JoinLobbyState.WaitingForNfcTap -> {
                Text(
                    text = stringResource(R.string.group_pause_bt_join_waiting_nfc),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            }
            JoinLobbyState.Scanning -> {
                if (join.discovered.isEmpty()) {
                    Text(
                        text = stringResource(R.string.group_pause_bt_join_device_list_empty),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    join.discovered.forEach { found ->
                        Surface(
                            onClick = { connect(found.device) },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        ) {
                            Text(
                                text = found.name,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
            JoinLobbyState.Connecting -> Text(
                text = stringResource(R.string.group_pause_bt_join_connecting),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            JoinLobbyState.WaitingForHost -> Text(
                text = stringResource(R.string.group_pause_bt_join_waiting_host),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            is JoinLobbyState.Error -> {
                Text(
                    text = current.message,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                Button(onClick = { state = JoinLobbyState.ChoosingMode }) {
                    Text(stringResource(R.string.group_pause_bt_retry_button))
                }
            }
        }

        OutlinedButton(onClick = onCancel, modifier = Modifier.padding(top = 24.dp)) {
            Text(stringResource(android.R.string.cancel))
        }
    }
}

@Composable
private fun BluetoothJoinStepScreen(
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

/**
 * Nome da annunciare all'host via HELLO — il nome che l'utente ha dato al
 * proprio telefono (Impostazioni > Info telefono > Nome dispositivo),
 * leggibile senza alcun permesso Bluetooth; se non impostato, il modello
 * del dispositivo come fallback generico.
 */
private fun localBluetoothDisplayName(context: android.content.Context): String =
    Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
        ?: Build.MODEL
        ?: "Calm Otter"
