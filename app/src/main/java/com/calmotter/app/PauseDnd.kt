package com.calmotter.app

import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.core.content.edit

/**
 * Non disturbare durante una pausa: cattura la policy dell'utente prima di
 * cambiarla, applica quella della pausa, la restituisce alla fine. Separata
 * da [SessionManager], che la coordina: è l'unico pezzo che parla con il
 * NotificationManager. Usa lo stesso file di preferenze della sessione e le
 * stesse chiavi di prima, così una pausa iniziata con la versione precedente
 * ritrova la policy da ripristinare.
 */
internal class PauseDnd(private val context: Context, private val prefs: SharedPreferences) {

    /**
     * Salva la policy DND dell'utente *prima* che la pausa la sovrascriva,
     * così [restore] può restituirla invece di forzare sempre "tutte le
     * notifiche" a fine pausa — chi aveva già il DND acceso prima di
     * avviare la pausa se lo ritrovava spento alla fine. Segnalato in
     * TODO.md ("1.2").
     *
     * Chiamata solo da SessionManager.startSession, non da reapplyAfterBoot: dopo un
     * riavvio la policy "di prima" è già salvata da quando la pausa è
     * iniziata — catturarla di nuovo salverebbe la policy silenziosa della
     * pausa stessa al posto di quella originale dell'utente.
     */
    fun capture() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) {
            prefs.edit { putBoolean(KEY_PREV_DND_CAPTURED, false) }
            return
        }
        // Mai null sui telefoni; per sicurezza, senza policy non c'è nulla da salvare.
        val policy: NotificationManager.Policy? = nm.notificationPolicy
        if (policy == null) {
            prefs.edit {
                putBoolean(KEY_PREV_DND_CAPTURED, true)
                putInt(KEY_PREV_FILTER, nm.currentInterruptionFilter)
            }
            return
        }
        prefs.edit {
            putBoolean(KEY_PREV_DND_CAPTURED, true)
            putInt(KEY_PREV_FILTER, nm.currentInterruptionFilter)
            putInt(KEY_PREV_POLICY_CATEGORIES, policy.priorityCategories)
            putInt(KEY_PREV_POLICY_CALL_SENDERS, policy.priorityCallSenders)
            putInt(KEY_PREV_POLICY_MESSAGE_SENDERS, policy.priorityMessageSenders)
            putInt(KEY_PREV_POLICY_SUPPRESSED_EFFECTS, policy.suppressedVisualEffects)
        }
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
    fun apply() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) return // permesso non concesso: si ignora silenziosamente

        nm.setNotificationPolicy(pausePolicy())
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
    }

    private fun pausePolicy(): NotificationManager.Policy {
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
        return NotificationManager.Policy(
            priorityCategories,
            NotificationManager.Policy.PRIORITY_SENDERS_ANY,
            0,
            suppressedEffects
        )
    }

    /**
     * Riapplica la pausa se qualcosa ha spento Non disturbare nel frattempo:
     * una routine o modalità di Samsung che finisce, un tocco nella tendina.
     * Chiamata ogni minuto dal servizio della pausa. Prima la pausa impostava
     * DND una volta sola all'inizio e non lo ricontrollava più, e se veniva
     * spento le notifiche tornavano per il resto della pausa (segnalato).
     * Ritorna true se ha dovuto riapplicarla.
     */
    fun ensureApplied(): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) return false
        if (nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY &&
            (nm.notificationPolicy as NotificationManager.Policy?)?.priorityCategories == pausePolicy().priorityCategories
        ) return false
        apply()
        return true
    }

    /**
     * Ripristina la policy DND catturata da [capture]. Se non
     * ce n'è una (permesso non concesso quando la pausa è iniziata, o dati
     * di una versione precedente a questa correzione senza nulla di
     * salvato), ricade sul comportamento precedente — tutte le notifiche —
     * che resta comunque più sicuro di lasciare attivo il filtro
     * "solo priorità" della pausa indefinitamente.
     */
    fun restore() {
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

        prefs.edit {
            remove(KEY_PREV_DND_CAPTURED)
            remove(KEY_PREV_FILTER)
            remove(KEY_PREV_POLICY_CATEGORIES)
            remove(KEY_PREV_POLICY_CALL_SENDERS)
            remove(KEY_PREV_POLICY_MESSAGE_SENDERS)
            remove(KEY_PREV_POLICY_SUPPRESSED_EFFECTS)
        }
    }

    private companion object {
        // Policy DND dell'utente catturata da capture() prima di applicare
        // quella "silenziosa" della pausa — vedi restore().
        private const val KEY_PREV_DND_CAPTURED = "session_prev_dnd_captured"
        private const val KEY_PREV_FILTER = "session_prev_dnd_filter"
        private const val KEY_PREV_POLICY_CATEGORIES = "session_prev_dnd_categories"
        private const val KEY_PREV_POLICY_CALL_SENDERS = "session_prev_dnd_call_senders"
        private const val KEY_PREV_POLICY_MESSAGE_SENDERS = "session_prev_dnd_message_senders"
        private const val KEY_PREV_POLICY_SUPPRESSED_EFFECTS = "session_prev_dnd_suppressed_effects"
    }
}
