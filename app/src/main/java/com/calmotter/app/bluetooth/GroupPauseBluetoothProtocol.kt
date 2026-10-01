package com.calmotter.app.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.provider.Settings
import android.os.Build
import com.calmotter.app.GROUP_PAUSE_PROTOCOL_VERSION
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * `BluetoothAdapter.getDefaultAdapter()` è deprecato da API 33 a favore di
 * `BluetoothManager.getAdapter()` — un solo punto per questa lettura,
 * condiviso da host, joiner e dalle due schermate di lobby, invece di
 * ripetere la stessa chiamata a `getSystemService` in quattro posti.
 */
fun bluetoothAdapterOrNull(context: Context): BluetoothAdapter? =
    (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

/**
 * Protocollo minimale usato dalla lobby dal vivo di Pausa di gruppo (Fase 2,
 * vedi specs/group-pause/design.md) sopra un socket RFCOMM Bluetooth
 * classico: righe di testo terminate da '\n', un prefisso per tipo di
 * messaggio. Nessuna libreria di serializzazione: due soli messaggi
 * esistono per tutta la vita di una connessione (HELLO una volta all'inizio,
 * RECIPE una volta alla fine), non serve altro.
 *
 * L'UUID del servizio è fisso e hardcoded (non generato per sessione):
 * host e joiner devono conoscerlo entrambi in anticipo per aprire lo stesso
 * canale RFCOMM — è l'equivalente Bluetooth del formato fisso del codice
 * QR/manuale di Fase 1.
 */
val GROUP_PAUSE_SERVICE_UUID: UUID = UUID.fromString("7e5a1c9e-7c2b-4b8b-9d1c-6f2a3b4c5d6e")

private const val HELLO_PREFIX = "HELLO:"
private const val MISMATCH_PREFIX = "MISMATCH:"
private const val RECIPE_PREFIX = "RECIPE:"
private const val LOBBY_PREFIX = "LOBBY:"
private const val LOBBY_SEPARATOR = '|'

sealed class GroupPauseBtMessage {
    /** [version] = [com.calmotter.app.GROUP_PAUSE_PROTOCOL_VERSION] di chi si unisce; 0 se non dichiarata. */
    data class Hello(val displayName: String, val version: Int = 0) : GroupPauseBtMessage()

    /** Host → joiner: versioni diverse, la connessione si chiude. */
    data class VersionMismatch(val hostVersion: Int) : GroupPauseBtMessage()
    data class Recipe(val code: String) : GroupPauseBtMessage()

    /**
     * Host → joiner, subito dopo l'HELLO: chi sta ospitando e per quanto.
     *
     * Aggiunto perché il protocollo era asimmetrico — l'host imparava il
     * nome del joiner dall'HELLO, il joiner non sapeva nulla fino al
     * `RECIPE`, che arriva solo all'avvio. Si accettava quindi di farsi
     * bloccare il telefono per una durata scoperta a pausa già cominciata,
     * e senza sapere di chi fosse la lobby: nella lista dei dispositivi
     * l'host compare come "CalmOtter-<tag>", perché è così che si rinomina
     * l'adattatore (vedi [groupPauseLobbyNameMarker]).
     */
    data class LobbyInfo(
        val hostName: String,
        val durationMinutes: Int,
        // Attività proposta dall'host (TogetherActivities), 0 = nessuna.
        val activityId: Int = 0,
    ) : GroupPauseBtMessage()
}

fun formatHello(displayName: String, version: Int = GROUP_PAUSE_PROTOCOL_VERSION): String =
    HELLO_PREFIX + version + LOBBY_SEPARATOR + sanitizeDisplayName(displayName)

fun formatVersionMismatch(hostVersion: Int = GROUP_PAUSE_PROTOCOL_VERSION): String = MISMATCH_PREFIX + hostVersion

fun formatRecipe(code: String): String = RECIPE_PREFIX + code

fun formatLobbyInfo(hostName: String, durationMinutes: Int, activityId: Int = 0): String =
    LOBBY_PREFIX + sanitizeDisplayName(hostName) + LOBBY_SEPARATOR + durationMinutes + LOBBY_SEPARATOR + activityId

/**
 * Ritorna `null` per una riga che non corrisponde a nessun messaggio noto —
 * il che rende l'aggiunta di [GroupPauseBtMessage.LobbyInfo] compatibile con
 * una versione precedente all'altro capo: chi non la conosce la ignora e
 * prosegue, invece di rompere la connessione.
 */
fun parseGroupPauseBtMessage(line: String): GroupPauseBtMessage? = when {
    line.startsWith(HELLO_PREFIX) -> {
        // "versione|nome"; senza versione (formato precedente) vale 0, cioè
        // "un'altra versione", e l'host lo dirà.
        val payload = line.removePrefix(HELLO_PREFIX)
        val separator = payload.indexOf(LOBBY_SEPARATOR)
        val version = if (separator > 0) payload.substring(0, separator).toIntOrNull() else null
        if (version != null) {
            GroupPauseBtMessage.Hello(payload.substring(separator + 1), version)
        } else {
            GroupPauseBtMessage.Hello(payload, 0)
        }
    }
    line.startsWith(MISMATCH_PREFIX) ->
        line.removePrefix(MISMATCH_PREFIX).toIntOrNull()?.let { GroupPauseBtMessage.VersionMismatch(it) }
    line.startsWith(RECIPE_PREFIX) -> GroupPauseBtMessage.Recipe(line.removePrefix(RECIPE_PREFIX))
    line.startsWith(LOBBY_PREFIX) -> {
        // "nome|minuti|attività": il nome non contiene mai il separatore
        // (sanitizeDisplayName), quindi bastano tre parti esatte.
        val parts = line.removePrefix(LOBBY_PREFIX).split(LOBBY_SEPARATOR)
        val minutes = parts.getOrNull(1)?.toIntOrNull()
        val activityId = parts.getOrNull(2)?.toIntOrNull()
        // Una durata mancante o non numerica rende il messaggio inutile:
        // meglio ignorarlo del tutto che mostrare "0 minuti" al joiner.
        if (parts.size == 3 && minutes != null && minutes > 0 && activityId != null && activityId >= 0) {
            GroupPauseBtMessage.LobbyInfo(parts[0], minutes, activityId)
        } else {
            null
        }
    }
    else -> null
}

// Un nome dispositivo non dovrebbe mai contenere newline, ma non ci si può
// fidare dell'input dell'altro estremo del socket: un newline incorporato
// spezzerebbe il framing a righe di questo protocollo.
private fun sanitizeDisplayName(name: String): String =
    name.replace('\n', ' ').replace('\r', ' ')
        // Il separatore di LOBBY: un nome che lo contenesse spezzerebbe il
        // parsing tanto quanto un newline spezza il framing a righe.
        .replace(LOBBY_SEPARATOR, ' ')
        .trim().take(40)

/**
 * Nome che l'host imposta come nome del proprio adattatore Bluetooth
 * (visibile durante il discovery classico) e come payload del servizio HCE
 * NFC — vedi design.md, "Perché l'NFC scambia un nome, non un indirizzo
 * MAC": `groupTag` è lo stesso tipo di tag cosmetico casuale già usato da
 * [com.calmotter.app.GroupPauseRecipe], qui generato all'apertura della
 * lobby (non alla creazione della ricetta finale, che avviene solo dopo
 * "Avvia").
 */
fun groupPauseLobbyNameMarker(groupTag: Int): String = "CalmOtter-$groupTag"

fun writeLine(output: OutputStream, line: String) {
    output.write((line + "\n").toByteArray(StandardCharsets.UTF_8))
    output.flush()
}

/** Legge una riga terminata da '\n' (CR opzionale ignorato). `null` se lo stream è finito senza dati. */
fun readLine(input: InputStream): String? {
    val builder = StringBuilder()
    while (true) {
        val next = input.read()
        if (next == -1) return if (builder.isEmpty()) null else builder.toString()
        if (next == '\n'.code) return builder.toString()
        if (next != '\r'.code) builder.append(next.toChar())
    }
}

/**
 * Nome con cui questo dispositivo si presenta all'altro capo: quello che
 * l'utente ha dato al telefono (Impostazioni > Info telefono > Nome
 * dispositivo), leggibile senza alcun permesso Bluetooth; se non impostato,
 * il modello come ripiego generico.
 *
 * Usato da entrambi i lati: dal joiner nell'HELLO, e dall'host nel
 * [GroupPauseBtMessage.LobbyInfo]. Nell'host **non** si può leggere
 * `adapter.name`, che durante la lobby vale "CalmOtter-<tag>" perché è stato
 * riscritto apposta per farsi riconoscere nel discovery.
 */
fun localBluetoothDisplayName(context: Context): String =
    Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
        ?: Build.MODEL
        ?: "Calm Otter"

private const val UNLOCK_PREFIX = "UNLOCK:"

/**
 * Payload NFC con cui l'host rilascia chi era nella sua stessa pausa: il
 * `groupTag` della sessione condivisa, che entrambi i dispositivi conoscono
 * perché è dentro la ricetta che hanno decodificato.
 *
 * Il tag **non è un segreto** — viaggia nel QR di Fase 1 — e non è
 * l'autorizzazione. L'autorizzazione è fisica: i due telefoni devono
 * toccarsi (NFC, pochi centimetri) e l'host deve aver deliberatamente
 * offerto il rilascio. Il tag serve solo a non liberare qualcuno che stava
 * facendo un'altra pausa.
 */
fun groupPauseUnlockToken(groupTag: Int): String = UNLOCK_PREFIX + groupTag

/** `groupTag` rilasciato, oppure null se il payload non è un rilascio valido. */
fun parseGroupPauseUnlockToken(payload: String): Int? =
    if (payload.startsWith(UNLOCK_PREFIX)) payload.removePrefix(UNLOCK_PREFIX).toIntOrNull() else null
