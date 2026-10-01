package com.calmotter.app

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import kotlin.random.Random

/** Le quattro famiglie di attività, ognuna con la sua icona (vedi Cronologia). */
enum class TogetherCategory(@StringRes val label: Int, @DrawableRes val icon: Int) {
    OUTSIDE(R.string.together_category_outside, R.drawable.ic_together_outside),
    TABLE(R.string.together_category_table, R.drawable.ic_together_table),
    GAMES(R.string.together_category_games, R.drawable.ic_together_games),
    SLOW(R.string.together_category_slow, R.drawable.ic_together_slow),
}

/**
 * Un'attività da proporre in una pausa di gruppo. Viaggia solo l'[id] (un
 * byte nella ricetta), così ogni telefono la mostra nella propria lingua.
 */
data class TogetherActivity(
    val id: Int,
    val slug: String,
    val category: TogetherCategory,
    @StringRes val text: Int,
    val minMinutes: Int,
    val maxMinutes: Int,
) {
    fun fits(durationMinutes: Int): Boolean = durationMinutes in minMinutes..maxMinutes
}

/**
 * Il catalogo delle attività insieme (specs/together-activity/).
 *
 * Regole di tono, valide anche per le voci future: prima persona plurale
 * ("Facciamo…", "Let's…") perché è il gruppo che propone a se stesso, mai
 * l'app che dà ordini; una sola attività per voce, mai "questo o quello";
 * adatte a una coppia, una famiglia o dei colleghi, senza attrezzatura né
 * spese.
 *
 * **Gli id sono per sempre:** viaggiano nella ricetta e restano in
 * Cronologia. Un'attività tolta lascia un buco, il suo id non si riusa.
 * 0 vuol dire "nessuna attività".
 */
object TogetherActivities {
    const val NONE = 0
    private const val ANY_DURATION = Int.MAX_VALUE

    val all: List<TogetherActivity> = listOf(
        TogetherActivity(1, "together", TogetherCategory.SLOW, R.string.together_activity_together, 0, ANY_DURATION),
        TogetherActivity(2, "tea", TogetherCategory.TABLE, R.string.together_activity_tea, 10, 30),
        TogetherActivity(3, "coffee", TogetherCategory.TABLE, R.string.together_activity_coffee, 10, 30),
        TogetherActivity(4, "best_of_week", TogetherCategory.SLOW, R.string.together_activity_best_of_week, 10, 30),
        TogetherActivity(5, "balcony", TogetherCategory.OUTSIDE, R.string.together_activity_balcony, 10, 60),
        TogetherActivity(6, "album", TogetherCategory.SLOW, R.string.together_activity_album, 10, 60),
        TogetherActivity(7, "short_walk", TogetherCategory.OUTSIDE, R.string.together_activity_short_walk, 30, 90),
        TogetherActivity(8, "cook_simple", TogetherCategory.TABLE, R.string.together_activity_cook_simple, 30, 90),
        TogetherActivity(9, "read", TogetherCategory.SLOW, R.string.together_activity_read, 30, 120),
        TogetherActivity(10, "cards", TogetherCategory.GAMES, R.string.together_activity_cards, 30, 120),
        TogetherActivity(11, "board_game", TogetherCategory.GAMES, R.string.together_activity_board_game, 30, 120),
        TogetherActivity(12, "tidy", TogetherCategory.SLOW, R.string.together_activity_tidy, 30, 90),
        TogetherActivity(13, "draw", TogetherCategory.SLOW, R.string.together_activity_draw, 30, 120),
        TogetherActivity(14, "write", TogetherCategory.SLOW, R.string.together_activity_write, 30, 120),
        TogetherActivity(15, "places", TogetherCategory.SLOW, R.string.together_activity_places, 30, 90),
        TogetherActivity(16, "long_walk", TogetherCategory.OUTSIDE, R.string.together_activity_long_walk, 60, 240),
        TogetherActivity(17, "slow_dinner", TogetherCategory.TABLE, R.string.together_activity_slow_dinner, 60, 240),
        TogetherActivity(18, "park", TogetherCategory.OUTSIDE, R.string.together_activity_park, 60, 240),
        TogetherActivity(19, "photos", TogetherCategory.SLOW, R.string.together_activity_photos, 60, 240),
        TogetherActivity(20, "board_afternoon", TogetherCategory.GAMES, R.string.together_activity_board_afternoon, 90, 240),
        TogetherActivity(21, "somewhere_new", TogetherCategory.OUTSIDE, R.string.together_activity_somewhere_new, 90, 240),
    )

    fun byId(id: Int): TogetherActivity? = all.firstOrNull { it.id == id }

    fun fitting(durationMinutes: Int): List<TogetherActivity> = all.filter { it.fits(durationMinutes) }

    /**
     * La prossima proposta per [durationMinutes]: a caso fra quelle adatte
     * non ancora viste ([seen]), preferendo una categoria diversa da
     * [lastCategory] perché l'alternativa sia davvero un'alternativa. Quando
     * le ha viste tutte ricomincia.
     */
    fun next(
        durationMinutes: Int,
        seen: Set<Int> = emptySet(),
        lastCategory: TogetherCategory? = null,
        random: Random = Random.Default,
    ): TogetherActivity {
        val fitting = fitting(durationMinutes).ifEmpty { listOf(all.first()) }
        val unseen = fitting.filter { it.id !in seen }.ifEmpty { fitting }
        val otherCategory = unseen.filter { it.category != lastCategory }.ifEmpty { unseen }
        return otherCategory.random(random)
    }
}
