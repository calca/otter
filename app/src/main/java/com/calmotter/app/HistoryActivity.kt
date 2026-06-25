package com.calmotter.app

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HistoryActivity : BaseActivity() {

    override val themeVariant = ThemeVariant.WITH_ACTION_BAR

    private lateinit var historyManager: SessionHistoryManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        supportActionBar?.apply {
            title = getString(R.string.history_title)
            setDisplayHomeAsUpEnabled(true)
        }

        historyManager = SessionHistoryManager(applicationContext)
        bindAll()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, MENU_CLEAR, 0, getString(R.string.history_clear))
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> { finish(); true }
        MENU_CLEAR        -> { confirmClear(); true }
        else              -> super.onOptionsItemSelected(item)
    }

    // ── Binding principale ────────────────────────────────────────────────

    private fun bindAll() {
        val sessions = historyManager.getAll()
        val isEmpty  = sessions.isEmpty()

        findViewById<View>(R.id.statsBar).visibility       = if (isEmpty) View.GONE else View.VISIBLE
        findViewById<View>(R.id.weeklyChart).visibility    = if (isEmpty) View.GONE else View.VISIBLE
        findViewById<View>(R.id.weeklySummary).visibility  = if (isEmpty) View.GONE else View.VISIBLE
        findViewById<View>(R.id.historyList).visibility    = if (isEmpty) View.GONE else View.VISIBLE
        findViewById<View>(R.id.emptyView).visibility      = if (isEmpty) View.VISIBLE else View.GONE

        if (isEmpty) return

        bindStats(sessions)
        bindWeeklyChart(sessions)
        bindList(sessions)
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

        // Sommario settimana corrente
        val weekSessions = sessionsByDay.sum()
        val weekMinutes  = minutesByDay.sum()
        val summaryText  = when {
            weekSessions == 0 -> getString(R.string.weekly_summary_none)
            weekSessions == 1 -> getString(R.string.weekly_summary_one, formatMinutes(weekMinutes))
            else              -> getString(R.string.weekly_summary_many, weekSessions, formatMinutes(weekMinutes))
        }
        findViewById<TextView>(R.id.weeklySummary).text = summaryText
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
    }
}
