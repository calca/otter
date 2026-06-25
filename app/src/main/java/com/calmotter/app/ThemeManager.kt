package com.calmotter.app

import android.content.Context
import androidx.appcompat.app.AppCompatActivity

enum class AppTheme(val key: String) {
    SAGE("sage"),
    LAVENDER("lavender"),
    TERRACOTTA("terracotta");

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
            AppTheme.LAVENDER    -> when (variant) {
                ThemeVariant.BASE         -> R.style.Theme_CalmOtter_Lavender
                ThemeVariant.BLOCK        -> R.style.Theme_CalmOtter_Lavender_Block
                ThemeVariant.WITH_ACTION_BAR -> R.style.Theme_CalmOtter_Lavender_WithActionBar
            }
            AppTheme.TERRACOTTA  -> when (variant) {
                ThemeVariant.BASE         -> R.style.Theme_CalmOtter_Terracotta
                ThemeVariant.BLOCK        -> R.style.Theme_CalmOtter_Terracotta_Block
                ThemeVariant.WITH_ACTION_BAR -> R.style.Theme_CalmOtter_Terracotta_WithActionBar
            }
        }
        activity.setTheme(styleId)
    }

    /** Colore accent del tema corrente (per il widget e altri usi non-Activity). */
    fun accentColor(context: Context): Int {
        return when (getTheme(context)) {
            AppTheme.SAGE        -> 0xFF0F5238.toInt()
            AppTheme.LAVENDER    -> 0xFF6750A4.toInt()
            AppTheme.TERRACOTTA  -> 0xFF8F4C38.toInt()
        }
    }
}

enum class ThemeVariant { BASE, BLOCK, WITH_ACTION_BAR }
