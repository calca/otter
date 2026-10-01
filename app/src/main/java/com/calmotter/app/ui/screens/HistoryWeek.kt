package com.calmotter.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
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
 * Streak, grafico settimanale, sommario e obiettivo raggruppati in un'unica
 * card tinta (Surface arrotondata) invece di lasciarli sciolti sulla pagina
 * come prima di questo redesign. Senza divisori interni, a differenza delle
 * sezioni di SettingsScreen.kt: lì ogni riga è un'azione o un dato a sé,
 * qui streak/grafico/sommario/obiettivo sono la stessa storia raccontata in
 * quattro tappe — un divisore fra sommario e obiettivo c'era, segnalato e
 * tolto, perché tagliava a metà "questa settimana" → "verso l'obiettivo di
 * questa settimana" come se fossero due argomenti diversi, mentre nel
 * mockup ("Calm Otter - History & Journey") restano nella stessa card senza
 * separatore.
 */
@Composable
internal fun WeekOverviewCard(
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
                    // 4dp sopra e sotto bastavano quando c'era un divisore
                    // fra questa riga e l'obiettivo: tolto (vedi sotto), è
                    // rimasto solo questo respiro, e il sommario finiva
                    // appiccicato alla barra di avanzamento — segnalato.
                    // 16dp sotto, niente in più sopra: qui il sommario deve
                    // restare vicino al grafico che descrive, e staccarsi
                    // solo dall'obiettivo che viene dopo.
                    .padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp)
            )

            // Nessun divisore qui, segnalato: sommario e obiettivo sono la
            // stessa storia continuata ("questa settimana" → "verso
            // l'obiettivo di questa settimana"), non due sezioni distinte
            // come lo sono card diverse — il mockup le tiene in un'unica
            // card senza separatore interno, vedi il commento di classe di
            // [WeekOverviewCard].
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
internal fun WeeklyGoalSection(
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
                // Quantity su goal.target, non su current: è quello con cui
                // "sessioni"/"minuti" concorda grammaticalmente — vedi il
                // commento sulla risorsa in strings.xml.
                text = when (goal.type) {
                    GoalType.SESSIONS -> pluralStringResource(R.plurals.weekly_goal_progress_sessions, goal.target, current, goal.target)
                    GoalType.MINUTES -> pluralStringResource(R.plurals.weekly_goal_progress_minutes, goal.target, current, goal.target)
                },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
        } else {
            // Senza obiettivo la sezione era il solo bottone dentro una card
            // larga tutta la pagina: un blocco vuoto con qualcosa in mezzo,
            // che non diceva di cosa fosse la card. Questa riga è il dato
            // mancante — "obiettivo: nessuno" — non un invito in più.
            Text(
                text = stringResource(R.string.weekly_goal_none),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
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

/**
 * Costruisce l'array a 7 slot dei minuti di pausa per giorno (slot[0] = 6
 * giorni fa, slot[6] = oggi) e i totali della settimana corrente, riusati
 * sia dal sommario che dalla sezione obiettivo. Logica invariata rispetto
 * alla vecchia bindWeeklyChart() — pura aritmetica su Calendar, indipendente
 * da Compose.
 */
internal fun weeklyChartData(sessions: List<SessionRecord>): Triple<IntArray, Int, Int> {
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
