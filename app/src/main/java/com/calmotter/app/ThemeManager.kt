package com.calmotter.app

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit

/**
 * Le quattro palette. I loro colori non stanno qui né in Kotlin: sono le
 * risorse `<chiave>_*` di values/colors.xml (e values-night/), letta sia dal
 * tema XML della finestra sia da Compose — vedi [com.calmotter.app.ui.theme.
 * CalmOtterTheme] e specs/multi-theme-system/design.md. Una chiave sconosciuta
 * ricade su [SAGE].
 */
enum class AppTheme(val key: String) {
    SAGE("sage"),

    /** L'unica palette fredda: un blu-petrolio, per distinguersi dal verde di [SAGE]. */
    STILL_WATER("still_water"),
    DUSK_SAND("dusk_sand"),
    DAWN_CLAY("dawn_clay");

    companion object {
        fun fromKey(key: String) = entries.firstOrNull { it.key == key } ?: SAGE
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
            .edit { putString(KEY, theme.key) }
    }

    /**
     * Applica il tema corretto — da chiamare PRIMA di super.onCreate().
     *
     * Due strati: la base strutturale (con o senza ActionBar) e, sopra,
     * l'overlay della palette scelta, che porta solo colori (vedi
     * values/themes.xml). Richiamabile anche a Activity già creata, per un
     * cambio palette al volo: l'overlay sovrascrive i valori precedenti.
     */
    fun applyTheme(activity: AppCompatActivity, variant: ThemeVariant = ThemeVariant.BASE) {
        activity.setTheme(
            when (variant) {
                ThemeVariant.BASE -> R.style.Theme_CalmOtter_Base
                ThemeVariant.WITH_ACTION_BAR -> R.style.Theme_CalmOtter_Base_WithActionBar
            }
        )
        activity.setTheme(overlayFor(getTheme(activity)))
    }

    private fun overlayFor(theme: AppTheme): Int = when (theme) {
        AppTheme.SAGE        -> R.style.ThemeOverlay_CalmOtter_Sage
        AppTheme.STILL_WATER -> R.style.ThemeOverlay_CalmOtter_StillWater
        AppTheme.DUSK_SAND   -> R.style.ThemeOverlay_CalmOtter_DuskSand
        AppTheme.DAWN_CLAY   -> R.style.ThemeOverlay_CalmOtter_DawnClay
    }
}

enum class ThemeVariant { BASE, WITH_ACTION_BAR }
