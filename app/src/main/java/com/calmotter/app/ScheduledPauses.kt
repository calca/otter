package com.calmotter.app

import android.content.Context
import androidx.core.content.edit
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Una pausa che parte da sola, ogni settimana, nei giorni scelti
 * (specs/scheduled-pauses/). [days]: un bit per giorno, lunedì = bit 0 …
 * domenica = bit 6. [skipUntil]: "non stasera" — le occorrenze fino a questo
 * istante (incluso) vengono saltate senza spegnere la programmazione.
 */
data class ScheduledPause(
    val id: Int,
    val days: Int,
    val startMinuteOfDay: Int,
    val durationMinutes: Int,
    val enabled: Boolean = true,
    val profileId: Int = AllowedAppsManager.DEFAULT_PROFILE_ID,
    val skipUntil: Long = 0L,
) {
    fun runsOn(dayOfWeekIso: Int): Boolean = days and (1 shl (dayOfWeekIso - 1)) != 0

    /**
     * true se [this] allenta il patto rispetto a [old], quindi va confermato
     * con la password: spenta, giorni tolti, più corta, orario spostato
     * (spostare le 21:00 alle 23:30 è un modo per evitarla), profilo di app
     * consentite diverso. Più giorni o più lunga invece no: stringono.
     */
    fun isLooserThan(old: ScheduledPause): Boolean =
        (old.enabled && !enabled) ||
            (old.days and days) != old.days ||
            durationMinutes < old.durationMinutes ||
            startMinuteOfDay != old.startMinuteOfDay ||
            profileId != old.profileId ||
            skipUntil > old.skipUntil
}

/**
 * La prossima partenza di [schedule] strettamente dopo [now], saltando quelle
 * fino a `skipUntil`; null se non ha giorni. Pura, per i test: ora legale e
 * cambio di settimana passano da java.time.
 */
fun nextOccurrence(schedule: ScheduledPause, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
    if (schedule.days == 0) return null
    val today = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zone).toLocalDate()
    for (offset in 0..14) {
        val date = today.plusDays(offset.toLong())
        if (!schedule.runsOn(date.dayOfWeek.value)) continue
        // L'ora locale, non "mezzanotte + minuti": nel giorno del cambio
        // d'ora la giornata dura 23 o 25 ore, e la somma faceva partire la
        // pausa un'ora prima o dopo (trovato dal test sull'ora legale).
        val at = date.atTime(LocalTime.of(schedule.startMinuteOfDay / 60, schedule.startMinuteOfDay % 60))
            .atZone(zone)
            .toInstant().toEpochMilli()
        if (at > now && at > schedule.skipUntil) return at
    }
    return null
}

/** Le pause programmate, in SharedPreferences: poche righe, mai interrogate, niente Room. */
class ScheduleManager internal constructor(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun all(): List<ScheduledPause> =
        prefs.getString(KEY, "").orEmpty().lines().mapNotNull(::decode)

    fun byId(id: Int): ScheduledPause? = all().firstOrNull { it.id == id }

    /** Aggiunge (id 0) o sostituisce; restituisce la versione salvata. */
    fun save(schedule: ScheduledPause): ScheduledPause {
        val current = all()
        val saved = if (schedule.id == 0) schedule.copy(id = (current.maxOfOrNull { it.id } ?: 0) + 1) else schedule
        write(current.filter { it.id != saved.id } + saved)
        return saved
    }

    fun delete(id: Int) = write(all().filter { it.id != id })

    private fun write(list: List<ScheduledPause>) {
        prefs.edit { putString(KEY, list.sortedBy { it.id }.joinToString("\n", transform = ::encode)) }
    }

    private fun encode(s: ScheduledPause) =
        listOf(s.id, s.days, s.startMinuteOfDay, s.durationMinutes, if (s.enabled) 1 else 0, s.profileId, s.skipUntil)
            .joinToString("|")

    private fun decode(line: String): ScheduledPause? {
        val p = line.split("|")
        if (p.size != 7) return null
        return runCatching {
            ScheduledPause(
                id = p[0].toInt(), days = p[1].toInt(), startMinuteOfDay = p[2].toInt(),
                durationMinutes = p[3].toInt(), enabled = p[4] == "1", profileId = p[5].toInt(),
                skipUntil = p[6].toLong(),
            )
        }.getOrNull()
    }

    companion object {
        private const val PREFS_NAME = "calm_otter_schedules"
        private const val KEY = "schedules"

        /** L'istanza dell'app: vive in [AppGraph], una per Application (vedi CalmOtterApplication). */
        fun getInstance(context: Context): ScheduleManager = context.appGraph.scheduleManager
    }
}
