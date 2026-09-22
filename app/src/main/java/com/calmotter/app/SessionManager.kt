package com.calmotter.app

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.VisibleForTesting

/**
 * Gestisce lo stato "sessione di pausa attiva/non attiva", la sua durata e,
 * in parallelo, attiva/disattiva la modalità Non disturbare lasciando
 * passare solo le chiamate.
 */
class SessionManager private constructor(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Vero se la sessione è attiva. Se il tempo impostato è già scaduto
     * (rete di sicurezza nel caso l'allarme di sistema non sia scattato),
     * la sessione viene chiusa automaticamente qui.
     */
    fun isSessionActive(): Boolean {
        if (!prefs.getBoolean(KEY_ACTIVE, false)) return false
        if (System.currentTimeMillis() >= prefs.getLong(KEY_END_TIME, 0L)) {
            endSession(completedNaturally = true)
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
    ) {
        val now = System.currentTimeMillis()
        val endTime = now + durationMinutes * 60_000L
        prefs.edit()
            .putBoolean(KEY_ACTIVE, true)
            .putLong(KEY_START_TIME, now)
            .putLong(KEY_END_TIME, endTime)
            .putInt(KEY_PLANNED_MINUTES, durationMinutes)
            .putBoolean(KEY_IS_GROUP, isGroupSession)
            .putString(KEY_COMPANIONS, companions.filter { it.isNotBlank() }.joinToString("\n"))
            .putInt(KEY_GROUP_TAG, groupTag)
            .putBoolean(KEY_IS_HOST, isHost)
            .apply()
        captureDndForRestore()
        applyPauseDnd()
        scheduleAutoExpiry(endTime)
        SessionForegroundService.start(context)
        PauseWidgetProvider.saveLastDuration(context, durationMinutes)
        PauseWidgetProvider.updateAllWidgets(context)
    }

    /** Vero se la sessione attiva (o appena terminata) era una pausa di gruppo. */
    fun isGroupSession(): Boolean = prefs.getBoolean(KEY_IS_GROUP, false)

    /** Tag della pausa condivisa in corso; 0 se non è di gruppo o non lo si conosce. */
    fun groupTag(): Int = prefs.getInt(KEY_GROUP_TAG, 0)

    /** true se è questo dispositivo ad aver convocato la pausa condivisa. */
    fun isGroupHost(): Boolean = prefs.getBoolean(KEY_IS_HOST, false)

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
     *
     * Idempotente: se la sessione è già chiusa, non fa nulla. Senza questa
     * guardia una pausa scaduta naturalmente veniva registrata due volte —
     * sia SessionExpiryReceiver (l'allarme di sistema) sia il loop del
     * conto alla rovescia di BlockScreen chiamano endSession() alla
     * scadenza, indipendentemente l'uno dall'altro, e avere lo schermo di
     * blocco aperto proprio quando scade è il caso comune, non un caso
     * limite. Segnalato in TODO.md ("1.1").
     */
    fun endSession(completedNaturally: Boolean = false) {
        if (!prefs.getBoolean(KEY_ACTIVE, false)) return

        val startTime = prefs.getLong(KEY_START_TIME, 0L)
        val plannedMinutes = prefs.getInt(KEY_PLANNED_MINUTES, 0)
        val effectiveMinutes = ((System.currentTimeMillis() - startTime) / 60_000L)
            .toInt().coerceAtLeast(0)

        if (startTime > 0 && plannedMinutes > 0) {
            SessionHistoryManager.getInstance(context).add(
                SessionRecord(
                    startTimeMs        = startTime,
                    plannedMinutes     = plannedMinutes,
                    effectiveMinutes   = effectiveMinutes,
                    completedNaturally = completedNaturally,
                    isGroupSession     = prefs.getBoolean(KEY_IS_GROUP, false),
                    companions         = prefs.getString(KEY_COMPANIONS, "").orEmpty(),
                )
            )
        }

        prefs.edit()
            .putBoolean(KEY_ACTIVE, false)
            .remove(KEY_START_TIME)
            .remove(KEY_PLANNED_MINUTES)
            .remove(KEY_COMPANIONS)
            .remove(KEY_GROUP_TAG)
            .remove(KEY_IS_HOST)
            .remove(KEY_END_TIME)
            .apply()
        restoreDnd()
        cancelAutoExpiry()
        SessionForegroundService.stop(context)
        PauseWidgetProvider.updateAllWidgets(context)
    }

    /**
     * Riapplica DND, allarme e service dopo un riavvio.
     *
     * Non richiama [captureDndForRestore]: la policy "di prima" è già stata
     * catturata da [startSession] quando la pausa è iniziata, prima del
     * riavvio. Catturarla di nuovo qui sovrascriverebbe quel valore con la
     * policy "silenziosa" impostata dalla pausa stessa — quella da
     * ripristinare a fine pausa, non quella da ricordare.
     */
    fun reapplyAfterBoot() {
        val endTime = prefs.getLong(KEY_END_TIME, 0L)
        applyPauseDnd()
        scheduleAutoExpiry(endTime)
        SessionForegroundService.start(context)
    }

    /**
     * Programma una scadenza automatica via AlarmManager: alla fine del tempo
     * scelto, la sessione termina (DND disattivato) anche se l'utente non
     * sta interagendo con l'app in quel momento. Usiamo un allarme "inesatto"
     * (nessun permesso aggiuntivo richiesto): può avere qualche minuto di
     * scarto in Doze, accettabile per questo caso d'uso.
     */
    private fun scheduleAutoExpiry(endTime: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endTime, expiryPendingIntent())
    }

    private fun cancelAutoExpiry() {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(expiryPendingIntent())
    }

    private fun expiryPendingIntent(): PendingIntent {
        val intent = Intent(context, SessionExpiryReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            EXPIRY_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Salva la policy DND dell'utente *prima* che la pausa la sovrascriva,
     * così [restoreDnd] può restituirla invece di forzare sempre "tutte le
     * notifiche" a fine pausa — chi aveva già il DND acceso prima di
     * avviare la pausa se lo ritrovava spento alla fine. Segnalato in
     * TODO.md ("1.2").
     *
     * Chiamata solo da [startSession], non da [reapplyAfterBoot]: dopo un
     * riavvio la policy "di prima" è già salvata da quando la pausa è
     * iniziata — catturarla di nuovo salverebbe la policy silenziosa della
     * pausa stessa al posto di quella originale dell'utente.
     */
    private fun captureDndForRestore() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) {
            prefs.edit().putBoolean(KEY_PREV_DND_CAPTURED, false).apply()
            return
        }
        val policy = nm.notificationPolicy
        prefs.edit()
            .putBoolean(KEY_PREV_DND_CAPTURED, true)
            .putInt(KEY_PREV_FILTER, nm.currentInterruptionFilter)
            .putInt(KEY_PREV_POLICY_CATEGORIES, policy.priorityCategories)
            .putInt(KEY_PREV_POLICY_CALL_SENDERS, policy.priorityCallSenders)
            .putInt(KEY_PREV_POLICY_MESSAGE_SENDERS, policy.priorityMessageSenders)
            .putInt(KEY_PREV_POLICY_SUPPRESSED_EFFECTS, policy.suppressedVisualEffects)
            .apply()
    }

    /**
     * Silenzia le notifiche, non il telefono: passano le chiamate (da
     * qualunque numero), le sveglie e l'audio dei media.
     *
     * Sveglie e media non sono distrazioni in arrivo, che è ciò che questa
     * pausa esiste per togliere: sono rispettivamente un impegno già preso e
     * qualcosa che stai già ascoltando. Una pausa può durare 4 ore, e
     * zittire una sveglia — o troncare la musica a metà canzone nell'istante
     * esatto in cui si tocca l'otter — sono danni che stanno fuori dal
     * patto. Entrambi segnalati.
     *
     * Vanno concesse esplicitamente solo da Android 9 (API 28): è lì che
     * sono comparse PRIORITY_CATEGORY_ALARMS/_MEDIA, insieme alla
     * possibilità stessa per DND di silenziare quei due canali. Sotto quella
     * versione il filtro "solo priorità" non li toccava, quindi non c'è
     * nulla da aggiungere (e le costanti non esisterebbero).
     *
     * Nota: questo riguarda solo l'audio. *Aprire* Spotify durante una
     * pausa resta una decisione separata, che si prende dalla whitelist
     * (vedi AllowedAppsManager) — qui si evita solo che l'app consentita
     * suoni a vuoto.
     *
     * Da API 28 nasconde anche i pallini (dots), la tendina (pull-down) e
     * la barra di stato (status bar).
     */
    private fun applyPauseDnd() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) return // permesso non concesso: si ignora silenziosamente

        val priorityCategories: Int
        val suppressedEffects: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            priorityCategories = NotificationManager.Policy.PRIORITY_CATEGORY_CALLS or
                    NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS or
                    NotificationManager.Policy.PRIORITY_CATEGORY_MEDIA
            suppressedEffects = NotificationManager.Policy.SUPPRESSED_EFFECT_BADGE or
                    NotificationManager.Policy.SUPPRESSED_EFFECT_NOTIFICATION_LIST or
                    NotificationManager.Policy.SUPPRESSED_EFFECT_STATUS_BAR
        } else {
            priorityCategories = NotificationManager.Policy.PRIORITY_CATEGORY_CALLS
            suppressedEffects = 0
        }

        nm.setNotificationPolicy(
            NotificationManager.Policy(
                priorityCategories,
                NotificationManager.Policy.PRIORITY_SENDERS_ANY,
                0,
                suppressedEffects
            )
        )
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
    }

    /**
     * Ripristina la policy DND catturata da [captureDndForRestore]. Se non
     * ce n'è una (permesso non concesso quando la pausa è iniziata, o dati
     * di una versione precedente a questa correzione senza nulla di
     * salvato), ricade sul comportamento precedente — tutte le notifiche —
     * che resta comunque più sicuro di lasciare attivo il filtro
     * "solo priorità" della pausa indefinitamente.
     */
    private fun restoreDnd() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) return

        if (!prefs.getBoolean(KEY_PREV_DND_CAPTURED, false)) {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            return
        }

        nm.setNotificationPolicy(
            NotificationManager.Policy(
                prefs.getInt(KEY_PREV_POLICY_CATEGORIES, 0),
                prefs.getInt(KEY_PREV_POLICY_CALL_SENDERS, NotificationManager.Policy.PRIORITY_SENDERS_ANY),
                prefs.getInt(KEY_PREV_POLICY_MESSAGE_SENDERS, NotificationManager.Policy.PRIORITY_SENDERS_ANY),
                prefs.getInt(KEY_PREV_POLICY_SUPPRESSED_EFFECTS, 0),
            )
        )
        nm.setInterruptionFilter(prefs.getInt(KEY_PREV_FILTER, NotificationManager.INTERRUPTION_FILTER_ALL))

        prefs.edit()
            .remove(KEY_PREV_DND_CAPTURED)
            .remove(KEY_PREV_FILTER)
            .remove(KEY_PREV_POLICY_CATEGORIES)
            .remove(KEY_PREV_POLICY_CALL_SENDERS)
            .remove(KEY_PREV_POLICY_MESSAGE_SENDERS)
            .remove(KEY_PREV_POLICY_SUPPRESSED_EFFECTS)
            .apply()
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
        // Policy DND dell'utente catturata da captureDndForRestore() prima
        // di applicare quella "silenziosa" della pausa — vedi restoreDnd().
        private const val KEY_PREV_DND_CAPTURED = "session_prev_dnd_captured"
        private const val KEY_PREV_FILTER = "session_prev_dnd_filter"
        private const val KEY_PREV_POLICY_CATEGORIES = "session_prev_dnd_categories"
        private const val KEY_PREV_POLICY_CALL_SENDERS = "session_prev_dnd_call_senders"
        private const val KEY_PREV_POLICY_MESSAGE_SENDERS = "session_prev_dnd_message_senders"
        private const val KEY_PREV_POLICY_SUPPRESSED_EFFECTS = "session_prev_dnd_suppressed_effects"
        private const val EXPIRY_REQUEST_CODE = 1001

        @Volatile private var instance: SessionManager? = null

        fun getInstance(context: Context): SessionManager =
            instance ?: synchronized(this) {
                instance ?: SessionManager(context.applicationContext).also { instance = it }
            }

        @VisibleForTesting
        internal fun resetInstanceForTests() {
            instance = null
        }
    }
}
