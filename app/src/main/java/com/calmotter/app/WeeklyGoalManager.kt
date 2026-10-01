package com.calmotter.app

import android.content.Context
import androidx.core.content.edit

enum class GoalType { MINUTES, SESSIONS }

data class WeeklyGoal(val type: GoalType, val target: Int)

/**
 * Gestisce l'obiettivo settimanale opzionale dell'utente (minuti o sessioni
 * di pausa a settimana). Dato puramente locale, non sensibile: nessuna
 * cifratura necessaria — stesso pattern di PhraseManager/AllowedAppsManager.
 *
 * Nessun obiettivo impostato di default: la funzionalità è opt-in.
 */
class WeeklyGoalManager internal constructor(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Restituisce l'obiettivo impostato, o null se l'utente non ne ha ancora scelto uno. */
    fun getGoal(): WeeklyGoal? {
        val typeName = prefs.getString(KEY_TYPE, null) ?: return null
        val target = prefs.getInt(KEY_TARGET, 0)
        if (target <= 0) return null
        val type = runCatching { GoalType.valueOf(typeName) }.getOrNull() ?: return null
        return WeeklyGoal(type, target)
    }

    fun setGoal(goal: WeeklyGoal) {
        prefs.edit {
            putString(KEY_TYPE, goal.type.name)
            putInt(KEY_TARGET, goal.target)
        }
    }

    companion object {
        private const val PREFS_NAME = "calm_otter_weekly_goal"
        private const val KEY_TYPE = "goal_type"
        private const val KEY_TARGET = "goal_target"

        /** L'istanza dell'app: vive in [AppGraph], una per Application (vedi CalmOtterApplication). */
        fun getInstance(context: Context): WeeklyGoalManager = context.appGraph.weeklyGoalManager
    }
}
