package com.calmotter.app.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
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
private const val RECIPE_PREFIX = "RECIPE:"

sealed class GroupPauseBtMessage {
    data class Hello(val displayName: String) : GroupPauseBtMessage()
    data class Recipe(val code: String) : GroupPauseBtMessage()
}

fun formatHello(displayName: String): String = HELLO_PREFIX + sanitizeDisplayName(displayName)

fun formatRecipe(code: String): String = RECIPE_PREFIX + code

/** Ritorna `null` per una riga che non corrisponde a nessun messaggio noto. */
fun parseGroupPauseBtMessage(line: String): GroupPauseBtMessage? = when {
    line.startsWith(HELLO_PREFIX) -> GroupPauseBtMessage.Hello(line.removePrefix(HELLO_PREFIX))
    line.startsWith(RECIPE_PREFIX) -> GroupPauseBtMessage.Recipe(line.removePrefix(RECIPE_PREFIX))
    else -> null
}

// Un nome dispositivo non dovrebbe mai contenere newline, ma non ci si può
// fidare dell'input dell'altro estremo del socket: un newline incorporato
// spezzerebbe il framing a righe di questo protocollo.
private fun sanitizeDisplayName(name: String): String =
    name.replace('\n', ' ').replace('\r', ' ').trim().take(40)

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
