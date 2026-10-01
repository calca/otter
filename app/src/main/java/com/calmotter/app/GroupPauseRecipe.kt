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
    // Attività proposta dall'host (TogetherActivities), 0 = nessuna — vedi
    // specs/together-activity/.
    val activityId: Int = 0,
)

/**
 * Codifica: 10 byte grezzi (1 = versione del protocollo,
 * [GROUP_PAUSE_PROTOCOL_VERSION]; 4 = orario di inizio in secondi epoch, 1 =
 * minuti di durata, 2 = tag di gruppo casuale — puramente cosmetico, non
 * ha alcun ruolo di sicurezza — 1 = attività proposta, 1 = checksum XOR
 * dei 9 byte precedenti, per segnalare un errore di battitura
 * nell'inserimento manuale invece di accettare silenziosamente una ricetta
 * corrotta) in Base64 URL-safe senza padding: una stringa di 14 caratteri.
 * Un codice di un'altra versione viene riconosciuto come tale
 * ([RecipeDecodeResult.OtherVersion]) e chi lo legge vede "aggiornate", non
 * "codice non valido". Resta abbastanza corta da poter
 * essere anche digitata a mano come fallback quando la fotocamera non è
 * disponibile o comoda. Stesso identico valore usato sia come contenuto
 * del QR sia come codice manuale — un solo formato, non due.
 *
 * `java.util.Base64` (non `android.util.Base64`): disponibile da API 26,
 * lo stesso minSdk del progetto — a differenza della classe Android questa
 * funziona anche nei test JVM puri (`testDebugUnitTest`) senza bisogno di
 * Robolectric, coerente con "logica pura, nessun Context" di questo file.
 */
/**
 * Versione del protocollo della pausa di gruppo: primo byte della ricetta e
 * parte dell'HELLO Bluetooth. Va incrementata a ogni modifica di formato,
 * così due telefoni con versioni diverse dell'app si dicono "aggiornate"
 * invece di "codice non valido". 2 = ricetta con l'attività proposta.
 */
const val GROUP_PAUSE_PROTOCOL_VERSION = 2

private const val PAYLOAD_SIZE = 10
private const val CHECKSUM_INDEX = PAYLOAD_SIZE - 1
private val URL_ENCODER = Base64.getUrlEncoder().withoutPadding()
private val URL_DECODER = Base64.getUrlDecoder()

// Oltre questo margine un orario di inizio "nel futuro" è quasi certamente
// un codice corrotto piuttosto che un vero invito (l'app offre solo 1/2/5
// minuti di attesa in GroupPauseHostScreen) — rifiutato in decode(), non
// solo affidato al checksum che non copre tutti i pattern di corruzione.
private const val MAX_FUTURE_START_MILLIS = 30 * 60_000L

fun GroupPauseRecipe.encode(): String {
    require(durationMinutes in 1..255) { "durationMinutes must fit in one byte" }
    require(activityId in 0..255) { "activityId must fit in one byte" }
    val startAtEpochSec = (startAtEpochMillis / 1000L).toInt()
    val buffer = ByteBuffer.allocate(PAYLOAD_SIZE)
        .put(GROUP_PAUSE_PROTOCOL_VERSION.toByte())
        .putInt(startAtEpochSec)
        .put(durationMinutes.toByte())
        .putShort(groupTag.toShort())
        .put(activityId.toByte())
    val bytes = buffer.array()
    bytes[CHECKSUM_INDEX] = checksumOf(bytes)
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
fun decodeGroupPauseRecipe(code: String, now: Long = System.currentTimeMillis()): GroupPauseRecipe? =
    (decodeGroupPauseRecipeResult(code, now) as? RecipeDecodeResult.Ok)?.recipe

/** Esito della lettura di un codice: valido, di un'altra versione dell'app, o non valido. */
sealed class RecipeDecodeResult {
    data class Ok(val recipe: GroupPauseRecipe) : RecipeDecodeResult()
    /** Un codice ben formato ma di un'altra versione: va detto "aggiornate", non "non valido". */
    data object OtherVersion : RecipeDecodeResult()
    data object Invalid : RecipeDecodeResult()
}

fun decodeGroupPauseRecipeResult(code: String, now: Long = System.currentTimeMillis()): RecipeDecodeResult {
    val bytes = try {
        URL_DECODER.decode(code.trim())
    } catch (e: IllegalArgumentException) {
        return RecipeDecodeResult.Invalid
    }
    if (bytes.isEmpty()) return RecipeDecodeResult.Invalid
    // La versione si guarda prima di lunghezza e checksum: sono proprio
    // quelli a cambiare fra una versione e l'altra. Un codice del formato
    // senza versione (8-9 byte) cade qui anche lui, ed è giusto: viene da
    // un'altra versione dell'app.
    if (bytes.size != PAYLOAD_SIZE || bytes[0].toInt() != GROUP_PAUSE_PROTOCOL_VERSION) {
        return if (bytes.size in 8..16) RecipeDecodeResult.OtherVersion else RecipeDecodeResult.Invalid
    }
    if (bytes[CHECKSUM_INDEX] != checksumOf(bytes)) return RecipeDecodeResult.Invalid

    val buffer = ByteBuffer.wrap(bytes)
    buffer.get() // versione, già controllata
    val startAtEpochSec = buffer.int
    val durationMinutes = buffer.get().toInt() and 0xFF
    val groupTag = buffer.short.toInt()
    val activityId = buffer.get().toInt() and 0xFF
    val startAtEpochMillis = startAtEpochSec * 1000L

    if (durationMinutes <= 0) return RecipeDecodeResult.Invalid
    if (startAtEpochMillis <= now) return RecipeDecodeResult.Invalid
    if (startAtEpochMillis - now > MAX_FUTURE_START_MILLIS) return RecipeDecodeResult.Invalid
    // Un'attività che questo telefono non conosce non può essere mostrata.
    if (activityId != TogetherActivities.NONE && TogetherActivities.byId(activityId) == null) return RecipeDecodeResult.Invalid

    return RecipeDecodeResult.Ok(GroupPauseRecipe(durationMinutes, startAtEpochMillis, groupTag, activityId))
}

private fun checksumOf(bytes: ByteArray): Byte {
    var xor = 0
    for (i in 0 until CHECKSUM_INDEX) xor = xor xor bytes[i].toInt()
    return xor.toByte()
}
