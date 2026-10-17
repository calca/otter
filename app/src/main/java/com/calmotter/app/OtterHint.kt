package com.calmotter.app

import android.content.Context
import androidx.core.content.edit
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/**
 * Il fumetto sopra l'otter in Home (specs/otter-daily-hint/).
 *
 * - **Il primo**, "Toccami quando vuoi una pausa": finché non c'è mai stata
 *   una pausa. Sparisce per sempre alla prima, comunque parta
 *   ([SessionManager.startSession] chiama [markDone]), o al tocco.
 * - **Quello del giorno**: chi usa poco l'app si dimentica che l'otter si
 *   tocca. Al massimo una volta al giorno, alla prima apertura della Home,
 *   e solo se oggi non c'è ancora stata una pausa: una frase a caso, sempre
 *   un invito, mai un conto dei giorni mancati. A chi non ha pause
 *   programmate, ogni tanto, la frase ricorda che l'otter può partire da
 *   solo, e il tocco porta lì. Si spegne in Impostazioni.
 */
object OtterHint {
    private const val PREFS = "calm_otter_ui"
    private const val KEY_DONE = "otter_hint_done"
    private const val KEY_DAILY_ENABLED = "otter_daily_hint_enabled"
    private const val KEY_LAST_DAILY_DAY = "otter_daily_hint_last_day"

    /** Una volta su quattro, a chi non ha pause programmate, la frase su di loro. */
    private const val SCHEDULES_ODDS = 4

    /** Il generatore delle frasi; fissato nei test. */
    internal var random: Random = Random.Default

    /** Cosa dice il fumetto e cosa fa il tocco. */
    data class Hint(
        val text: String,
        // true = il tocco apre le pause programmate invece di chiudere il fumetto.
        val opensSchedules: Boolean = false,
        // true = quello del giorno: sparisce da solo dopo qualche secondo.
        val daily: Boolean = false,
    )

    /**
     * Il fumetto da mostrare ora in Home, o null. Quello del giorno conta come
     * mostrato già qui: riaprendo la Home nella stessa giornata non torna,
     * che lo si sia toccato o no.
     */
    fun pick(
        context: Context,
        history: List<SessionRecord>,
        hasSchedules: Boolean,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Hint? {
        val prefs = prefs(context)
        if (history.isEmpty() && !prefs.getBoolean(KEY_DONE, false)) {
            return Hint(context.getString(R.string.otter_hint))
        }
        if (!isDailyEnabled(context)) return null
        if (prefs.getLong(KEY_LAST_DAILY_DAY, Long.MIN_VALUE) == today.toEpochDay()) return null
        val pausedToday = history.any {
            Instant.ofEpochMilli(it.startTimeMs).atZone(zone).toLocalDate() == today
        }
        if (pausedToday) return null

        prefs.edit { putLong(KEY_LAST_DAILY_DAY, today.toEpochDay()) }
        if (!hasSchedules && random.nextInt(SCHEDULES_ODDS) == 0) {
            return Hint(context.getString(R.string.otter_hint_schedules), opensSchedules = true, daily = true)
        }
        val phrases = context.resources.getStringArray(R.array.otter_daily_phrases)
        return Hint(phrases.random(random), daily = true)
    }

    fun markDone(context: Context) = prefs(context).edit { putBoolean(KEY_DONE, true) }

    fun isDailyEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_DAILY_ENABLED, true)

    fun setDailyEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit { putBoolean(KEY_DAILY_ENABLED, enabled) }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

