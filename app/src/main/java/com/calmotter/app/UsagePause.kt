package com.calmotter.app

import android.content.Context
import androidx.core.content.edit
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/*
 * Pausa dopo un uso lungo (specs/usage-pause/): quando il telefono è in uso
 * da tanto senza una pausa vera, parte una pausa da sola, con un avviso
 * cinque minuti (d'uso) prima. Qui le parti pure — contatore, regola, notte —
 * e lo stato salvato; il cablaggio con lo schermo sta in [UsageWatcher].
 */

/** Le soglie proposte: "dopo" quanti minuti d'uso. */
val USAGE_AFTER_OPTIONS: List<Int> = listOf(20, 30, 45, 60, 90)

/** Le durate della pausa che parte: corte, è un'interruzione e non un programma. */
val USAGE_DURATION_OPTIONS: List<Int> = listOf(10, 30, 60)

/** Avviso: cinque minuti d'uso prima della pausa, come le pause programmate. */
const val USAGE_HEADS_UP_MILLIS = 5 * 60_000L

/** Una pausa vera: almeno cinque minuti a schermo spento azzerano il conto. */
const val USAGE_BREAK_MILLIS = 5 * 60_000L

/** "Non ora" sposta la pausa di quindici minuti d'uso. */
const val USAGE_NOT_NOW_MILLIS = 15 * 60_000L

/** "Non ora" al massimo tre volte al giorno. */
const val USAGE_NOT_NOW_PER_DAY = 3

/**
 * Lo stato del contatore, tre istanti e due interruttori: niente pacchetti,
 * niente storia (vedi la sezione Privacy dello spec).
 */
data class UsageState(
    /** Tempo d'uso già contato, fino a [runningSince]. */
    val accumulatedMillis: Long = 0L,
    /** Da quando il conto corre; 0 = fermo. */
    val runningSince: Long = 0L,
    /** Ultimo spegnimento dello schermo; 0 = mai visto. */
    val lastScreenOffAt: Long = 0L,
    val screenOn: Boolean = false,
    /** Conto sospeso: chiamata, app che resta, pausa in corso. */
    val excluded: Boolean = false,
)

/**
 * Il tempo d'uso continuo. Puro: riceve gli eventi con il loro istante e
 * non chiede nulla al sistema, così le regole si provano da sole.
 *
 * - schermo spento: il conto si ferma;
 * - schermo riacceso dopo almeno [breakMillis]: riparte da zero;
 * - escluso (chiamata, app che resta, pausa): si ferma senza azzerarsi.
 */
class UsageCounter(var state: UsageState = UsageState(), private val breakMillis: Long = USAGE_BREAK_MILLIS) {

    /** Schermo acceso e sbloccato. Ritorna true se il conto è stato azzerato da una pausa vera. */
    fun screenOn(at: Long): Boolean {
        if (state.screenOn) return false
        val wasBreak = state.lastScreenOffAt > 0 && at - state.lastScreenOffAt >= breakMillis
        val base = if (wasBreak) state.copy(accumulatedMillis = 0L, runningSince = 0L) else state
        state = base.copy(screenOn = true).restarted(at)
        return wasBreak
    }

    fun screenOff(at: Long) {
        if (!state.screenOn) return
        state = state.folded(at).copy(screenOn = false, lastScreenOffAt = at).restarted(at)
    }

    fun setExcluded(at: Long, excluded: Boolean) {
        if (state.excluded == excluded) return
        state = state.folded(at).copy(excluded = excluded).restarted(at)
    }

    fun usedMillis(at: Long): Long =
        state.accumulatedMillis + if (state.runningSince > 0) (at - state.runningSince).coerceAtLeast(0L) else 0L

    val isRunning: Boolean get() = state.runningSince > 0

    /** Da zero, tenendo com'è lo schermo adesso. */
    fun reset(at: Long) {
        state = state.copy(accumulatedMillis = 0L, runningSince = 0L).restarted(at)
    }

    private fun UsageState.folded(at: Long): UsageState =
        if (runningSince > 0) copy(accumulatedMillis = accumulatedMillis + (at - runningSince).coerceAtLeast(0L), runningSince = 0L)
        else this

    private fun UsageState.restarted(at: Long): UsageState =
        copy(runningSince = if (screenOn && !excluded) at else 0L)
}

/**
 * La regola scelta in Impostazioni. [locked]: "protetta da password", lo
 * stesso patto delle pause programmate (vedi [usagePasswordNeededToSave]).
 */
data class UsagePauseRule(
    val enabled: Boolean = false,
    val afterMinutes: Int = 45,
    val durationMinutes: Int = 10,
    val profileId: Int = AllowedAppsManager.DEFAULT_PROFILE_ID,
    val locked: Boolean = false,
) {
    /** Spenta, soglia più alta, pausa più corta, altro profilo: allenta. Il contrario stringe. */
    fun isLooserThan(old: UsagePauseRule): Boolean =
        (old.enabled && !enabled) ||
            afterMinutes > old.afterMinutes ||
            durationMinutes < old.durationMinutes ||
            profileId != old.profileId
}

/** Come [passwordNeededToSave] delle pause programmate: proteggere chiede la password, poi allentare. */
fun usagePasswordNeededToSave(old: UsagePauseRule, updated: UsagePauseRule): Boolean = when {
    !old.locked -> updated.locked
    else -> !updated.locked || updated.isLooserThan(old)
}

/** Di notte (23:00–07:00) la pausa dopo un uso lungo non si propone: deciso con l'utente. */
fun isUsageQuietTime(at: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean {
    val hour = ZonedDateTime.ofInstant(Instant.ofEpochMilli(at), zone).hour
    return hour >= 23 || hour < 7
}

/** Millisecondi al prossimo cambio fra giorno e notte (alle 7:00 o alle 23:00). */
fun millisToNextQuietChange(at: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
    val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(at), zone)
    val today = now.toLocalDate()
    val candidates = listOf(today.atTime(7, 0), today.atTime(23, 0), today.plusDays(1).atTime(7, 0))
        .map { it.atZone(zone).toInstant().toEpochMilli() }
    return candidates.first { it > at } - at
}

/** La prossima pausa programmata (a ora fissa o a sorpresa già estratta), dopo [now]; null se nessuna. */
fun nextScheduledStart(schedules: List<ScheduledPause>, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long? =
    schedules.filter { it.enabled }.mapNotNull { s ->
        if (s.isSurprise) s.drawnAt.takeIf { it > now && it > s.skipUntil } else nextOccurrence(s, now, zone)
    }.minOrNull()

/**
 * Regola e stato della pausa dopo un uso lungo, in SharedPreferences come le
 * pause programmate: poche chiavi, nessuna storia.
 */
class UsagePauseManager internal constructor(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun rule(): UsagePauseRule = UsagePauseRule(
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        afterMinutes = prefs.getInt(KEY_AFTER, 45).takeIf { it in USAGE_AFTER_OPTIONS } ?: 45,
        durationMinutes = prefs.getInt(KEY_DURATION, 10).takeIf { it in USAGE_DURATION_OPTIONS } ?: 10,
        profileId = prefs.getInt(KEY_PROFILE, AllowedAppsManager.DEFAULT_PROFILE_ID),
        locked = prefs.getBoolean(KEY_LOCKED, false),
    )

    fun saveRule(rule: UsagePauseRule) = prefs.edit {
        putBoolean(KEY_ENABLED, rule.enabled)
        putInt(KEY_AFTER, rule.afterMinutes)
        putInt(KEY_DURATION, rule.durationMinutes)
        putInt(KEY_PROFILE, rule.profileId)
        putBoolean(KEY_LOCKED, rule.locked)
    }

    fun counterState(): UsageState = UsageState(
        accumulatedMillis = prefs.getLong(KEY_ACCUMULATED, 0L),
        runningSince = prefs.getLong(KEY_RUNNING_SINCE, 0L),
        lastScreenOffAt = prefs.getLong(KEY_LAST_OFF, 0L),
        screenOn = prefs.getBoolean(KEY_SCREEN_ON, false),
        excluded = prefs.getBoolean(KEY_EXCLUDED, false),
    )

    fun saveCounterState(state: UsageState) = prefs.edit {
        putLong(KEY_ACCUMULATED, state.accumulatedMillis)
        putLong(KEY_RUNNING_SINCE, state.runningSince)
        putLong(KEY_LAST_OFF, state.lastScreenOffAt)
        putBoolean(KEY_SCREEN_ON, state.screenOn)
        putBoolean(KEY_EXCLUDED, state.excluded)
    }

    /** Minuti d'uso in più concessi dai "Non ora" di questo giro. */
    var extraMillis: Long
        get() = prefs.getLong(KEY_EXTRA, 0L)
        set(value) = prefs.edit { putLong(KEY_EXTRA, value) }

    /** L'avviso di questo giro è già stato mostrato. */
    var headsUpShown: Boolean
        get() = prefs.getBoolean(KEY_HEADS_UP, false)
        set(value) = prefs.edit { putBoolean(KEY_HEADS_UP, value) }

    /** Un giro nuovo: niente "Non ora" accumulati, niente avviso. */
    fun clearRound() = prefs.edit {
        remove(KEY_EXTRA)
        remove(KEY_HEADS_UP)
    }

    /** Quanti "Non ora" restano oggi. */
    fun notNowLeft(today: LocalDate = LocalDate.now()): Int {
        val used = if (prefs.getLong(KEY_NOT_NOW_DAY, -1L) == today.toEpochDay()) prefs.getInt(KEY_NOT_NOW_COUNT, 0) else 0
        return (USAGE_NOT_NOW_PER_DAY - used).coerceAtLeast(0)
    }

    fun recordNotNow(today: LocalDate = LocalDate.now()) {
        val used = USAGE_NOT_NOW_PER_DAY - notNowLeft(today)
        prefs.edit {
            putLong(KEY_NOT_NOW_DAY, today.toEpochDay())
            putInt(KEY_NOT_NOW_COUNT, used + 1)
        }
    }

    companion object {
        private const val PREFS_NAME = "calm_otter_usage_pause"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_AFTER = "after_minutes"
        private const val KEY_DURATION = "duration_minutes"
        private const val KEY_PROFILE = "profile_id"
        private const val KEY_LOCKED = "locked"
        private const val KEY_ACCUMULATED = "accumulated"
        private const val KEY_RUNNING_SINCE = "running_since"
        private const val KEY_LAST_OFF = "last_screen_off"
        private const val KEY_SCREEN_ON = "screen_on"
        private const val KEY_EXCLUDED = "excluded"
        private const val KEY_EXTRA = "extra_millis"
        private const val KEY_HEADS_UP = "heads_up_shown"
        private const val KEY_NOT_NOW_DAY = "not_now_day"
        private const val KEY_NOT_NOW_COUNT = "not_now_count"

        /** L'istanza dell'app: vive in [AppGraph], una per Application. */
        fun getInstance(context: Context): UsagePauseManager = context.appGraph.usagePauseManager
    }
}
