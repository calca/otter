package com.calmotter.app.nfc

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * Lo scambio NFC di "Avvicina i telefoni" (specs/nfc-quick-together/): un
 * tocco, due APDU, la ricetta della pausa portata direttamente dall'NFC,
 * senza Bluetooth. Pura, senza Android: provata da QuickTogetherProtocolTest.
 *
 * ```
 * A → B  SELECT AID            (lo stesso di apduservice.xml)
 * B → A  HELLO   "QT" v state nome
 * A → B  PROPOSE 80 10 00 00 Lc  v durata(2) attività tag(2) ritardo(2) nome
 * B → A  esito (1 byte) 90 00
 * ```
 *
 * L'inizio viaggia come **ritardo** dal momento in cui B riceve la proposta,
 * non come orario: ogni telefono lo somma al proprio orologio, quindi due
 * orologi un po' diversi non contano.
 */
object QuickTogetherProtocol {

    /** Versione dello scambio; a ogni cambio di formato, chi è diverso dice "aggiornate". */
    const val VERSION = 3

    /** Conto alla rovescia prima della partenza, uguale sui due telefoni. */
    const val START_DELAY_MILLIS = 5_000

    const val INS_PROPOSE: Byte = 0x10
    private const val CLA_PROPRIETARY: Byte = 0x80.toByte()
    private val MAGIC = byteArrayOf('Q'.code.toByte(), 'T'.code.toByte())
    private const val MAX_NAME_BYTES = 40

    val STATUS_OK = byteArrayOf(0x90.toByte(), 0x00)

    /** Cosa può fare B quando A si avvicina. */
    enum class State(val code: Int) {
        READY(0),
        IN_PAUSE(1),
        NOT_SET_UP(2),
        OTHER_VERSION(3);

        companion object {
            fun of(code: Int) = entries.firstOrNull { it.code == code }
        }
    }

    data class Hello(val version: Int, val state: State, val name: String)

    data class Proposal(
        val version: Int,
        val durationMinutes: Int,
        val activityId: Int,
        val groupTag: Int,
        val startDelayMillis: Int,
        val name: String,
    )

    // ── HELLO: B → A ───────────────────────────────────────────────────

    fun encodeHello(state: State, name: String): ByteArray =
        MAGIC + byteArrayOf(VERSION.toByte(), state.code.toByte()) + nameBytes(name) + STATUS_OK

    /** null se la risposta non è un HELLO (per esempio il marker della lobby Bluetooth). */
    fun decodeHello(response: ByteArray): Hello? {
        if (response.size < 6 || !response.endsWithOk()) return null
        if (response[0] != MAGIC[0] || response[1] != MAGIC[1]) return null
        val version = response[2].toInt() and 0xFF
        val state = State.of(response[3].toInt() and 0xFF) ?: return null
        val name = String(response, 4, response.size - 6, StandardCharsets.UTF_8)
        return Hello(version, state, name)
    }

    // ── PROPOSE: A → B ─────────────────────────────────────────────────

    fun encodePropose(p: Proposal): ByteArray {
        val name = nameBytes(p.name)
        val data = ByteBuffer.allocate(8 + name.size)
            .put(p.version.toByte())
            .putShort(p.durationMinutes.toShort())
            .put(p.activityId.toByte())
            .putShort(p.groupTag.toShort())
            .putShort(p.startDelayMillis.toShort())
            .put(name)
            .array()
        return byteArrayOf(CLA_PROPRIETARY, INS_PROPOSE, 0x00, 0x00, data.size.toByte()) + data
    }

    fun isPropose(command: ByteArray): Boolean =
        command.size >= 5 && command[0] == CLA_PROPRIETARY && command[1] == INS_PROPOSE

    /** null se il comando è malformato. */
    fun decodePropose(command: ByteArray): Proposal? {
        if (!isPropose(command)) return null
        val length = command[4].toInt() and 0xFF
        if (length < 8 || command.size < 5 + length) return null
        val b = ByteBuffer.wrap(command, 5, length)
        val version = b.get().toInt() and 0xFF
        val duration = b.short.toInt() and 0xFFFF
        val activity = b.get().toInt() and 0xFF
        val tag = b.short.toInt()
        val delay = b.short.toInt() and 0xFFFF
        val nameLength = length - 8
        val name = String(command, 5 + 8, nameLength, StandardCharsets.UTF_8)
        if (duration <= 0) return null
        return Proposal(version, duration, activity, tag, delay, name)
    }

    // ── Esito: B → A ───────────────────────────────────────────────────

    fun encodeResult(state: State): ByteArray = byteArrayOf(state.code.toByte()) + STATUS_OK

    fun decodeResult(response: ByteArray): State? {
        if (response.size != 3 || !response.endsWithOk()) return null
        return State.of(response[0].toInt() and 0xFF)
    }

    /** Il nome tagliato a [MAX_NAME_BYTES] byte senza spezzare un carattere. */
    private fun nameBytes(name: String): ByteArray {
        var n = name.trim()
        while (n.toByteArray(StandardCharsets.UTF_8).size > MAX_NAME_BYTES) n = n.dropLast(1)
        return n.toByteArray(StandardCharsets.UTF_8)
    }

    private fun ByteArray.endsWithOk() =
        size >= 2 && this[size - 2] == STATUS_OK[0] && this[size - 1] == STATUS_OK[1]
}
