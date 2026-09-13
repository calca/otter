package com.calmotter.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
private val historyDateFormat = SimpleDateFormat("EEEE d MMM · HH:mm", Locale.ITALY)

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
        StatsBar(sessions = sessions)

        // Tutto il resto in scroll — equivalente del NestedScrollView.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            if (streak >= 1) {
                Text(
                    text = stringResource(R.string.streak_days, streak),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 16.dp, end = 16.dp)
                )
            }

            WeeklyChart(
                data = minutesByDay,
                modifier = Modifier.padding(top = 16.dp, start = 8.dp, end = 8.dp)
            )

            Text(
                text = summaryText,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp)
            )

            WeeklyGoalSection(
                goal = goal,
                weekSessions = weekSessions,
                weekMinutes = weekMinutes,
                onEditGoal = onEditGoal,
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            Text(
                text = stringResource(R.string.history_all_sessions),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 4.dp)
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
private fun StatsBar(sessions: List<SessionRecord>) {
    val total = sessions.size
    val totalMinutes = sessions.sumOf { it.effectiveMinutes }
    val completedCount = sessions.count { it.completedNaturally }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
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

// ── Obiettivo settimanale ────────────────────────────────────────────────

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
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (goal != null) {
            val current = if (goal.type == GoalType.SESSIONS) weekSessions else weekMinutes
            val percent = (current * 100 / goal.target).coerceIn(0, 100)

            LinearProgressIndicator(
                progress = { percent / 100f },
                color = MaterialTheme.colorScheme.primary,
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

        TextButton(onClick = onEditGoal) {
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
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

/**
 * Editor dell'obiettivo settimanale: tipo (sessioni/minuti) via due
 * `RadioButton` affiancati, target numerico via `OutlinedTextField`. Colori
 * del RadioButton ristretti a `primary`/`onSurface` — `RadioButtonDefaults`
 * userebbe altrimenti `onSurfaceVariant` per lo stato non selezionato, un
 * ruolo non personalizzato per palette (stessa trappola documentata in
 * CLAUDE.md/specs/mascot-marks per altri componenti).
 */
@Composable
fun WeeklyGoalDialog(
    currentGoal: WeeklyGoal?,
    onDismiss: () -> Unit,
    onSave: (GoalType, Int) -> Unit,
) {
    var selectedType by remember { mutableStateOf(currentGoal?.type ?: GoalType.SESSIONS) }
    var targetText by remember { mutableStateOf(currentGoal?.target?.toString() ?: "") }
    var errorText by remember { mutableStateOf("") }

    val invalidText = stringResource(R.string.weekly_goal_invalid)
    val radioColors = RadioButtonDefaults.colors(
        selectedColor = MaterialTheme.colorScheme.primary,
        unselectedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.weekly_goal_dialog_title)) },
        text = {
            Column {
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf(
                        GoalType.SESSIONS to stringResource(R.string.weekly_goal_type_sessions),
                        GoalType.MINUTES to stringResource(R.string.weekly_goal_type_minutes),
                    ).forEach { (type, label) ->
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .selectable(
                                    selected = selectedType == type,
                                    onClick = { selectedType = type },
                                    role = Role.RadioButton,
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selectedType == type, onClick = null, colors = radioColors)
                            Text(label)
                        }
                    }
                }
                OutlinedTextField(
                    value = targetText,
                    onValueChange = {
                        targetText = it
                        errorText = ""
                    },
                    label = { Text(stringResource(R.string.weekly_goal_target_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )
                if (errorText.isNotBlank()) {
                    Text(
                        text = errorText,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val target = targetText.toIntOrNull()
                    if (target == null || target <= 0) {
                        errorText = invalidText
                    } else {
                        onSave(selectedType, target)
                    }
                }
            ) {
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
