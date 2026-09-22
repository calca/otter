package com.calmotter.app.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.compose.runtime.mutableStateListOf
import androidx.core.content.ContextCompat
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

data class DiscoveredBtDevice(val device: BluetoothDevice, val name: String)

/**
 * Lato joiner della lobby dal vivo (Fase 2, vedi specs/group-pause/design.md):
 * avvia il discovery Bluetooth classico, espone i dispositivi trovati, e si
 * connette a quello scelto (manualmente, o in automatico se [startDiscovery]
 * riceve un `autoConnectToNameMarker` — il caso NFC: il joiner ha già letto
 * via NFC il nome che l'host sta annunciando, quindi non serve mostrare un
 * elenco da toccare, basta collegarsi al primo dispositivo trovato con quel
 * nome esatto). Stesso ciclo di vita ephemeral di [GroupPauseBluetoothHost]
 * — un'istanza per visita alla schermata, distrutta in [stop].
 */
class GroupPauseBluetoothJoin(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var socket: BluetoothSocket? = null
    private var receiver: BroadcastReceiver? = null

    val discovered = mutableStateListOf<DiscoveredBtDevice>()

    @SuppressLint("MissingPermission")
    fun startDiscovery(
        autoConnectToNameMarker: String? = null,
        onAutoMatch: (BluetoothDevice) -> Unit = {},
    ) {
        discovered.clear()
        val adapter = bluetoothAdapterOrNull(context) ?: return

        val br = object : BroadcastReceiver() {
            override fun onReceive(receivedContext: Context, intent: Intent) {
                val device = intent.bluetoothDeviceExtra() ?: return
                val name = device.name ?: return
                // Segnalato: senza filtro, il discovery Bluetooth classico
                // mostra qualunque dispositivo classico in raggio —
                // lavatrice, stampante, lampadine — non solo l'altro
                // telefono che sta ospitando la lobby. L'host qui è sempre
                // e solo un telefono Android (nessun altro tipo di
                // dispositivo espone il servizio RFCOMM di questa app), e
                // Android riporta la classe hardware reale del dispositivo
                // trovato (non qualcosa che l'app annuncia da sé) —
                // `Major.PHONE` la filtra correttamente senza dover
                // indovinare nomi o pattern.
                if (!device.isPhoneClass()) return
                if (discovered.none { it.device.address == device.address }) {
                    discovered.add(DiscoveredBtDevice(device, name))
                }
                if (autoConnectToNameMarker != null && name == autoConnectToNameMarker) {
                    runCatching { adapter.cancelDiscovery() }
                    onAutoMatch(device)
                }
            }
        }
        receiver = br
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(br, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(br, filter)
        }
        adapter.startDiscovery()
    }

    @SuppressLint("MissingPermission")
    fun connectTo(
        device: BluetoothDevice,
        localDisplayName: String,
        onConnected: () -> Unit = {},
        onLobbyInfo: (hostName: String, durationMinutes: Int) -> Unit = { _, _ -> },
        onRecipe: (String) -> Unit,
        onError: () -> Unit,
    ) {
        scope.launch {
            try {
                bluetoothAdapterOrNull(context)?.let { runCatching { it.cancelDiscovery() } }
                val newSocket = device.createInsecureRfcommSocketToServiceRecord(GROUP_PAUSE_SERVICE_UUID)
                newSocket.connect()
                socket = newSocket
                writeLine(newSocket.outputStream, formatHello(localDisplayName))
                onConnected()
                while (true) {
                    val line = readLine(newSocket.inputStream) ?: break
                    when (val message = parseGroupPauseBtMessage(line)) {
                        is GroupPauseBtMessage.Recipe -> {
                            onRecipe(message.code)
                            break
                        }
                        // Arriva subito dopo l'HELLO: dice di chi è la lobby e
                        // per quanto. Non interrompe il ciclo — si continua ad
                        // aspettare la ricetta vera, che arriva solo all'avvio.
                        is GroupPauseBtMessage.LobbyInfo ->
                            onLobbyInfo(message.hostName, message.durationMinutes)
                        // Righe sconosciute (o un HELLO di ritorno, che non ci
                        // si aspetta qui) vengono ignorate, non chiudono nulla.
                        else -> Unit
                    }
                }
            } catch (e: IOException) {
                onError()
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        runCatching { bluetoothAdapterOrNull(context)?.cancelDiscovery() }
        receiver?.let { runCatching { context.unregisterReceiver(it) } }
        receiver = null
        runCatching { socket?.close() }
        scope.cancel()
    }
}

// API 33+ deprecates getParcelableExtra(String) senza classe esplicita;
// isolato qui per non sparpagliare l'annotazione @Suppress nel resto del file.
@Suppress("DEPRECATION")
private fun Intent.bluetoothDeviceExtra(): BluetoothDevice? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
    } else {
        getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
    }

// `getBluetoothClass()` può tornare null (dispositivo che non l'ha ancora
// annunciato al momento di ACTION_FOUND, o adapter che non la conosce
// affatto) — in quel caso il dispositivo resta escluso invece di essere
// mostrato per dubbio: coerente con lo scopo del filtro (nascondere ciò
// che non si sa essere un telefono), non un edge case da gestire a parte.
@SuppressLint("MissingPermission")
private fun BluetoothDevice.isPhoneClass(): Boolean =
    bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.PHONE
