package com.calmotter.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calmotter.app.GoalType
import com.calmotter.app.R
import com.calmotter.app.SessionRecord
import com.calmotter.app.SessionStreak
import com.calmotter.app.WeeklyGoal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Formatter con giorno della settimana esteso — un'unica istanza riusata per
// tutte le righe, stesso pattern del vecchio SessionAdapter (uso esclusivo
// dal thread main durante la composizione, nessun problema di concorrenza).
// Locale.getDefault(), non Locale.ITALY: era hardcoded in italiano, quindi
// ogni riga mostrava sempre "Domenica 13 set" anche con l'app in inglese
// (dove il resto della schermata usa correttamente values-en/strings.xml) —
// stesso bug di WeeklyChart.kt ("Lu"/"Ma"/... e "oggi" hardcoded).
private val historyDateFormat = SimpleDateFormat("EEEE d MMM · HH:mm", Locale.getDefault())

/**
 * Schermata cronologia (ultimo step della migrazione a Compose — il più
 * articolato: statistiche, streak, grafico settimanale disegnato a mano,
 * obiettivo opzionale con dialog, lista sessioni, stato vuoto).
 *
 * Riproduce esattamente la struttura della vecchia activity_history.xml:
 * la barra statistiche resta fissa in alto (non scrolla); tutto il resto —
 * streak, grafico, sommario, obiettivo, divisore, etichetta ed elenco
 * sessioni — scorre insieme in un'unica regione, dato che il RecyclerView
 * originale aveva nestedScrollingEnabled="false" apposta per scorrere come
 * parte del NestedScrollView circostante e non in modo indipendente. Qui le
 * righe sessione sono quindi composable semplici dentro la Column scrollabile
 * (sessions.forEach { ... }), NON una LazyColumn annidata — una LazyColumn
 * dentro una Column scrollabile è la traduzione sbagliata per questo layout.
 *
 * Nessun refresh legato a onResume(): la vecchia bindAll() veniva chiamata
 * solo da onCreate() e dopo lo svuotamento della cronologia, mai da
 * onResume() — comportamento volutamente preservato qui.
 */
@Composable
fun HistoryScreen(
    sessions: List<SessionRecord>,
    goal: WeeklyGoal?,
    onEditGoal: () -> Unit,
) {
    if (sessions.isEmpty()) {
        EmptyHistory()
        return
    }

    val streak = SessionStreak.currentStreakDays(sessions)
    val (minutesByDay, weekSessions, weekMinutes) = weeklyChartData(sessions)

    val summaryText = when {
        weekSessions == 0 -> stringResource(R.string.weekly_summary_none)
        weekSessions == 1 -> stringResource(R.string.weekly_summary_one, formatMinutes(weekMinutes))
        else -> stringResource(R.string.weekly_summary_many, weekSessions, formatMinutes(weekMinutes))
    }

    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        StatsBar(
            sessions = sessions,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )

        // Tutto il resto in scroll — equivalente del NestedScrollView.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            WeekOverviewCard(
                streak = streak,
                minutesByDay = minutesByDay,
                summaryText = summaryText,
                goal = goal,
                weekSessions = weekSessions,
                weekMinutes = weekMinutes,
                onEditGoal = onEditGoal,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            Text(
                text = stringResource(R.string.history_all_sessions),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 4.dp)
            )

            sessions.forEach { session ->
                SessionRow(session)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ── Statistiche totali ──────────────────────────────────────────────────

@Composable
private fun StatsBar(sessions: List<SessionRecord>, modifier: Modifier = Modifier) {
    val total = sessions.size
    val totalMinutes = sessions.sumOf { it.effectiveMinutes }
    val completedCount = sessions.count { it.completedNaturally }

    // Card tinta invece della precedente barra piatta su colorScheme.surface
    // (coincide con background nella palette chiara, vedi CalmOtterTheme.kt —
    // la stessa "trappola" già corretta in AllowedAppsScreen.kt) — stesso
    // linguaggio a card delle sezioni di SettingsScreen.kt.
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
        modifier = modifier,
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            StatColumn(
                value = total.toString(),
                label = stringResource(R.string.stat_sessions),
                modifier = Modifier.weight(1f)
            )
            StatColumn(
                value = formatMinutes(totalMinutes),
                label = stringResource(R.string.stat_minutes),
                modifier = Modifier.weight(1f)
            )
            StatColumn(
                value = stringResource(R.string.stat_completed_fraction, completedCount, total),
                label = stringResource(R.string.stat_completed),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatColumn(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ── Streak + grafico settimanale + obiettivo (card unica) ────────────────

/**
 * Streak, grafico settimanale, sommario e obiettivo raggruppati in un'unica
 * card tinta — stesso linguaggio delle sezioni di SettingsScreen.kt (Surface
 * arrotondata + divisore tenue tra sottosezioni) invece di lasciarli sciolti
 * sulla pagina come prima di questo redesign.
 */
@Composable
private fun WeekOverviewCard(
    streak: Int,
    minutesByDay: IntArray,
    summaryText: String,
    goal: WeeklyGoal?,
    weekSessions: Int,
    weekMinutes: Int,
    onEditGoal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            if (streak >= 1) {
                Text(
                    text = stringResource(R.string.streak_days, streak),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            WeeklyChart(
                data = minutesByDay,
                modifier = Modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp)
            )

            Text(
                text = summaryText,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            WeeklyGoalSection(
                goal = goal,
                weekSessions = weekSessions,
                weekMinutes = weekMinutes,
                onEditGoal = onEditGoal,
            )
        }
    }
}

@Composable
private fun WeeklyGoalSection(
    goal: WeeklyGoal?,
    weekSessions: Int,
    weekMinutes: Int,
    onEditGoal: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (goal != null) {
            val current = if (goal.type == GoalType.SESSIONS) weekSessions else weekMinutes
            val percent = (current * 100 / goal.target).coerceIn(0, 100)

            LinearProgressIndicator(
                progress = { percent / 100f },
                color = MaterialTheme.colorScheme.primary,
                // trackColor esplicito: il default di LinearProgressIndicator
                // legge surfaceVariant, un ruolo NON personalizzato per
                // palette in CalmOtterTheme.kt — cadeva sul lavanda/viola di
                // base di Material3 a prescindere dal tema scelto (stessa
                // trappola di Switch/Checkbox/AlertDialog altrove nell'app).
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            )

            Text(
                text = when (goal.type) {
                    GoalType.SESSIONS -> stringResource(R.string.weekly_goal_progress_sessions, current, goal.target)
                    GoalType.MINUTES -> stringResource(R.string.weekly_goal_progress_minutes, current, goal.target)
                },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
        }

        // FilledTonalButton, non TextButton: azione con più peso visivo,
        // coerente con le altre call-to-action dell'app. Colori espliciti:
        // ButtonDefaults.filledTonalButtonColors() di default userebbe
        // secondaryContainer/onSecondaryContainer, ruoli NON personalizzati
        // per palette in CalmOtterTheme.kt (stessa trappola di
        // surfaceVariant/onSurfaceVariant già documentata altrove).
        FilledTonalButton(
            onClick = onEditGoal,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                contentColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Text(
                text = stringResource(
                    if (goal == null) R.string.weekly_goal_set_button else R.string.weekly_goal_edit_button
                )
            )
        }
    }
}

// ── Lista sessioni ──────────────────────────────────────────────────────

@Composable
private fun SessionRow(session: SessionRecord) {
    // Card tinta invece di background(colorScheme.surface): nella palette
    // chiara surface coincide con background (vedi CalmOtterTheme.kt), quindi
    // ogni riga era visivamente indistinguibile dalla pagina — stessa
    // "trappola" già corretta in AllowedAppsScreen.kt.
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Indicatore esito: cerchio colorato — riproduzione nativa degli
            // shape drawable dot_active/dot_early (oval piatte statiche, stesso
            // precedente di StepIndicator in OnboardingScreen), niente AndroidView.
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        if (session.completedNaturally) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp)
            ) {
                val rawDate = historyDateFormat.format(Date(session.startTimeMs))
                Text(
                    text = rawDate.replaceFirstChar { it.uppercase() },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = if (session.completedNaturally)
                        stringResource(R.string.history_item_natural)
                    else
                        stringResource(R.string.history_item_early, session.plannedMinutes),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Text(
                text = formatMinutes(session.effectiveMinutes),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// ── Stato vuoto ──────────────────────────────────────────────────────────

@Composable
private fun EmptyHistory() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.history_empty),
            fontSize = 16.sp,
            lineHeight = 24.sp,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
}

// ── Utility (calcolo puro, non legato a Compose) ─────────────────────────

/**
 * Costruisce l'array a 7 slot dei minuti di pausa per giorno (slot[0] = 6
 * giorni fa, slot[6] = oggi) e i totali della settimana corrente, riusati
 * sia dal sommario che dalla sezione obiettivo. Logica invariata rispetto
 * alla vecchia bindWeeklyChart() — pura aritmetica su Calendar, indipendente
 * da Compose.
 */
private fun weeklyChartData(sessions: List<SessionRecord>): Triple<IntArray, Int, Int> {
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
            minutesByDay[slot] += session.effectiveMinutes
            sessionsByDay[slot] += 1
        }
    }

    return Triple(minutesByDay, sessionsByDay.sum(), minutesByDay.sum())
}

private fun formatMinutes(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "${m}m"
        m == 0 -> "${h}h"
        else -> "${h}h ${m}m"
    }
}

// ── Dialog dell'obiettivo settimanale e di conferma svuotamento ──────────
//
// Prima costruiti in HistoryActivity con AlertDialog.Builder + View native
// (RadioGroup, EditText, LinearLayout) — non Material, uniformati agli
// altri dialog Compose dell'app (vedi PasswordVerifyDialog.kt) su richiesta
// esplicita, dopo che lo stesso era già stato fatto per il prompt password
// di Settings. HistoryActivity ora possiede solo i due booleani
// "show dialog" e cosa fare a salvataggio/conferma riusciti — tutto lo
// stato del form (tipo obiettivo, testo del target, errore di validazione)
// vive qui dentro, non hoisted, per lo stesso motivo di PasswordVerifyDialog:
// essendo invocati solo dentro un `if (show) { ... }`, Compose distrugge il
// loro `remember` alla chiusura, quindi riaprirli parte sempre pulito senza
// reset manuali.

// Preset del target, non un campo numerico libero (vedi doc di
// [WeeklyGoalDialog]): partono da una soglia già "di minimo impegno" invece
// che da 1, su richiesta esplicita ("un minimo di sfida!"), e crescono a
// passi via via più larghi — stessa progressione "shape" per entrambi i tipi
// (5 preset, differenze crescenti) pur partendo da soglie diverse.
private val SESSION_GOAL_PRESETS = listOf(3, 5, 7, 10, 14)
private val MINUTE_GOAL_PRESETS = listOf(120, 180, 300, 420, 600) // 2h/3h/5h/7h/10h

private fun goalPresetsFor(type: GoalType) =
    if (type == GoalType.SESSIONS) SESSION_GOAL_PRESETS else MINUTE_GOAL_PRESETS

/**
 * Editor dell'obiettivo settimanale: tipo (sessioni/minuti) e target sono
 * entrambi righe di chip pillola — un solo idioma di selezione in tutto il
 * dialog, invece di RadioButton per il tipo e chip per il target. Le
 * `RadioButton` iniziali sono state sostituite su richiesta diretta
 * ("invece dei radio button, si può fare di meglio?"): due cerchietti +
 * etichetta pesano visivamente di più di una pillola, e mischiavano due
 * idiomi di selezione diversi nello stesso dialog. Stesso pattern a pillole
 * di [DurationChipRow] in MainScreen.kt (Home usa lo stesso linguaggio per
 * scegliere la durata della pausa). I preset del target partono da 3
 * sessioni / 2h, non da 1, su richiesta ("un minimo di sfida!") — un
 * obiettivo "1 sessione a settimana" sarebbe banale da centrare comunque.
 */
@Composable
fun WeeklyGoalDialog(
    currentGoal: WeeklyGoal?,
    onDismiss: () -> Unit,
    onSave: (GoalType, Int) -> Unit,
) {
    var selectedType by remember { mutableStateOf(currentGoal?.type ?: GoalType.SESSIONS) }
    var selectedTarget by remember {
        mutableStateOf(currentGoal?.target?.takeIf { it in goalPresetsFor(selectedType) }
            ?: goalPresetsFor(selectedType).first())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.weekly_goal_dialog_title)) },
        text = {
            Column {
                GoalChipRow(
                    items = listOf(
                        GoalType.SESSIONS to stringResource(R.string.weekly_goal_type_sessions),
                        GoalType.MINUTES to stringResource(R.string.weekly_goal_type_minutes),
                    ),
                    selected = selectedType,
                    onSelect = { type ->
                        selectedType = type
                        val presets = goalPresetsFor(type)
                        if (selectedTarget !in presets) selectedTarget = presets.first()
                    },
                    scrollable = false,
                )
                GoalChipRow(
                    items = goalPresetsFor(selectedType).map { target ->
                        val label = if (selectedType == GoalType.SESSIONS) target.toString() else formatMinutes(target)
                        target to label
                    },
                    selected = selectedTarget,
                    onSelect = { selectedTarget = it },
                    scrollable = true,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(selectedType, selectedTarget) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

/**
 * Riga di chip pillola generica, riusata sia per il tipo (2 voci, non
 * scorrevole) sia per il target (5 preset, scorrevole in orizzontale) — vedi
 * doc di [WeeklyGoalDialog]. Stesso stile di `DurationChipRow` in
 * MainScreen.kt (privata a quel file, da qui la duplicazione): sfondo
 * `primary` a bassa opacità invece di `FilterChip` di M3, i cui colori di
 * stato leggono ruoli non personalizzati per palette.
 */
@Composable
private fun <T> GoalChipRow(
    items: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    scrollable: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .let { if (scrollable) it.horizontalScroll(rememberScrollState()) else it },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { (value, label) ->
            val isSelected = value == selected
            Surface(
                onClick = { onSelect(value) },
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primary.copy(alpha = if (isSelected) 0.22f else 0.08f),
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isSelected) 1f else 0.65f),
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

/** Conferma prima di svuotare la cronologia — operazione non reversibile. */
@Composable
fun ClearHistoryConfirmDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.history_clear)) },
        text = { Text(stringResource(R.string.history_clear_confirm)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
