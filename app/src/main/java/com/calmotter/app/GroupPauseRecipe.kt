package com.calmotter.app

import java.nio.ByteBuffer
import java.util.Base64

/**
 * La "ricetta" scambiata tra chi crea una pausa di gruppo e chi si unisce —
 * via QR code o codice manuale, vedi specs/group-pause/design.md per
 * l'architettura "handshake poi autonomia": una volta letta questa ricetta,
 * ogni telefono pianifica il proprio blocco in autonomia, nessuna
 * connessione resta viva dopo lo scambio (coerente col vincolo "niente
 * rete esterna/backend" del progetto — QR/codice sono scambi locali
 * one-shot, non un canale persistente).
 *
 * NON contiene i nomi dei partecipanti né un elenco di chi si è unito:
 * senza un canale live, chi crea la pausa non può sapere chi altro l'ha
 * letta — vedi design.md, sezione "Deferred" per la versione con lobby
 * live via Bluetooth/NFC che risolverebbe questo limite.
 */
data class GroupPauseRecipe(
    val durationMinutes: Int,
    val startAtEpochMillis: Long,
    val groupTag: Int,
)

/**
 * Codifica: 8 byte grezzi (4 = orario di inizio in secondi epoch, 1 =
 * minuti di durata, 2 = tag di gruppo casuale — puramente cosmetico, non
 * ha alcun ruolo di sicurezza — 1 = checksum XOR dei 7 byte precedenti, per
 * segnalare un errore di battitura nell'inserimento manuale invece di
 * accettare silenziosamente una ricetta corrotta) in Base64 URL-safe senza
 * padding: una stringa di circa 11 caratteri, abbastanza corta da poter
 * essere anche digitata a mano come fallback quando la fotocamera non è
 * disponibile o comoda. Stesso identico valore usato sia come contenuto
 * del QR sia come codice manuale — un solo formato, non due.
 *
 * `java.util.Base64` (non `android.util.Base64`): disponibile da API 26,
 * lo stesso minSdk del progetto — a differenza della classe Android questa
 * funziona anche nei test JVM puri (`testDebugUnitTest`) senza bisogno di
 * Robolectric, coerente con "logica pura, nessun Context" di questo file.
 */
private const val PAYLOAD_SIZE = 8
private val URL_ENCODER = Base64.getUrlEncoder().withoutPadding()
private val URL_DECODER = Base64.getUrlDecoder()

// Oltre questo margine un orario di inizio "nel futuro" è quasi certamente
// un codice corrotto piuttosto che un vero invito (l'app offre solo 1/2/5
// minuti di attesa in GroupPauseHostScreen) — rifiutato in decode(), non
// solo affidato al checksum che non copre tutti i pattern di corruzione.
private const val MAX_FUTURE_START_MILLIS = 30 * 60_000L

fun GroupPauseRecipe.encode(): String {
    require(durationMinutes in 1..255) { "durationMinutes must fit in one byte" }
    val startAtEpochSec = (startAtEpochMillis / 1000L).toInt()
    val buffer = ByteBuffer.allocate(PAYLOAD_SIZE)
        .putInt(startAtEpochSec)
        .put(durationMinutes.toByte())
        .putShort(groupTag.toShort())
    val bytes = buffer.array()
    bytes[7] = checksumOf(bytes)
    return URL_ENCODER.encodeToString(bytes)
}

/**
 * Ritorna `null` per qualunque codice non valido — corrotto (checksum),
 * malformato (lunghezza/Base64 sbagliati), già iniziato, o con un orario
 * d'inizio troppo lontano nel futuro per essere plausibile — invece di
 * lanciare, così i chiamanti (GroupPauseJoinScreen) possono limitarsi a un
 * unico messaggio di errore generico senza distinguere la causa esatta:
 * per chi si unisce non farebbe differenza saperla.
 */
fun decodeGroupPauseRecipe(code: String, now: Long = System.currentTimeMillis()): GroupPauseRecipe? {
    val bytes = try {
        URL_DECODER.decode(code.trim())
    } catch (e: IllegalArgumentException) {
        return null
    }
    if (bytes.size != PAYLOAD_SIZE) return null
    if (bytes[7] != checksumOf(bytes)) return null

    val buffer = ByteBuffer.wrap(bytes)
    val startAtEpochSec = buffer.int
    val durationMinutes = buffer.get().toInt() and 0xFF
    val groupTag = buffer.short.toInt()
    val startAtEpochMillis = startAtEpochSec * 1000L

    if (durationMinutes <= 0) return null
    if (startAtEpochMillis <= now) return null
    if (startAtEpochMillis - now > MAX_FUTURE_START_MILLIS) return null

    return GroupPauseRecipe(durationMinutes, startAtEpochMillis, groupTag)
}

private fun checksumOf(bytes: ByteArray): Byte {
    var xor = 0
    for (i in 0 until 7) xor = xor xor bytes[i].toInt()
    return xor.toByte()
}
