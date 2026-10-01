package com.calmotter.app

import com.calmotter.app.bluetooth.parseGroupPauseUnlockToken

/**
 * Le decisioni della schermata di blocco, fuori dal composable: chi può
 * rilasciare chi, cosa succede a ogni modo di finire la pausa, avanzamento e
 * respiro. Prima vivevano dentro `BlockScreen` e nessuna era provabile —
 * per questo il passo di rilascio allo sblocco anticipato era rimasto "non
 * verificato". Qui sono funzioni normali, coperte da BlockSessionControllerTest;
 * la schermata disegna e chiama.
 *
 * Lo stato di gruppo si legge una volta, alla creazione: `endSession()`
 * azzera tag e ruolo, e le decisioni sul rilascio vanno prese con i valori
 * di quando la pausa era in corso.
 *
 * [nfcAvailable]: il telefono ha l'NFC e la schermata vive in un'Activity
 * (serve al lettore). Senza, nessuna delle due strade NFC ha senso.
 */
class BlockSessionController(
    private val session: SessionManager,
    private val slowExit: SlowExitManager,
    nfcAvailable: Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Durata totale della pausa, letta una volta: non cambia mentre è in corso. */
    val totalMillis: Long = session.totalMillis()

    /** Pausa respiro (specs/breathing-pause/): l'aspetto dipende solo dalla durata. */
    val isBreathing: Boolean = isBreathingPause(totalMillis)

    val groupTag: Int = session.groupTag()

    /** Attività proposta per la pausa di gruppo; 0 = nessuna (specs/together-activity/). */
    val groupActivityId: Int = session.groupActivityId()

    private val isGroup = session.isGroupSession()
    private val isHost = session.isGroupHost()
    private val hasCompanions = session.companions().isNotEmpty()

    /**
     * Chi si è unito a una pausa di gruppo può essere rilasciato dall'host
     * avvicinando i telefoni, invece di digitare una password che non conosce
     * (specs/group-pause/). L'host no: non ha nessuno da cui farsi rilasciare.
     */
    val canBeReleasedByNfc: Boolean = nfcAvailable && isGroup && !isHost && groupTag != 0

    /**
     * L'host che **esce prima della fine** può rilasciare chi è ancora in
     * pausa. Alla scadenza naturale no: gli altri hanno lo stesso conto alla
     * rovescia e finiscono da soli. Serve sapere con chi si era (companions):
     * dal percorso QR/codice non lo si sa, e non c'è nessuno da cercare.
     */
    val canReleaseOthers: Boolean = nfcAvailable && isGroup && isHost && groupTag != 0 && hasCompanions

    fun remainingMillis(): Long = session.remainingMillis()

    /** Quanto della pausa è passato, da 0 a 1, per l'anello di avanzamento. */
    fun progress(remainingMillis: Long = remainingMillis()): Float =
        if (totalMillis > 0) (1f - remainingMillis.toFloat() / totalMillis.toFloat()).coerceIn(0f, 1f) else 0f

    /** La pausa è arrivata in fondo: si chiude come completata. Mai il passo di rilascio. */
    fun finishNaturally() {
        session.endSession(completedNaturally = true)
    }

    /**
     * Password giusta: la pausa finisce prima del tempo.
     * @return true se prima di uscire va offerto il rilascio degli altri.
     */
    fun unlockWithPassword(): Boolean {
        session.endSession(reason = EndReason.PASSWORD)
        return canReleaseOthers
    }

    /**
     * Un telefono avvicinato ha letto un token di rilascio. Vale solo se è
     * quello di *questa* pausa: il tag evita di liberare chi stava facendo
     * un'altra pausa (non è un segreto, l'autorizzazione è la vicinanza).
     * @return true se la pausa è finita.
     */
    fun releaseByNfc(payload: String): Boolean {
        if (!canBeReleasedByNfc || parseGroupPauseUnlockToken(payload) != groupTag) return false
        session.endSession(reason = EndReason.NFC_RELEASE)
        return true
    }

    // --- Uscita lenta (specs/slow-exit/) -------------------------------------

    val slowExitAvailable: Boolean get() = slowExit.isEnabled()

    val slowExitWaitMinutes: Int get() = slowExit.waitMinutes()

    /** L'attesa in corso, se c'è (epoch ms; 0 = nessuna). Sopravvive all'uscita dalla schermata. */
    fun slowExitDeadline(): Long = session.slowExitDeadline()

    /** Avvia l'attesa e ne restituisce la scadenza. */
    fun startSlowExit(): Long {
        session.startSlowExit(slowExit.waitMinutes())
        return session.slowExitDeadline()
    }

    fun cancelSlowExit() = session.cancelSlowExit()

    /** Quanto dell'attesa è passato, da 0 a 1, per l'anello. */
    fun slowExitProgress(deadline: Long, now: Long = clock()): Float {
        val wait = slowExit.waitMinutes() * 60_000L
        return (1f - (deadline - now).toFloat() / wait).coerceIn(0f, 1f)
    }

    /**
     * L'attesa è finita: la pausa si chiude come "senza password"
     * (idempotente: l'allarme può esserci arrivato prima).
     * @return true se prima di uscire va offerto il rilascio degli altri —
     * un host che esce così lascia gli altri in pausa come con la password.
     */
    fun finishSlowExit(): Boolean {
        session.endSession(reason = EndReason.SLOW_EXIT)
        return canReleaseOthers
    }
}
