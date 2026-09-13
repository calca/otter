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

    fun stop() {
        val adapter = NfcAdapter.getDefaultAdapter(activity) ?: return
        runCatching { adapter.disableReaderMode(activity) }
    }
}
