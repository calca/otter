package com.calmotter.app

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import com.calmotter.app.ui.screens.ClearHistoryConfirmDialog
import com.calmotter.app.ui.screens.HistoryScreen
import com.calmotter.app.ui.screens.WeeklyGoalDialog
import com.calmotter.app.ui.theme.CalmOtterTheme
import java.io.File

/**
 * Cronologia sessioni (ultimo step della migrazione a Compose).
 *
 * Il corpo della schermata (statistiche, streak, grafico, obiettivo, lista)
 * vive in HistoryScreen.kt/WeeklyChart.kt; qui restano la chrome
 * dell'ActionBar (menu opzioni) e lo stato "mostra dialog" per i due dialog
 * che vivono in HistoryScreen.kt (`WeeklyGoalDialog`, `ClearHistoryConfirmDialog`)
 * — entrambi Compose Material3 ora, non più `AlertDialog.Builder` + View
 * native (RadioGroup/EditText/LinearLayout), uniformati al resto dell'app
 * su richiesta esplicita.
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
    private var showGoalDialog by mutableStateOf(false)
    private var showClearConfirmDialog by mutableStateOf(false)

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
                    onEditGoal = { showGoalDialog = true },
                )

                if (showGoalDialog) {
                    WeeklyGoalDialog(
                        currentGoal = goal,
                        onDismiss = { showGoalDialog = false },
                        onSave = { type, target ->
                            weeklyGoalManager.setGoal(WeeklyGoal(type, target))
                            goal = weeklyGoalManager.getGoal()
                            showGoalDialog = false
                        },
                    )
                }

                if (showClearConfirmDialog) {
                    ClearHistoryConfirmDialog(
                        onDismiss = { showClearConfirmDialog = false },
                        onConfirm = {
                            historyManager.clear()
                            sessions = historyManager.getAll()
                            showClearConfirmDialog = false
                        },
                    )
                }
            }
        }
    }

    /**
     * Due sole azioni, entrambe come icona sempre visibile invece che dentro
     * l'overflow "⋮": con due voci soltanto, il menu a tendina costava un tap
     * in più per nascondere quello che ci sta comodamente in barra.
     *
     * Il titolo passato a `menu.add` resta quello di prima e non è ridondante
     * ora che c'è un'icona: Android lo usa come tooltip sulla pressione lunga
     * e come etichetta per TalkBack, quindi le icone non restano mute per chi
     * non le riconosce o non le vede.
     */
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, MENU_EXPORT, 0, getString(R.string.history_export))
            .setIcon(R.drawable.ic_history_download)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        menu.add(0, MENU_CLEAR, 1, getString(R.string.history_clear))
            .setIcon(R.drawable.ic_history_delete)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> { finish(); true }
        MENU_EXPORT       -> { exportHistory(); true }
        MENU_CLEAR        -> { showClearConfirmDialog = true; true }
        else              -> super.onOptionsItemSelected(item)
    }

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

    companion object {
        private const val MENU_CLEAR = 1
        private const val MENU_EXPORT = 2
    }
}
