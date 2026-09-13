package com.calmotter.app.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import java.nio.charset.StandardCharsets

/**
 * Lato "carta" dello scambio NFC opzionale della lobby Bluetooth dal vivo
 * (Fase 2, vedi specs/group-pause/design.md — "perché l'NFC scambia un nome,
 * non un indirizzo MAC"): quando un altro telefono in modalità reader
 * ([com.calmotter.app.nfc.GroupPauseNfcReader]) seleziona l'AID dichiarato
 * in `res/xml/apduservice.xml`, questo servizio risponde con il nome che
 * l'host sta annunciando come proprio nome Bluetooth
 * (`groupPauseLobbyNameMarker`), permettendo al joiner di collegarsi senza
 * dover scegliere manualmente il dispositivo giusto da un elenco.
 *
 * Semplificazione deliberata: non viene ispezionato il comando APDU in
 * arrivo (accettato qualunque esso sia una volta che l'OS ha instradato la
 * selezione dell'AID verso questo servizio) — un solo comando/risposta
 * esiste in questo protocollo, non serve altro.
 */
class GroupPauseHceService : HostApduService() {

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        val marker = pendingMarker
        return if (marker != null) {
            marker.toByteArray(StandardCharsets.UTF_8) + STATUS_OK
        } else {
            STATUS_NOT_FOUND
        }
    }

    override fun onDeactivated(reason: Int) {
        // Nessuno stato da ripulire: pendingMarker è gestito dalla schermata
        // della lobby host (impostato all'attivazione del toggle NFC,
        // azzerato alla disattivazione o all'uscita dalla lobby).
    }

    companion object {
        // Un solo campo statico basta: un solo processo, un solo host attivo
        // alla volta — nessuna concorrenza reale da gestire qui.
        @Volatile
        var pendingMarker: String? = null

        private val STATUS_OK = byteArrayOf(0x90.toByte(), 0x00)
        private val STATUS_NOT_FOUND = byteArrayOf(0x6A, 0x82.toByte())
    }
}
