package com.calmotter.app

import android.content.Context
import androidx.core.content.edit

/**
 * Le durate di pausa offerte dall'app, in minuti: la pausa respiro da 10
 * (vedi specs/breathing-pause/) e poi da 30 minuti a 4 ore a passi di 30.
 * Un solo elenco per la Home e per la lobby di Time together, così la durata
 * scelta in uno dei due cade sempre su un'opzione esistente nell'altro.
 */
val SESSION_DURATION_OPTIONS: List<Int> = listOf(10) + (1..8).map { it * 30 }

/**
 * Durata proposta quando non ne è mai stata scelta una — 1 h e non la più
 * breve: segnalato esplicitamente. Vale per Home, widget, riquadro nelle
 * Impostazioni rapide e per il ripiego dei flussi di Time together.
 */
const val DEFAULT_SESSION_DURATION_MINUTES = 60

/** Fino a questa durata la pausa è una "pausa respiro": anello che respira al posto dell'avanzamento. */
const val BREATHING_PAUSE_MAX_MINUTES = 10

fun isBreathingPause(totalMillis: Long): Boolean =
    totalMillis in 1..BREATHING_PAUSE_MAX_MINUTES * 60_000L

/** Etichetta delle pillole di durata della Home: "10 min", "1 h", "1 h 30". */
fun durationPillLabel(minutes: Int): String {
    if (minutes < 60) return "$minutes min"
    val hours = minutes / 60
    val rest = minutes % 60
    return if (rest == 0) "$hours h" else "$hours h $rest"
}

/**
 * L'ultima durata scelta, unica per tutta l'app (specs/breathing-pause/,
 * User Story 2): la Home la preseleziona, widget e riquadro la usano per
 * avviare. Si aggiorna al tocco di una pillola in Home e a ogni pausa avviata
 * con una durata scelta dall'utente (vedi [SessionManager.startSession]).
 *
 * Vive nelle stesse preferenze dove il widget la teneva già, così il valore
 * salvato da prima resta valido.
 */
object LastDuration {
    private const val PREFS = "calm_otter_widget"
    private const val KEY = "last_duration"

    fun get(context: Context): Int {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY, DEFAULT_SESSION_DURATION_MINUTES)
        // Una durata non più offerta (o mai offerta) ricade sul default
        // invece di lasciare la Home senza pillola selezionata.
        return if (saved in SESSION_DURATION_OPTIONS) saved else DEFAULT_SESSION_DURATION_MINUTES
    }

    fun save(context: Context, minutes: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { putInt(KEY, minutes) }
    }
}
