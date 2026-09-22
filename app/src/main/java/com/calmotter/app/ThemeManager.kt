package com.calmotter.app

import android.content.Context
import androidx.appcompat.app.AppCompatActivity

enum class AppTheme(val key: String) {
    SAGE("sage"),
    DUSK_SAND("dusk_sand"),
    DAWN_CLAY("dawn_clay"),

    /**
     * Quarta palette, importata dal redesign (vedi
     * specs/multi-theme-system/design.md). Aggiunta in coda e non come nuovo
     * default: chi ha già l'app non deve vedersi cambiare i colori sotto le
     * mani, e [fromKey] continua a ricadere su [SAGE].
     */
    DEEP_FOREST("deep_forest");

    companion object {
        /**
         * Chiavi storiche delle palette sostituite dal redesign: chi aveva
         * scelto Lavanda o Terracotta si ritrova sulla palette che ne ha
         * preso il posto invece che riportato al default. La preferenza su
         * disco resta la vecchia stringa finche' l'utente non ne sceglie
         * un'altra — nessuna migrazione da scrivere, solo da leggere.
         */
        private val renamed = mapOf("lavender" to DUSK_SAND, "terracotta" to DAWN_CLAY)

        fun fromKey(key: String) =
            entries.firstOrNull { it.key == key } ?: renamed[key] ?: SAGE
    }
}

object ThemeManager {

    private const val PREFS = "calm_otter_theme"
    private const val KEY   = "selected_theme"

    fun getTheme(context: Context): AppTheme {
        val key = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, AppTheme.SAGE.key) ?: AppTheme.SAGE.key
        return AppTheme.fromKey(key)
    }

    fun setTheme(context: Context, theme: AppTheme) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, theme.key).apply()
    }

    /** Applica il tema corretto — da chiamare PRIMA di super.onCreate(). */
    fun applyTheme(activity: AppCompatActivity, variant: ThemeVariant = ThemeVariant.BASE) {
        val styleId = when (getTheme(activity)) {
            AppTheme.SAGE        -> when (variant) {
                ThemeVariant.BASE         -> R.style.Theme_CalmOtter_Sage
                ThemeVariant.BLOCK        -> R.style.Theme_CalmOtter_Sage_Block
                ThemeVariant.WITH_ACTION_BAR -> R.style.Theme_CalmOtter_Sage_WithActionBar
            }
            AppTheme.DUSK_SAND    -> when (variant) {
                ThemeVariant.BASE         -> R.style.Theme_CalmOtter_DuskSand
                ThemeVariant.BLOCK        -> R.style.Theme_CalmOtter_DuskSand_Block
                ThemeVariant.WITH_ACTION_BAR -> R.style.Theme_CalmOtter_DuskSand_WithActionBar
            }
            AppTheme.DAWN_CLAY  -> when (variant) {
                ThemeVariant.BASE         -> R.style.Theme_CalmOtter_DawnClay
                ThemeVariant.BLOCK        -> R.style.Theme_CalmOtter_DawnClay_Block
                ThemeVariant.WITH_ACTION_BAR -> R.style.Theme_CalmOtter_DawnClay_WithActionBar
            }
            AppTheme.DEEP_FOREST -> when (variant) {
                ThemeVariant.BASE         -> R.style.Theme_CalmOtter_DeepForest
                ThemeVariant.BLOCK        -> R.style.Theme_CalmOtter_DeepForest_Block
                ThemeVariant.WITH_ACTION_BAR -> R.style.Theme_CalmOtter_DeepForest_WithActionBar
            }
        }
        activity.setTheme(styleId)
    }
}

enum class ThemeVariant { BASE, BLOCK, WITH_ACTION_BAR }
