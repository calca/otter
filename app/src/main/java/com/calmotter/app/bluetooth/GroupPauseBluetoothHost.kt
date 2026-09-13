package com.calmotter.app.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Lato host della lobby dal vivo (Fase 2, vedi specs/group-pause/design.md):
 * accetta connessioni RFCOMM da uno o più joiner, tiene la lista dei nomi
 * dichiarati via HELLO, e infine trasmette la ricetta finale a tutti prima
 * di chiudere. Istanza singola per visita alla schermata di lobby (creata
 * con `remember { }`, distrutta in `DisposableEffect`'s `onDispose` via
 * [stop]) — non un singleton `getInstance()` come i manager applicativi
 * (SessionManager e simili): questo stato è vivo solo mentre la lobby è a
 * schermo, stesso ciclo di vita del binding camera di `QrScannerView`.
 *
 * Chi chiama [start]/[stop] deve aver già verificato
 * `hasGroupPauseBluetoothPermissions()` — da qui i `@SuppressLint
 * ("MissingPermission")`: senza quel controllo a monte queste chiamate
 * lancerebbero `SecurityException` su Android 12+.
 */
class GroupPauseBluetoothHost(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serverSocket: BluetoothServerSocket? = null
    private val sockets = mutableListOf<BluetoothSocket>()
    private var originalAdapterName: String? = null

    /** Nomi dichiarati dai partecipanti collegati finora — osservabile direttamente da Compose. */
    val participantNames = mutableStateListOf<String>()

    @SuppressLint("MissingPermission")
    fun start(lobbyMarkerName: String) {
        val adapter = bluetoothAdapterOrNull(context) ?: return
        originalAdapterName = adapter.name
        adapter.name = lobbyMarkerName
        scope.launch {
            try {
                val server = adapter.listenUsingInsecureRfcommWithServiceRecord(
                    lobbyMarkerName,
                    GROUP_PAUSE_SERVICE_UUID,
                )
                serverSocket = server
                while (true) {
                    val socket = server.accept() ?: break
                    acceptSocket(socket)
                }
            } catch (e: IOException) {
                // Socket del server chiuso da stop()/broadcastRecipeAndClose(), o radio
                // spenta a metà — nessun retry automatico: la schermata mostra lo stato
                // corrente tramite `participantNames`, non serve altra segnalazione qui.
            }
        }
    }

    private fun acceptSocket(socket: BluetoothSocket) {
        scope.launch {
            try {
                val line = readLine(socket.inputStream)
                val hello = line?.let(::parseGroupPauseBtMessage) as? GroupPauseBtMessage.Hello
                if (hello == null) {
                    runCatching { socket.close() }
                    return@launch
                }
                synchronized(sockets) { sockets.add(socket) }
                participantNames.add(hello.displayName)
            } catch (e: IOException) {
                // Connessione caduta prima dell'HELLO — nessun partecipante aggiunto.
            }
        }
    }

    /** Invia la ricetta a ogni partecipante collegato, poi chiude tutto — usato una sola volta, a "Avvia". */
    fun broadcastRecipeAndClose(code: String) {
        synchronized(sockets) {
            for (socket in sockets) {
                try {
                    writeLine(socket.outputStream, formatRecipe(code))
                } catch (e: IOException) {
                    // Partecipante già disconnesso — il conto alla rovescia dell'host procede comunque.
                } finally {
                    runCatching { socket.close() }
                }
            }
        }
        stop()
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        runCatching { serverSocket?.close() }
        scope.cancel()
        val adapter = bluetoothAdapterOrNull(context)
        val previousName = originalAdapterName
        if (adapter != null && previousName != null) {
            runCatching { adapter.name = previousName }
        }
    }
}
