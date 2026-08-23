package com.calmotter.app

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AlertDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import com.calmotter.app.ui.screens.HistoryScreen
import com.calmotter.app.ui.theme.CalmOtterTheme
import java.io.File

/**
 * Cronologia sessioni (ultimo step della migrazione a Compose).
 *
 * Il corpo della schermata (statistiche, streak, grafico, obiettivo, lista)
 * vive in HistoryScreen.kt/WeeklyChart.kt; qui restano solo la chrome
 * dell'ActionBar (menu opzioni) e i due dialog costruiti con
 * AlertDialog.Builder — quello dell'obiettivo settimanale (RadioGroup +
 * EditText numerico, misto) e quello di conferma svuotamento — stesso
 * pattern già usato da MainActivity/AllowedAppsActivity per i dialog non
 * convertiti a Compose.
 *
 * Nessun resumeSignal: a differenza di MainScreen/OnboardingScreen, questa
 * schermata non dipende da stato del sistema operativo che può cambiare
 * mentre l'Activity è in background — bindAll() nella versione precedente
 * veniva chiamato solo da onCreate() e dopo lo svuotamento della cronologia,
 * mai da onResume(), e questo Activity riproduce lo stesso comportamento.
 */
class HistoryActivity : BaseActivity() {

    override val themeVariant = ThemeVariant.WITH_ACTION_BAR

    private lateinit var historyManager: SessionHistoryManager
    private lateinit var weeklyGoalManager: WeeklyGoalManager

    private var sessions by mutableStateOf<List<SessionRecord>>(emptyList())
    private var goal by mutableStateOf<WeeklyGoal?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.apply {
            title = getString(R.string.history_title)
            setDisplayHomeAsUpEnabled(true)
        }

        historyManager = SessionHistoryManager.getInstance(applicationContext)
        weeklyGoalManager = WeeklyGoalManager.getInstance(applicationContext)

        sessions = historyManager.getAll()
        goal = weeklyGoalManager.getGoal()

        setContent {
            CalmOtterTheme(appTheme = ThemeManager.getTheme(this)) {
                HistoryScreen(
                    sessions = sessions,
                    goal = goal,
                    onEditGoal = { showGoalDialog(goal) },
                )
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, MENU_EXPORT, 0, getString(R.string.history_export))
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(0, MENU_CLEAR, 1, getString(R.string.history_clear))
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> { finish(); true }
        MENU_EXPORT       -> { exportHistory(); true }
        MENU_CLEAR        -> { confirmClear(); true }
        else              -> super.onOptionsItemSelected(item)
    }

    // ── Obiettivo settimanale ────────────────────────────────────────────

    private fun showGoalDialog(current: WeeklyGoal?) {
        val density = resources.displayMetrics.density
        val padding = (16 * density).toInt()

        val typeGroup = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
        }
        val sessionsRadio = RadioButton(this).apply {
            id = View.generateViewId()
            text = getString(R.string.weekly_goal_type_sessions)
        }
        val minutesRadio = RadioButton(this).apply {
            id = View.generateViewId()
            text = getString(R.string.weekly_goal_type_minutes)
        }
        typeGroup.addView(sessionsRadio)
        typeGroup.addView(minutesRadio)

        val defaultType = current?.type ?: GoalType.SESSIONS
        if (defaultType == GoalType.SESSIONS) sessionsRadio.isChecked = true else minutesRadio.isChecked = true

        val targetInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.weekly_goal_target_hint)
            if (current != null) setText(current.target.toString())
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            addView(typeGroup)
            addView(targetInput)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.weekly_goal_dialog_title)
            .setView(container)
            .setPositiveButton(R.string.save) { _, _ ->
                val target = targetInput.text.toString().toIntOrNull()
                if (target == null || target <= 0) {
                    Toast.makeText(this, R.string.weekly_goal_invalid, Toast.LENGTH_SHORT).show()
                } else {
                    val type = if (sessionsRadio.isChecked) GoalType.SESSIONS else GoalType.MINUTES
                    weeklyGoalManager.setGoal(WeeklyGoal(type, target))
                    goal = weeklyGoalManager.getGoal()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ── Utility ───────────────────────────────────────────────────────────

    private fun exportHistory() {
        val current = historyManager.getAll()
        if (current.isEmpty()) {
            Toast.makeText(this, R.string.history_export_empty, Toast.LENGTH_SHORT).show()
            return
        }

        val csv = SessionCsvExporter.toCsv(current)
        val file = File(cacheDir, "calm_otter_history.csv")
        file.writeText(csv)

        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(sendIntent, getString(R.string.history_export)))
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle(R.string.history_clear)
            .setMessage(R.string.history_clear_confirm)
            .setPositiveButton(R.string.confirm) { _, _ ->
                historyManager.clear()
                sessions = historyManager.getAll()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    companion object {
        private const val MENU_CLEAR = 1
        private const val MENU_EXPORT = 2
    }
}
