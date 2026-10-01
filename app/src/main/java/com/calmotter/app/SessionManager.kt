package com.calmotter.app

import android.content.Context
import androidx.core.content.edit

/**
 * Gestisce lo stato "sessione di pausa attiva/non attiva", la sua durata e,
 * in parallelo, attiva/disattiva la modalità Non disturbare lasciando
 * passare solo le chiamate.
 */
class SessionManager internal constructor(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    // I due pezzi che non sono "stato della pausa": Non disturbare e gli
    // allarmi di fine. SessionManager li coordina, non li implementa.
    private val dnd = PauseDnd(context, prefs)
    private val alarms = SessionAlarms(context)

    /**
     * Vero se la sessione è attiva. Se il tempo impostato è già scaduto
     * (rete di sicurezza nel caso l'allarme di sistema non sia scattato,
     * inevitabilmente inesatto — vedi [SessionAlarms.scheduleExpiry]), la sessione
     * viene chiusa automaticamente qui. `markBackgroundSummary = true`:
     * chi chiama questo getter sta *chiedendo* se una pausa è attiva, non
     * guardando dal vivo un conto alla rovescia arrivare a zero (quello è
     * BlockScreen, che chiama endSession() direttamente, non passa di qui)
     * — se è questa rete di sicurezza a scoprire la scadenza, per
     * definizione l'utente non c'era. Vedi endSession().
     */
    fun isSessionActive(): Boolean {
        if (!prefs.getBoolean(KEY_ACTIVE, false)) return false
        val now = System.currentTimeMillis()
        if (now >= prefs.getLong(KEY_END_TIME, 0L)) {
            endSession(completedNaturally = true, markBackgroundSummary = true)
            return false
        }
        // Stessa rete di sicurezza per l'uscita lenta: se l'allarme non è
        // scattato, la scadenza dell'attesa si scopre qui.
        val slowExit = prefs.getLong(KEY_SLOW_EXIT_DEADLINE, 0L)
        if (slowExit in 1..now) {
            endSession(reason = EndReason.SLOW_EXIT)
            return false
        }
        return true
    }

    /** Millisecondi rimanenti alla fine della pausa (0 se non attiva/scaduta). */
    fun remainingMillis(): Long {
        val end = prefs.getLong(KEY_END_TIME, 0L)
        return (end - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    /**
     * [isGroupSession]: true quando questa sessione è partita da
     * GroupPauseHostActivity/GroupPauseJoinActivity (pausa di gruppo, vedi
     * specs/group-pause/) invece che dal tap sull'otter in Home — letto poi
     * da [isGroupSession] per mostrare un indicatore in BlockScreen e
     * riportato in [SessionRecord] per il tag in Cronologia. Non cambia in
     * alcun modo il funzionamento della sessione stessa (durata, DND,
     * sblocco): è solo un'etichetta.
     */
    /**
     * [companions] sono i nomi raccolti nella lobby dal vivo — vuoto per una
     * pausa in solitaria e anche per una di gruppo nata dal percorso QR/codice,
     * che non ha modo di conoscerli. Persistiti separati da "\n" perché un
     * nome dispositivo può contenere una virgola, non un a capo (il protocollo
     * Bluetooth li toglie comunque, vedi GroupPauseBluetoothProtocol).
     */
    fun startSession(
        durationMinutes: Int,
        isGroupSession: Boolean = false,
        companions: List<String> = emptyList(),
        // Identificano la pausa condivisa e il ruolo di questo dispositivo:
        // servono al rilascio via NFC (vedi groupPauseUnlockToken) — solo
        // l'host può rilasciare, e solo chi era in *quella* pausa.
        groupTag: Int = 0,
        isHost: Boolean = false,
        // false solo per le pause che nessuno ha scelto in quel momento (le
        // pause programmate): non devono cambiare la durata che la Home
        // propone — vedi [LastDuration].
        rememberDuration: Boolean = true,
        // Attività proposta dall'host di una pausa di gruppo (vedi
        // TogetherActivities); 0 = nessuna.
        activityId: Int = 0,
        // Profilo di app consentite per questa pausa (vedi
        // AllowedAppsManager); null = quello selezionato al momento.
        profileId: Int? = null,
    ) {
        val now = System.currentTimeMillis()
        val endTime = now + durationMinutes * 60_000L
        prefs.edit {
            putBoolean(KEY_ACTIVE, true)
            putLong(KEY_START_TIME, now)
            putLong(KEY_END_TIME, endTime)
            putInt(KEY_PLANNED_MINUTES, durationMinutes)
            putBoolean(KEY_IS_GROUP, isGroupSession)
            putString(KEY_COMPANIONS, companions.filter { it.isNotBlank() }.joinToString("\n"))
            putInt(KEY_GROUP_TAG, groupTag)
            putBoolean(KEY_IS_HOST, isHost)
            putInt(KEY_GROUP_ACTIVITY, activityId)
            putInt(KEY_PROFILE_ID, profileId ?: AllowedAppsManager.getInstance(context).selectedProfileId())
            remove(KEY_SLOW_EXIT_DEADLINE)
        }
        dnd.capture()
        dnd.apply()
        alarms.scheduleExpiry(endTime)
        SessionForegroundService.start(context)
        if (rememberDuration) LastDuration.save(context, durationMinutes)
        PauseWidgetProvider.updateAllWidgets(context)
        PauseTileService.requestRefresh(context)
    }

    /** Vero se la sessione attiva (o appena terminata) era una pausa di gruppo. */
    fun isGroupSession(): Boolean = prefs.getBoolean(KEY_IS_GROUP, false)

    /** Tag della pausa condivisa in corso; 0 se non è di gruppo o non lo si conosce. */
    fun groupTag(): Int = prefs.getInt(KEY_GROUP_TAG, 0)

    /** true se è questo dispositivo ad aver convocato la pausa condivisa. */
    fun isGroupHost(): Boolean = prefs.getBoolean(KEY_IS_HOST, false)

    /** Attività proposta per la pausa di gruppo in corso; 0 = nessuna. */
    fun groupActivityId(): Int = prefs.getInt(KEY_GROUP_ACTIVITY, 0)

    /** Profilo di app consentite della pausa in corso (vedi AllowedAppsManager). */
    fun sessionProfileId(): Int = prefs.getInt(KEY_PROFILE_ID, AllowedAppsManager.DEFAULT_PROFILE_ID)

    /** Nomi di chi condivide la pausa in corso; vuoto se non se ne conoscono. */
    fun companions(): List<String> =
        prefs.getString(KEY_COMPANIONS, "").orEmpty()
            .split("\n")
            .filter { it.isNotBlank() }

    /** Millisecondi totali della sessione (0 se non disponibile). */
    fun totalMillis(): Long {
        val start = prefs.getLong(KEY_START_TIME, 0L)
        val end = prefs.getLong(KEY_END_TIME, 0L)
        return (end - start).coerceAtLeast(0L)
    }

    /**
     * Termina la sessione e la registra nella cronologia.
     * @param completedNaturally true se scaduta per timer, false se sbloccata con password
     * @param markBackgroundSummary true solo dalla chiamata di
     * [SessionExpiryReceiver]: segna che è stato l'allarme di sistema a
     * chiudere la pausa, non il conto alla rovescia di [ui.screens.BlockScreen]
     * — l'unico caso in cui l'utente non ha visto nessuna schermata di fine
     * pausa dal vivo. Grazie alla guardia di idempotenza qui sotto, se
     * BlockScreen ha già chiuso la sessione (l'ha vista lui) questa seconda
     * chiamata è un no-op e il segnale non viene mai scritto — la Home
     * mostra il riepilogo una tantum solo quando serve davvero. Letto da
     * [consumePendingBackgroundSummary].
     *
     * Idempotente: se la sessione è già chiusa, non fa nulla. Senza questa
     * guardia una pausa scaduta naturalmente veniva registrata due volte —
     * sia SessionExpiryReceiver (l'allarme di sistema) sia il loop del
     * conto alla rovescia di BlockScreen chiamano endSession() alla
     * scadenza, indipendentemente l'uno dall'altro, e avere lo schermo di
     * blocco aperto proprio quando scade è il caso comune, non un caso
     * limite. Segnalato in TODO.md ("1.1").
     */
    fun endSession(
        completedNaturally: Boolean = false,
        markBackgroundSummary: Boolean = false,
        // Come è finita (EndReason): se non indicato, si ricava da
        // completedNaturally — da sé o con la password.
        reason: String = if (completedNaturally) EndReason.NATURAL else EndReason.PASSWORD,
    ) {
        if (!prefs.getBoolean(KEY_ACTIVE, false)) return

        val startTime = prefs.getLong(KEY_START_TIME, 0L)
        val plannedMinutes = prefs.getInt(KEY_PLANNED_MINUTES, 0)
        val effectiveMinutes = ((System.currentTimeMillis() - startTime) / 60_000L)
            .toInt().coerceAtLeast(0)

        var recordId = 0L
        if (startTime > 0 && plannedMinutes > 0) {
            recordId = SessionHistoryManager.getInstance(context).add(
                SessionRecord(
                    startTimeMs        = startTime,
                    plannedMinutes     = plannedMinutes,
                    effectiveMinutes   = effectiveMinutes,
                    completedNaturally = completedNaturally,
                    isGroupSession     = prefs.getBoolean(KEY_IS_GROUP, false),
                    companions         = prefs.getString(KEY_COMPANIONS, "").orEmpty(),
                    activityId         = prefs.getInt(KEY_GROUP_ACTIVITY, 0),
                    endReason          = reason,
                )
            )
        }
        val hadSlowExit = prefs.getLong(KEY_SLOW_EXIT_DEADLINE, 0L) != 0L

        prefs.edit {
            putBoolean(KEY_ACTIVE, false)
            remove(KEY_START_TIME)
            remove(KEY_PLANNED_MINUTES)
            remove(KEY_COMPANIONS)
            remove(KEY_GROUP_TAG)
            remove(KEY_IS_HOST)
            remove(KEY_END_TIME)
            remove(KEY_GROUP_ACTIVITY)
            remove(KEY_PROFILE_ID)
            remove(KEY_SLOW_EXIT_DEADLINE)
            // Solo una pausa arrivata in fondo merita il momento di chiusura
            // (specs/closing-moment/): mai dopo un'uscita anticipata. Una
            // nuova pausa completata sostituisce quella non ancora risposta.
            if (completedNaturally && recordId > 0) putLong(KEY_PENDING_REFLECTION_ID, recordId)
            if (markBackgroundSummary) {
                putLong(KEY_PENDING_SUMMARY_END, System.currentTimeMillis())
                putInt(KEY_PENDING_SUMMARY_MINUTES, effectiveMinutes)
            }
        }
        dnd.restore()
        alarms.cancelExpiry()
        if (hadSlowExit) alarms.cancelSlowExit()
        SessionForegroundService.stop(context)
        PauseWidgetProvider.updateAllWidgets(context)
        PauseTileService.requestRefresh(context)
        // La nota della domenica, se era stata rimandata per questa pausa.
        WeeklySummary.onSessionEnded(context)
    }

    /** Id della sessione completata ancora senza risposta al momento di chiusura; 0 = nessuna. */
    fun pendingReflectionId(): Long = prefs.getLong(KEY_PENDING_REFLECTION_ID, 0L)

    /** Il momento di chiusura è stato risposto o saltato: non va più chiesto. */
    fun clearPendingReflection() {
        prefs.edit { remove(KEY_PENDING_REFLECTION_ID) }
    }

    // --- Uscita lenta (specs/slow-exit/) -------------------------------------

    /** Fine dell'attesa dell'uscita lenta in corso (epoch ms); 0 = nessuna attesa. */
    fun slowExitDeadline(): Long = prefs.getLong(KEY_SLOW_EXIT_DEADLINE, 0L)

    /**
     * Avvia l'attesa: allo scadere la pausa finisce come "senza password".
     * Persistita e affidata a un allarme come la fine naturale, così continua
     * anche se si esce dalla schermata, il telefono dorme o si riavvia.
     */
    fun startSlowExit(waitMinutes: Int) {
        if (!prefs.getBoolean(KEY_ACTIVE, false)) return
        val deadline = System.currentTimeMillis() + waitMinutes * 60_000L
        prefs.edit { putLong(KEY_SLOW_EXIT_DEADLINE, deadline) }
        alarms.scheduleSlowExit(deadline)
    }

    /** Annulla l'attesa: la pausa continua invariata; una nuova attesa riparte da zero. */
    fun cancelSlowExit() {
        prefs.edit { remove(KEY_SLOW_EXIT_DEADLINE) }
        alarms.cancelSlowExit()
    }

    /** Dettagli del riepilogo "pausa finita mentre eri via" — vedi [endSession]. */
    data class PendingBackgroundSummary(val endTimeMs: Long, val effectiveMinutes: Int)

    /**
     * Consuma (legge e cancella) il riepilogo in sospeso, se c'è: la Home lo
     * chiama una volta per apertura e mostra il risultato al posto della
     * riga streak/settimana finché non riparte una nuova pausa — la seconda
     * chiamata torna sempre null, così il riepilogo si vede una sola volta.
     */
    fun consumePendingBackgroundSummary(): PendingBackgroundSummary? {
        val endTimeMs = prefs.getLong(KEY_PENDING_SUMMARY_END, 0L)
        if (endTimeMs == 0L) return null
        val minutes = prefs.getInt(KEY_PENDING_SUMMARY_MINUTES, 0)
        prefs.edit {
            remove(KEY_PENDING_SUMMARY_END)
            remove(KEY_PENDING_SUMMARY_MINUTES)
        }
        return PendingBackgroundSummary(endTimeMs, minutes)
    }

    /**
     * Riapplica DND, allarme e service dopo un riavvio.
     *
     * Non richiama [PauseDnd.capture]: la policy "di prima" è già stata
     * catturata da [startSession] quando la pausa è iniziata, prima del
     * riavvio. Catturarla di nuovo qui sovrascriverebbe quel valore con la
     * policy "silenziosa" impostata dalla pausa stessa — quella da
     * ripristinare a fine pausa, non quella da ricordare.
     */
    fun reapplyAfterBoot() {
        val endTime = prefs.getLong(KEY_END_TIME, 0L)
        dnd.apply()
        alarms.scheduleExpiry(endTime)
        val slowExit = prefs.getLong(KEY_SLOW_EXIT_DEADLINE, 0L)
        if (slowExit > 0) alarms.scheduleSlowExit(slowExit)
        SessionForegroundService.start(context)
    }

    companion object {
        private const val PREFS_NAME = "calm_otter_session"
        private const val KEY_ACTIVE = "session_active"
        private const val KEY_COMPANIONS = "session_companions"
        private const val KEY_GROUP_TAG = "session_group_tag"
        private const val KEY_IS_HOST = "session_is_host"
        private const val KEY_START_TIME = "session_start_time"
        private const val KEY_END_TIME = "session_end_time"
        private const val KEY_PLANNED_MINUTES = "session_planned_minutes"
        private const val KEY_IS_GROUP = "session_is_group"
        // Riepilogo "una tantum" per la Home — vedi endSession()/
        // consumePendingBackgroundSummary(). Sopravvivono a endSession()
        // apposta: quella li scrive, solo consumePendingBackgroundSummary()
        // li cancella.
        private const val KEY_PENDING_SUMMARY_END = "session_pending_summary_end"
        private const val KEY_PENDING_SUMMARY_MINUTES = "session_pending_summary_minutes"
        private const val KEY_GROUP_ACTIVITY = "session_group_activity"
        private const val KEY_PROFILE_ID = "session_profile_id"
        private const val KEY_SLOW_EXIT_DEADLINE = "session_slow_exit_deadline"
        // Sopravvive a endSession() come il riepilogo una tantum: la pausa è
        // finita, la domanda "com'è andata?" resta in sospeso.
        private const val KEY_PENDING_REFLECTION_ID = "session_pending_reflection_id"

        /** L'istanza dell'app: vive in [AppGraph], una per Application (vedi CalmOtterApplication). */
        fun getInstance(context: Context): SessionManager = context.appGraph.sessionManager
    }
}
