package com.calmotter.app.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import com.calmotter.app.bluetooth.localBluetoothDisplayName
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
        val command = commandApdu ?: return STATUS_NOT_FOUND
        // "Avvicina i telefoni" (specs/nfc-quick-together/): la proposta di A.
        if (QuickTogetherProtocol.isPropose(command)) return onPropose(command)

        val marker = pendingMarker
        return if (marker != null) {
            // L'altro telefono ha appena letto il marker: segnale diretto e
            // affidabile di "un tap è appena avvenuto", indipendente dalla
            // connessione Bluetooth che segue (vedi onTapRead in
            // GroupPauseBluetoothLobbyHostScreen.kt e
            // specs/group-pause/design.md).
            onTapRead?.invoke()
            marker.toByteArray(StandardCharsets.UTF_8) + STATUS_OK
        } else {
            // Nessuna lobby né rilascio in corso: questo telefono è la "carta"
            // di "Avvicina i telefoni", anche con l'app chiusa.
            QuickTogetherProtocol.encodeHello(
                QuickTogetherInbox.state(applicationContext),
                localBluetoothDisplayName(applicationContext),
            )
        }
    }

    private fun onPropose(command: ByteArray): ByteArray {
        val proposal = QuickTogetherProtocol.decodePropose(command) ?: return STATUS_NOT_FOUND
        if (proposal.version != QuickTogetherProtocol.VERSION) {
            return QuickTogetherProtocol.encodeResult(QuickTogetherProtocol.State.OTHER_VERSION)
        }
        val state = QuickTogetherInbox.state(applicationContext)
        if (state == QuickTogetherProtocol.State.READY) {
            QuickTogetherInbox.deliver(applicationContext, proposal)
        }
        return QuickTogetherProtocol.encodeResult(state)
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

        // Chiamato dal thread NFC del sistema (non quello principale): chi si
        // registra qui deve postare sul main thread da sé, se serve. Gestito
        // dalla lobby host esattamente come pendingMarker — impostato
        // all'attivazione, azzerato alla disattivazione o all'uscita.
        @Volatile
        var onTapRead: (() -> Unit)? = null

        private val STATUS_OK = byteArrayOf(0x90.toByte(), 0x00)
        private val STATUS_NOT_FOUND = byteArrayOf(0x6A, 0x82.toByte())
    }
}
