package com.calmotter.app.ui.screens

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.calmotter.app.ui.mascot.OtterFloatMark
import com.calmotter.app.ui.mascot.OtterTapMark

private sealed class JoinLobbyState {
    data object NfcHero : JoinLobbyState()
    data object SearchHero : JoinLobbyState()
    data object Connecting : JoinLobbyState()
    data object WaitingForHost : JoinLobbyState()
    data class Error(val message: String) : JoinLobbyState()
}

/**
 * Lobby dal vivo lato joiner (Fase 2, vedi specs/group-pause/design.md):
 * l'NFC è il gesto predefinito quando disponibile ("avvicina i telefoni"),
 * con la ricerca Bluetooth manuale e il codice/QR come ripieghi secondari —
 * non tre opzioni alla pari come nella prima versione. Stesso avviso
 * gentile permessi/Bluetooth di [GroupPauseBluetoothLobbyHostScreen], nessuna
 * schermata dedicata per quei due passi.
 */
@Composable
fun GroupPauseBluetoothLobbyJoinScreen(
    onRecipeReady: (GroupPauseRecipe) -> Unit,
    onWantCodeInstead: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as Activity
    var hasPermissions by remember { mutableStateOf(hasGroupPauseBluetoothPermissions(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results -> hasPermissions = results.values.all { it } }

    val adapter = remember { bluetoothAdapterOrNull(context) }
    var bluetoothEnabled by remember { mutableStateOf(adapter?.isEnabled == true) }
    val enableBtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { bluetoothEnabled = adapter?.isEnabled == true }

    val allReady = hasPermissions && bluetoothEnabled
    val nfcAvailable = remember { NfcAdapter.getDefaultAdapter(context) != null }
    val localName = remember { localBluetoothDisplayName(context) }
    val join = remember { GroupPauseBluetoothJoin(context) }
    val nfcReader = remember { GroupPauseNfcReader(activity) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    var state by remember { mutableStateOf(if (nfcAvailable) JoinLobbyState.NfcHero else JoinLobbyState.SearchHero) }
    val invalidCodeText = stringResource(R.string.group_pause_invalid_code)
    val connectionLostText = stringResource(R.string.group_pause_join_error)

    fun onRecipeCode(code: String) {
        val recipe = decodeGroupPauseRecipe(code)
        if (recipe != null) onRecipeReady(recipe) else state = JoinLobbyState.Error(invalidCodeText)
    }

    val onReadinessAction: () -> Unit = {
        if (!hasPermissions) {
            permissionLauncher.launch(groupPauseBluetoothRuntimePermissions())
        } else {
            enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
    }

    fun connect(device: BluetoothDevice) {
        state = JoinLobbyState.Connecting
        join.connectTo(
            device = device,
            localDisplayName = localName,
            onConnected = { mainHandler.post { state = JoinLobbyState.WaitingForHost } },
            onRecipe = { code -> mainHandler.post { onRecipeCode(code) } },
            onError = { mainHandler.post { state = JoinLobbyState.Error(connectionLostText) } },
        )
    }

    // L'NFC/il discovery partono solo quando permessi+Bluetooth sono pronti
    // (allReady) — prima di allora la schermata mostra comunque l'illustrazione
    // e l'avviso gentile, ma nessuna chiamata Bluetooth/NFC reale.
    DisposableEffect(state, allReady) {
        if (allReady && state == JoinLobbyState.NfcHero) {
            nfcReader.start { marker ->
                mainHandler.post {
                    state = JoinLobbyState.Connecting
                    join.startDiscovery(autoConnectToNameMarker = marker, onAutoMatch = { device -> connect(device) })
                }
            }
        } else if (allReady && state == JoinLobbyState.SearchHero) {
            join.startDiscovery()
        }
        onDispose {
            nfcReader.stop()
            join.stop()
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
        when (val current = state) {
            JoinLobbyState.NfcHero -> {
                OtterTapMark(markSize = 88.dp)
                Text(
                    text = stringResource(R.string.group_pause_join_nfc_title),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)
                )
                Text(
                    text = stringResource(R.string.group_pause_join_nfc_subtitle),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                )
                if (!allReady) {
                    GentleReadinessBanner(hasPermissions, onReadinessAction)
                }
                TextButton(onClick = { state = JoinLobbyState.SearchHero }, modifier = Modifier.padding(top = 20.dp)) {
                    Text(stringResource(R.string.group_pause_join_search_link))
                }
                TextButton(onClick = onWantCodeInstead) {
                    Text(stringResource(R.string.group_pause_join_code_link))
                }
            }
            JoinLobbyState.SearchHero -> {
                SearchingIllustration(hasResults = join.discovered.isNotEmpty())
                Text(
                    text = stringResource(R.string.group_pause_join_search_hero_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)
                )
                if (join.discovered.isEmpty()) {
                    Text(
                        text = stringResource(R.string.group_pause_join_searching),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center,
                    )
                } else {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
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
                if (!allReady) {
                    GentleReadinessBanner(hasPermissions, onReadinessAction)
                }
                if (nfcAvailable) {
                    TextButton(onClick = { state = JoinLobbyState.NfcHero }, modifier = Modifier.padding(top = 16.dp)) {
                        Text(stringResource(R.string.group_pause_join_nfc_title))
                    }
                }
                TextButton(onClick = onWantCodeInstead) {
                    Text(stringResource(R.string.group_pause_join_code_link))
                }
            }
            JoinLobbyState.Connecting -> {
                OtterFloatMark(markSize = 88.dp)
                Text(
                    text = stringResource(R.string.group_pause_join_connecting),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 14.dp)
                )
            }
            JoinLobbyState.WaitingForHost -> {
                OtterFloatMark(markSize = 88.dp)
                Text(
                    text = stringResource(R.string.group_pause_join_waiting_host),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 14.dp)
                )
            }
            is JoinLobbyState.Error -> {
                Text(
                    text = current.message,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                Button(onClick = { state = if (nfcAvailable) JoinLobbyState.NfcHero else JoinLobbyState.SearchHero }) {
                    Text(stringResource(R.string.group_pause_join_retry_button))
                }
            }
        }

        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
            Text(stringResource(android.R.string.cancel))
        }
    }
}

@Composable
private fun GentleReadinessBanner(hasPermissions: Boolean, onAction: () -> Unit) {
    Surface(
        onClick = onAction,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    if (!hasPermissions) R.string.group_pause_bt_permission_notice
                    else R.string.group_pause_bt_gentle_notice
                ),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(
                    if (!hasPermissions) R.string.group_pause_bt_permission_action
                    else R.string.group_pause_bt_gentle_action
                ),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

/**
 * Otter al centro di anelli concentrici che si espandono e svaniscono in
 * loop mentre [hasResults] è falso — "sto cercando un amico", non un
 * dispositivo. Si ferma da sola (nessun anello) appena la lista smette di
 * essere vuota.
 */
@Composable
private fun SearchingIllustration(hasResults: Boolean) {
    Box(modifier = Modifier.size(140.dp), contentAlignment = Alignment.Center) {
        if (!hasResults) {
            val transition = rememberInfiniteTransition(label = "searching")
            val t by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(animation = tween(1800, easing = LinearEasing)),
                label = "searchT",
            )
            val ringColor = MaterialTheme.colorScheme.primary
            Canvas(modifier = Modifier.size(140.dp)) {
                listOf(0f, 0.33f, 0.66f).forEach { phase ->
                    val localT = (t + phase) % 1f
                    drawCircle(
                        color = ringColor,
                        radius = size.minDimension / 5f + localT * size.minDimension / 2.6f,
                        alpha = (1f - localT) * 0.4f,
                        style = Stroke(width = 1.5.dp.toPx()),
                    )
                }
            }
        }
        OtterFloatMark(markSize = 76.dp)
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
