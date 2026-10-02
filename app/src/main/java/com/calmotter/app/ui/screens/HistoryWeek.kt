package com.calmotter.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calmotter.app.GoalType
import com.calmotter.app.R
import com.calmotter.app.SessionRecord
import com.calmotter.app.WeeklyGoal
import com.calmotter.app.ui.mascot.SprigMark
import java.util.Calendar

// La parte "settimana" della Cronologia: il riepilogo con il grafico, la
// sezione dell'obiettivo settimanale e il dialogo per sceglierlo. Spostata
// qui da HistoryScreen.kt (spacchettato come già MainScreen.kt): codice
// spostato, non riscritto.

/**
 * Streak, grafico settimanale e sommario in un'unica card tinta. L'obiettivo
 * stava in fondo a questa card (barra, testo e un bottone sotto il grafico):
 * segnalato come "bruttino", ora ha una card sua, [WeeklyGoalCard], subito
 * dopo i totali.
 */
@Composable
internal fun WeekOverviewCard(
    streak: Int,
    minutesByDay: IntArray,
    summaryText: String,
    modifier: Modifier = Modifier,
    now: Long = System.currentTimeMillis(),
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            if (streak >= 1) {
                // Titolo del grafico, non solo un dato in più: segnalato
                // senza icona e con un font troppo piccolo per il ruolo che
                // ha (è l'intestazione di tutto ciò che segue — grafico,
                // sommario, obiettivo). SprigMark, non un'icona Material:
                // stesso motivo grafico che questa app usa già per "cosa è
                // fatto di natura" (Impostazioni → Esperienza di pausa),
                // niente artefatto icone esteso da tirarsi dentro per una
                // singola foglia.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    SprigMark(markSize = 16.dp)
                    Text(
                        text = pluralStringResource(R.plurals.streak_days, streak, streak),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                }
            }

            WeeklyChart(
                now = now,
                data = minutesByDay,
                modifier = Modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp),
                // Stessa frase del Text subito sotto (summaryText) — vedi il
                // commento di classe su WeeklyChart per il perché.
                accessibilityLabel = summaryText,
            )

            Text(
                text = summaryText,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    // Vicino al grafico che descrive.
                    .padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 8.dp)
            )
        }
    }
}

/**
 * L'obiettivo settimanale, in una card sua dopo i totali, con la forma delle
 * righe di Impostazioni: icona nella pastiglia tinta, "Obiettivo
 * settimanale", sotto lo stato ("3 di 5 pause" o "nessun obiettivo"), freccia;
 * tutta la riga apre il dialogo. Con un obiettivo, la barra di avanzamento
 * sotto. Prima stava in fondo alla card del grafico con un bottone tonale.
 */
@Composable
internal fun WeeklyGoalCard(
    goal: WeeklyGoal?,
    weekSessions: Int,
    weekMinutes: Int,
    onEditGoal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = when (goal?.type) {
        GoalType.SESSIONS -> weekSessions
        GoalType.MINUTES -> weekMinutes
        null -> 0
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .clickable(
                    onClickLabel = stringResource(
                        if (goal == null) R.string.weekly_goal_set_button else R.string.weekly_goal_edit_button
                    ),
                    onClick = onEditGoal,
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SettingsRowIcon {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.weekly_goal_dialog_title),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        // Quantity su goal.target, non su current: è quello
                        // con cui "sessioni"/"minuti" concorda
                        // grammaticalmente — vedi la risorsa in strings.xml.
                        text = when (goal?.type) {
                            GoalType.SESSIONS -> pluralStringResource(R.plurals.weekly_goal_progress_sessions, goal.target, current, goal.target)
                            GoalType.MINUTES -> pluralStringResource(R.plurals.weekly_goal_progress_minutes, goal.target, current, goal.target)
                            null -> stringResource(R.string.weekly_goal_none)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    )
                }
                Text(
                    text = "→",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (goal != null) {
                LinearProgressIndicator(
                    progress = { (current.toFloat() / goal.target).coerceIn(0f, 1f) },
                    color = MaterialTheme.colorScheme.primary,
                    // trackColor esplicito: il default legge surfaceVariant,
                    // un ruolo NON personalizzato per palette (vedi CLAUDE.md).
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
            }
        }
    }
}

// ── Lista sessioni ──────────────────────────────────────────────────────

/**
 * Costruisce l'array a 7 slot dei minuti di pausa per giorno (slot[0] = 6
 * giorni fa, slot[6] = oggi) e i totali della settimana corrente, riusati
 * sia dal sommario che dalla sezione obiettivo. Logica invariata rispetto
 * alla vecchia bindWeeklyChart() — pura aritmetica su Calendar, indipendente
 * da Compose.
 */
internal fun weeklyChartData(sessions: List<SessionRecord>, now: Long = System.currentTimeMillis()): Triple<IntArray, Int, Int> {
    val minutesByDay = IntArray(7)
    val sessionsByDay = IntArray(7)
    val today = Calendar.getInstance().apply {
        timeInMillis = now
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
        mutableIntStateOf(currentGoal?.target?.takeIf { it in goalPresetsFor(selectedType) }
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
                        val label = if (selectedType == GoalType.SESSIONS) target.toString() else formatHistoryMinutes(target)
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
            CalmPill(label = label, selected = value == selected, onClick = { onSelect(value) })
        }
    }
}
