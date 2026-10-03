package com.calmotter.app.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import java.io.IOException
import java.nio.charset.StandardCharsets

// SELECT AID: CLA INS P1 P2 Lc <AID da 7 byte, stesso di apduservice.xml> Le
private val SELECT_AID_APDU = byteArrayOf(
    0x00, 0xA4.toByte(), 0x04, 0x00, 0x07,
    0xF0.toByte(), 0x01, 0x02, 0x03, 0x04, 0x05, 0x06,
    0x00,
)

/**
 * Lato "reader" dello scambio NFC opzionale della lobby Bluetooth
 * (Fase 2, vedi specs/group-pause/design.md): `enableReaderMode`, non il
 * vecchio Android Beam/NDEF push — deprecato e inaffidabile da Android 10,
 * non una base solida per una funzionalità pensata come punta di diamante.
 *
 * La callback di `enableReaderMode` gira su un thread Binder, non sul thread
 * principale: [onMarkerRead] viene invocata da lì, e chi la usa (la
 * schermata Compose della lobby joiner) deve postarla sul thread principale
 * prima di toccare stato Compose — non è responsabilità di questa classe.
 */
class GroupPauseNfcReader(private val activity: Activity) {

    fun start(onMarkerRead: (String) -> Unit) {
        val adapter = NfcAdapter.getDefaultAdapter(activity) ?: return
        adapter.enableReaderMode(
            activity,
            { tag: Tag ->
                val isoDep = IsoDep.get(tag) ?: return@enableReaderMode
                try {
                    isoDep.connect()
                    val response = isoDep.transceive(SELECT_AID_APDU)
                    if (response.size > 2) {
                        val marker = String(response, 0, response.size - 2, StandardCharsets.UTF_8)
                        onMarkerRead(marker)
                    }
                } catch (e: IOException) {
                    // Tag allontanato durante lo scambio — l'utente può semplicemente riavvicinarlo.
                } finally {
                    runCatching { isoDep.close() }
                }
            },
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null,
        )
    }

    /**
     * "Avvicina i telefoni" (specs/nfc-quick-together/): al tocco legge lo
     * stato dell'altro telefono (HELLO) e, se è pronto, gli manda la
     * proposta ([proposal], letta al momento del tocco: durata e attività
     * possono cambiare finché si aspetta). [onOutcome] gira su un thread
     * Binder, come [start].
     */
    fun startQuickTogether(
        proposal: () -> QuickTogetherProtocol.Proposal,
        onOutcome: (QuickTogetherOutcome) -> Unit,
    ) {
        val adapter = NfcAdapter.getDefaultAdapter(activity) ?: return
        adapter.enableReaderMode(
            activity,
            { tag: Tag ->
                val isoDep = IsoDep.get(tag) ?: return@enableReaderMode
                try {
                    isoDep.connect()
                    isoDep.timeout = 2_000
                    val hello = QuickTogetherProtocol.decodeHello(isoDep.transceive(SELECT_AID_APDU))
                    when {
                        hello == null -> onOutcome(QuickTogetherOutcome.NotReady)
                        hello.version != QuickTogetherProtocol.VERSION ->
                            onOutcome(QuickTogetherOutcome.Refused(QuickTogetherProtocol.State.OTHER_VERSION, hello.name))
                        hello.state != QuickTogetherProtocol.State.READY ->
                            onOutcome(QuickTogetherOutcome.Refused(hello.state, hello.name))
                        else -> {
                            val p = proposal()
                            val result = QuickTogetherProtocol.decodeResult(
                                isoDep.transceive(QuickTogetherProtocol.encodePropose(p))
                            )
                            // L'inizio di A parte da adesso, come quello di B
                            // dalla ricezione: i due differiscono solo della
                            // latenza dello scambio.
                            val startAt = System.currentTimeMillis() + p.startDelayMillis
                            when (result) {
                                QuickTogetherProtocol.State.READY -> onOutcome(QuickTogetherOutcome.Accepted(hello.name, startAt))
                                null -> onOutcome(QuickTogetherOutcome.NotReady)
                                else -> onOutcome(QuickTogetherOutcome.Refused(result, hello.name))
                            }
                        }
                    }
                } catch (e: IOException) {
                    // Telefoni allontanati durante lo scambio: si riavvicinano.
                } finally {
                    runCatching { isoDep.close() }
                }
            },
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null,
        )
    }

    fun stop() {
        val adapter = NfcAdapter.getDefaultAdapter(activity) ?: return
        runCatching { adapter.disableReaderMode(activity) }
    }
}

/** Com'è andato un tocco di "Avvicina i telefoni", visto da chi propone. */
sealed class QuickTogetherOutcome {
    /** L'altro ha accettato: si parte insieme a [startAt]. */
    data class Accepted(val companion: String, val startAt: Long) : QuickTogetherOutcome()
    /** L'altro non può: già in pausa, configurazione da finire, altra versione. */
    data class Refused(val state: QuickTogetherProtocol.State, val companion: String) : QuickTogetherOutcome()
    /** L'altro telefono non ha risposto come Calm Otter (o era in un'altra pagina di Tempo insieme). */
    data object NotReady : QuickTogetherOutcome()
}
