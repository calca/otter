package com.calmotter.app

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HistoryActivity : BaseActivity() {

    override val themeVariant = ThemeVariant.WITH_ACTION_BAR

    private lateinit var historyManager: SessionHistoryManager
    private lateinit var weeklyGoalManager: WeeklyGoalManager

    // Aggregati della settimana corrente, calcolati in bindWeeklyChart() e
    // riutilizzati da bindWeeklyGoal() per il progresso rispetto all'obiettivo.
    private var weekSessions = 0
    private var weekMinutes = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        supportActionBar?.apply {
            title = getString(R.string.history_title)
            setDisplayHomeAsUpEnabled(true)
        }

        historyManager = SessionHistoryManager.getInstance(applicationContext)
        weeklyGoalManager = WeeklyGoalManager.getInstance(applicationContext)
        bindAll()
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

    // ── Binding principale ────────────────────────────────────────────────

    private fun bindAll() {
        val sessions = historyManager.getAll()
        val isEmpty  = sessions.isEmpty()

        findViewById<View>(R.id.statsBar).visibility           = if (isEmpty) View.GONE else View.VISIBLE
        findViewById<View>(R.id.weeklyChart).visibility        = if (isEmpty) View.GONE else View.VISIBLE
        findViewById<View>(R.id.weeklySummary).visibility      = if (isEmpty) View.GONE else View.VISIBLE
        findViewById<View>(R.id.weeklyGoalSection).visibility  = if (isEmpty) View.GONE else View.VISIBLE
        findViewById<View>(R.id.historyList).visibility        = if (isEmpty) View.GONE else View.VISIBLE
        findViewById<View>(R.id.emptyView).visibility          = if (isEmpty) View.VISIBLE else View.GONE

        if (isEmpty) {
            findViewById<View>(R.id.streakText).visibility = View.GONE
            return
        }

        bindStats(sessions)
        bindStreak(sessions)
        bindWeeklyChart(sessions)
        bindWeeklyGoal()
        bindList(sessions)
    }

    // ── Serie giornaliera (streak) ──────────────────────────────────────────

    private fun bindStreak(sessions: List<SessionRecord>) {
        val streak = SessionStreak.currentStreakDays(sessions)
        val streakView = findViewById<TextView>(R.id.streakText)
        if (streak >= 1) {
            streakView.text = getString(R.string.streak_days, streak)
            streakView.visibility = View.VISIBLE
        } else {
            streakView.visibility = View.GONE
        }
    }

    // ── Statistiche totali ────────────────────────────────────────────────

    private fun bindStats(sessions: List<SessionRecord>) {
        val total          = sessions.size
        val totalMinutes   = sessions.sumOf { it.effectiveMinutes }
        val completedCount = sessions.count { it.completedNaturally }

        findViewById<TextView>(R.id.statTotalSessions).text = total.toString()
        findViewById<TextView>(R.id.statTotalMinutes).text  = formatMinutes(totalMinutes)
        findViewById<TextView>(R.id.statCompleted).text     =
            getString(R.string.stat_completed_fraction, completedCount, total)
    }

    // ── Grafico e sommario settimanale ────────────────────────────────────

    private fun bindWeeklyChart(sessions: List<SessionRecord>) {
        // Costruisce un array di 7 slot: slot[0] = 6 giorni fa, slot[6] = oggi
        val minutesByDay = IntArray(7)
        val sessionsByDay = IntArray(7)
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        for (session in sessions) {
            val sessionDay = Calendar.getInstance().apply {
                timeInMillis = session.startTimeMs
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val diffDays = ((today.timeInMillis - sessionDay.timeInMillis) /
                    (1000 * 60 * 60 * 24)).toInt()
            if (diffDays in 0..6) {
                val slot = 6 - diffDays
                minutesByDay[slot]  += session.effectiveMinutes
                sessionsByDay[slot] += 1
            }
        }

        findViewById<WeeklyChartView>(R.id.weeklyChart).data = minutesByDay

        // Sommario settimana corrente (i totali sono riusati da bindWeeklyGoal())
        weekSessions = sessionsByDay.sum()
        weekMinutes  = minutesByDay.sum()
        val summaryText  = when {
            weekSessions == 0 -> getString(R.string.weekly_summary_none)
            weekSessions == 1 -> getString(R.string.weekly_summary_one, formatMinutes(weekMinutes))
            else              -> getString(R.string.weekly_summary_many, weekSessions, formatMinutes(weekMinutes))
        }
        findViewById<TextView>(R.id.weeklySummary).text = summaryText
    }

    // ── Obiettivo settimanale ────────────────────────────────────────────

    private fun bindWeeklyGoal() {
        val goal = weeklyGoalManager.getGoal()
        val progressBar  = findViewById<ProgressBar>(R.id.goalProgressBar)
        val progressText = findViewById<TextView>(R.id.goalProgressText)
        val goalButton   = findViewById<Button>(R.id.setGoalButton)

        if (goal == null) {
            progressBar.visibility  = View.GONE
            progressText.visibility = View.GONE
            goalButton.text = getString(R.string.weekly_goal_set_button)
        } else {
            val current = if (goal.type == GoalType.SESSIONS) weekSessions else weekMinutes
            val percent = (current * 100 / goal.target).coerceIn(0, 100)

            progressBar.progress    = percent
            progressBar.visibility  = View.VISIBLE
            progressText.text = when (goal.type) {
                GoalType.SESSIONS -> getString(R.string.weekly_goal_progress_sessions, current, goal.target)
                GoalType.MINUTES  -> getString(R.string.weekly_goal_progress_minutes, current, goal.target)
            }
            progressText.visibility = View.VISIBLE
            goalButton.text = getString(R.string.weekly_goal_edit_button)
        }

        goalButton.setOnClickListener { showGoalDialog(goal) }
    }

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
                    bindWeeklyGoal()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ── Lista sessioni ────────────────────────────────────────────────────

    private fun bindList(sessions: List<SessionRecord>) {
        val recyclerView = findViewById<RecyclerView>(R.id.historyList)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.itemAnimator  = null
        recyclerView.adapter       = SessionAdapter(sessions)
    }

    inner class SessionAdapter(
        private val items: List<SessionRecord>
    ) : RecyclerView.Adapter<SessionAdapter.VH>() {

        // Formatter con giorno della settimana esteso
        private val dateFmt = SimpleDateFormat("EEEE d MMM · HH:mm", Locale.ITALY)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = layoutInflater.inflate(R.layout.item_session, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])
        override fun getItemCount() = items.size

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            private val indicator = view.findViewById<View>(R.id.outcomeIndicator)
            private val dateText  = view.findViewById<TextView>(R.id.dateText)
            private val detail    = view.findViewById<TextView>(R.id.detailText)
            private val duration  = view.findViewById<TextView>(R.id.durationText)

            fun bind(r: SessionRecord) {
                // Giorno della settimana con prima lettera maiuscola
                val rawDate = dateFmt.format(Date(r.startTimeMs))
                dateText.text = rawDate.replaceFirstChar { it.uppercase() }

                detail.text = if (r.completedNaturally)
                    getString(R.string.history_item_natural)
                else
                    getString(R.string.history_item_early, r.plannedMinutes)

                duration.text = formatMinutes(r.effectiveMinutes)

                indicator.setBackgroundResource(
                    if (r.completedNaturally) R.drawable.dot_active else R.drawable.dot_early
                )
            }
        }
    }

    // ── Utility ───────────────────────────────────────────────────────────

    private fun exportHistory() {
        val sessions = historyManager.getAll()
        if (sessions.isEmpty()) {
            Toast.makeText(this, R.string.history_export_empty, Toast.LENGTH_SHORT).show()
            return
        }

        val csv = SessionCsvExporter.toCsv(sessions)
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
            .setPositiveButton(R.string.confirm) { _, _ -> historyManager.clear(); bindAll() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun formatMinutes(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0 -> "${m}m"
            m == 0 -> "${h}h"
            else   -> "${h}h ${m}m"
        }
    }

    companion object {
        private const val MENU_CLEAR = 1
        private const val MENU_EXPORT = 2
    }
}
