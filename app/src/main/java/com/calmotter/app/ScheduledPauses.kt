package com.calmotter.app

import android.content.Context
import androidx.core.content.edit
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.random.Random

/**
 * Una pausa che parte da sola, ogni settimana, nei giorni scelti
 * (specs/scheduled-pauses/). [days]: un bit per giorno, lunedì = bit 0 …
 * domenica = bit 6. [skipUntil]: "non stasera" — le occorrenze fino a questo
 * istante (incluso) vengono saltate senza spegnere la programmazione.
 * [locked]: "protetta da password", un patto con la persona di fiducia
 * invece di una pausa che ci si è messi da soli (vedi [passwordNeededToSave]).
 *
 * **A sorpresa** (specs/surprise-pauses/): con [windowEndMinuteOfDay] >= 0
 * la pausa non parte a [startMinuteOfDay] ma in un momento estratto a caso
 * fra [startMinuteOfDay] e [windowEndMinuteOfDay], finendo dentro la
 * finestra. [drawnAt] è l'estrazione già fatta: si conserva finché non viene
 * usata, così riaprire l'app non è un modo per spostare la pausa.
 */
data class ScheduledPause(
    val id: Int,
    val days: Int,
    val startMinuteOfDay: Int,
    val durationMinutes: Int,
    val enabled: Boolean = true,
    val profileId: Int = AllowedAppsManager.DEFAULT_PROFILE_ID,
    val skipUntil: Long = 0L,
    val locked: Boolean = false,
    val windowEndMinuteOfDay: Int = -1,
    val drawnAt: Long = 0L,
) {
    val isSurprise: Boolean get() = windowEndMinuteOfDay >= 0

    fun runsOn(dayOfWeekIso: Int): Boolean = days and (1 shl (dayOfWeekIso - 1)) != 0

    /**
     * true se [this] allenta il patto rispetto a [old], quindi va confermato
     * con la password: spenta, giorni tolti, più corta, orario spostato
     * (spostare le 21:00 alle 23:30 è un modo per evitarla), profilo di app
     * consentite diverso. Più giorni o più lunga invece no: stringono.
     *
     * A sorpresa: tornare a un'ora fissa (di nuovo prevedibile) e una
     * finestra più stretta o spostata allentano; più larga stringe. Passare
     * da un'ora fissa a una sorpresa sposta la pausa, quindi allenta.
     */
    fun isLooserThan(old: ScheduledPause): Boolean =
        (old.enabled && !enabled) ||
            (old.days and days) != old.days ||
            durationMinutes < old.durationMinutes ||
            timingIsLooserThan(old) ||
            profileId != old.profileId ||
            skipUntil > old.skipUntil

    private fun timingIsLooserThan(old: ScheduledPause): Boolean = when {
        old.isSurprise && isSurprise ->
            startMinuteOfDay > old.startMinuteOfDay || windowEndMinuteOfDay < old.windowEndMinuteOfDay
        old.isSurprise != isSurprise -> true
        else -> startMinuteOfDay != old.startMinuteOfDay
    }

    /** true se rispetto a [old] cambia qualcosa da cui dipende l'estrazione: va rifatta. */
    fun drawChangedFrom(old: ScheduledPause): Boolean =
        days != old.days || durationMinutes != old.durationMinutes ||
            startMinuteOfDay != old.startMinuteOfDay || windowEndMinuteOfDay != old.windowEndMinuteOfDay
}

/**
 * Se salvare [updated] al posto di [old] (null = pausa nuova) chiede la
 * password. Una pausa non protetta è di chi l'ha messa: si cambia
 * liberamente. Proteggerla è il gesto della persona di fiducia, quindi
 * chiede la password; da lì ogni modifica che allenta il patto
 * ([ScheduledPause.isLooserThan]), togliere la protezione compreso, la
 * chiede di nuovo. Eliminare o saltare la prossima: password solo se
 * protetta ([ScheduledPause.locked]).
 */
fun passwordNeededToSave(old: ScheduledPause?, updated: ScheduledPause): Boolean = when {
    old == null || !old.locked -> updated.locked
    else -> !updated.locked || updated.isLooserThan(old)
}

/** Ultimo minuto sceglibile come fine finestra: il TimePicker non arriva a 24:00. */
const val LAST_MINUTE_OF_DAY = 24 * 60 - 1

/** Una finestra a sorpresa dura almeno la pausa più un'ora: con meno, la partenza si indovina. */
const val SURPRISE_EXTRA_MINUTES = 60

/**
 * La finestra [from]–[to] resa valida per una pausa di [duration] minuti,
 * senza messaggi d'errore: "to" sale fino al minimo, e se il minimo
 * sforerebbe la mezzanotte scende "from". Pura, per i test.
 */
fun surpriseWindow(from: Int, to: Int, duration: Int): Pair<Int, Int> {
    val span = duration + SURPRISE_EXTRA_MINUTES
    val start = from.coerceIn(0, (LAST_MINUTE_OF_DAY - span).coerceAtLeast(0))
    val end = to.coerceAtLeast(start + span).coerceAtMost(LAST_MINUTE_OF_DAY)
    return start to end
}

/** Margine fra adesso e la partenza estratta: lascia posto all'avviso dei 5 minuti. */
private const val SURPRISE_MIN_LEAD_MILLIS = 6 * 60_000L

/**
 * La prossima partenza estratta per una pausa a sorpresa, dopo [now]: il
 * primo giorno valido con ancora spazio nella finestra, un minuto a caso
 * fra l'inizio e "fine − durata", così la pausa finisce dentro. Un giorno
 * già usato da un'estrazione passata ([ScheduledPause.drawnAt]) non ne ha
 * una seconda. Pura, con [random] passato da fuori per i test.
 */
fun nextSurpriseStart(
    schedule: ScheduledPause,
    now: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    random: Random = Random.Default,
): Long? {
    if (schedule.days == 0 || !schedule.isSurprise) return null
    // Una finestra fuori dalla giornata (dati rovinati: l'editor non la
    // permette) non deve far cadere l'app all'apertura, dove gira armAll.
    if (schedule.startMinuteOfDay !in 0..LAST_MINUTE_OF_DAY || schedule.windowEndMinuteOfDay > LAST_MINUTE_OF_DAY) return null
    val today = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zone).toLocalDate()
    val usedDay = schedule.drawnAt.takeIf { it in 1..now }
        ?.let { ZonedDateTime.ofInstant(Instant.ofEpochMilli(it), zone).toLocalDate() }
    val latestMinute = schedule.windowEndMinuteOfDay - schedule.durationMinutes
    if (latestMinute < schedule.startMinuteOfDay) return null
    for (offset in 0..14) {
        val date = today.plusDays(offset.toLong())
        if (!schedule.runsOn(date.dayOfWeek.value)) continue
        if (usedDay != null && !date.isAfter(usedDay)) continue
        fun millisAt(minute: Int) = date.atTime(LocalTime.of(minute / 60, minute % 60))
            .atZone(zone).toInstant().toEpochMilli()
        val latest = millisAt(latestMinute)
        if (latest <= schedule.skipUntil) continue
        val earliest = maxOf(millisAt(schedule.startMinuteOfDay), now + SURPRISE_MIN_LEAD_MILLIS)
        // Al minuto intero, come le pause a ora fissa.
        val firstMinute = (earliest + 59_999L) / 60_000L
        val lastMinute = latest / 60_000L
        if (firstMinute > lastMinute) continue
        return random.nextLong(firstMinute, lastMinute + 1) * 60_000L
    }
    return null
}

/**
 * La fine della finestra nel giorno di [at]: "salta la prossima" di una
 * pausa a sorpresa salta tutta la finestra di quel giorno, non solo l'istante.
 */
fun surpriseWindowEndOn(schedule: ScheduledPause, at: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
    val date = ZonedDateTime.ofInstant(Instant.ofEpochMilli(at), zone).toLocalDate()
    val minute = schedule.windowEndMinuteOfDay
    return date.atTime(LocalTime.of(minute / 60, minute % 60)).atZone(zone).toInstant().toEpochMilli()
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
        listOf(
            s.id, s.days, s.startMinuteOfDay, s.durationMinutes, if (s.enabled) 1 else 0, s.profileId, s.skipUntil,
            if (s.locked) 1 else 0, s.windowEndMinuteOfDay, s.drawnAt,
        ).joinToString("|")

    private fun decode(line: String): ScheduledPause? {
        val p = line.split("|")
        // 7 campi: salvate prima che esistesse "protetta" (locked), che vale
        // false — così le pause già sul telefono di prova non si perdono.
        // 8 campi: prima delle pause a sorpresa, quindi a ora fissa.
        if (p.size != 7 && p.size != 8 && p.size != 10) return null
        return runCatching {
            ScheduledPause(
                id = p[0].toInt(), days = p[1].toInt(), startMinuteOfDay = p[2].toInt(),
                durationMinutes = p[3].toInt(), enabled = p[4] == "1", profileId = p[5].toInt(),
                skipUntil = p[6].toLong(), locked = p.getOrNull(7) == "1",
                windowEndMinuteOfDay = p.getOrNull(8)?.toInt() ?: -1, drawnAt = p.getOrNull(9)?.toLong() ?: 0L,
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
