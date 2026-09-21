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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calmotter.app.GoalType
import com.calmotter.app.R
import com.calmotter.app.SessionRecord
import com.calmotter.app.SessionStreak
import com.calmotter.app.WeeklyGoal
import com.calmotter.app.ui.mascot.OtterZenMark
import com.calmotter.app.ui.mascot.SprigMark
import com.calmotter.app.ui.mascot.OtterHistoryIcon
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
 * **La barra statistiche scorre con il resto della pagina.** Riproduceva
 * esattamente la struttura della vecchia activity_history.xml, dove restava
 * fissa in alto mentre tutto il resto scorreva sotto — comportamento
 * volutamente preservato nella prima migrazione a Compose, poi segnalato:
 * su una pagina che si apre raramente e si legge dall'alto in giù, un
 * pannello fisso non aggiunge nulla che lo scorrimento non dia già, e la
 * pagina intera come un'unica regione è più semplice da capire di due
 * regioni con comportamenti diversi. Le righe sessione restano comunque
 * composable semplici dentro la Column scrollabile (sessions.forEach
 * { ... }), NON una LazyColumn annidata — una LazyColumn dentro una Column
 * scrollabile è la traduzione sbagliata per questo layout, indipendentemente
 * da cosa scorre fisso e cosa no.
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
    ) {
        StatsBar(
            sessions = sessions,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )

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

        ClosingPhraseCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp))

        Spacer(modifier = Modifier.height(16.dp))
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
        // Un VerticalDivider fra ciascuna colonna — segnalato mancante,
        // confrontato col mockup ("divide-x"). Tenue quanto gli altri
        // divisori della pagina (onSurface all'8%), e alto quanto il
        // contenuto della riga (IntrinsicSize.Min sulla Row) invece che
        // riempire tutta l'altezza della card.
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatColumn(
                value = total.toString(),
                label = stringResource(R.string.stat_sessions),
                modifier = Modifier.weight(1f)
            )
            VerticalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            StatColumn(
                value = formatMinutes(totalMinutes),
                label = stringResource(R.string.stat_minutes),
                modifier = Modifier.weight(1f)
            )
            VerticalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
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
                        text = stringResource(R.string.streak_days, streak),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                }
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
            SessionOutcomeIcon(
                isGroupSession = session.isGroupSession,
                completedNaturally = session.completedNaturally,
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
                // Con chi, quando lo si sa. L'icona diceva già "di gruppo" ma
                // non con chi: a distanza di settimane è proprio quella la
                // cosa che si vuole ritrovare. Vuoto per le pause di gruppo
                // nate da QR/codice, che non hanno nomi da registrare.
                val companions = session.companions.split("\n").filter { it.isNotBlank() }
                if (companions.isNotEmpty()) {
                    Text(
                        text = stringResource(
                            R.string.history_with_companions,
                            companions.joinToString(", "),
                        ),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
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

/**
 * Sostituisce il vecchio pallino verde/rosso + etichetta testuale "Pausa di
 * gruppo": un solo chip colorato (tinta "primary" se completata, tinta
 * "error" se interrotta prima — stesso linguaggio a card tinte già usato in
 * questa schermata e in "Gestisci app consentite", non un terzo modo di
 * comunicare stato) che ospita [OtterHistoryIcon] — sagoma da sola per una
 * pausa singola, con gli archi "segnale" sopra la testa se
 * [isGroupSession] (Tempo Insieme). Nessuna etichetta testuale: i due
 * segnali (colore del chip, forma dell'icona) bastano da soli.
 */
@Composable
private fun SessionOutcomeIcon(isGroupSession: Boolean, completedNaturally: Boolean) {
    val tint = if (completedNaturally) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    // Tonda, non quadrata arrotondata (redesign): la riga della sessione è
    // l'unico punto della pagina con una forma piena piccola, e il cerchio la
    // lega alle pastiglie di azione della pausa invece che alle card.
    Surface(
        shape = CircleShape,
        color = tint.copy(alpha = 0.14f),
        modifier = Modifier.size(38.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            OtterHistoryIcon(markSize = 20.dp, showSignal = isGroupSession, tint = tint)
        }
    }
}

// ── Stato vuoto ──────────────────────────────────────────────────────────

/**
 * Otter, titolo, una frase. Nient'altro: niente obiettivo settimanale.
 *
 * [WeeklyGoalSection] compariva anche qui (con `weekSessions`/`weekMinutes`
 * a 0), per una richiesta precedente di tenere "Imposta obiettivo" sempre
 * visibile; la richiesta è stata poi ritirata — chiedere un obiettivo a chi
 * non ha ancora fatto una pausa suona come un impegno da prendere prima di
 * cominciare, e questa app non spinge. L'obiettivo resta dov'è utile:
 * dentro [WeekOverviewCard], cioè quando c'è già qualcosa da misurare.
 *
 * L'otter è grande quanto quello della Home e sta dentro un alone
 * circolare tinto: a 100dp su una pagina per il resto bianca sembrava un
 * segnaposto più che il mascotte, e l'alone è ciò che rende questa pagina
 * parte dello stesso "stagno" invece di una schermata di errore. È statico
 * di proposito — le increspature animate della Home dicono "in attesa di
 * cominciare", che è vero lì, non su una pagina di sola lettura.
 *
 * La frase sotto è pescata a caso da `history_empty_phrases` a ogni
 * apertura (`remember`, quindi non cambia a ogni ricomposizione). Array
 * suo, **non** `pause_phrases`: quelle passano da [PhraseManager], che le
 * restituisce null se l'utente ha disattivato le frasi durante la pausa —
 * una preferenza su tutt'altro contesto che qui lascerebbe un buco.
 */
@Composable
private fun EmptyHistory() {
    val phrases = stringArrayResource(R.array.history_empty_phrases)
    val phrase = remember(phrases) { phrases.random() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                        shape = CircleShape,
                    )
            )
            OtterZenMark(markSize = 132.dp)
        }
        Text(
            text = stringResource(R.string.history_empty),
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 20.dp, bottom = 20.dp)
        )
        CalmCard {
            Text(
                text = phrase,
                fontSize = 15.sp,
                lineHeight = 23.sp,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
            )
        }
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


/**
 * Chiusura della lista: una frase riflessiva, pescata a caso a ogni apertura
 * (redesign). Stessa idea dello stato vuoto, all'altro capo della pagina.
 *
 * Legge `pause_phrases` **direttamente dalle risorse**, non tramite
 * [com.calmotter.app.PhraseManager]. Quel gestore risponde alla preferenza
 * "mostra frasi durante la pausa", che è scritta così perché riguarda
 * esattamente quello: cosa compare mentre sei bloccato e la frase te la
 * trovi davanti. Qui è l'ultima riga di una pagina che hai scelto di aprire
 * e da cui esci quando vuoi — contesto diverso, preferenza diversa.
 */
@Composable
private fun ClosingPhraseCard(modifier: Modifier = Modifier) {
    val phrases = stringArrayResource(R.array.pause_phrases)
    val phrase = remember(phrases) { phrases.random() }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
        modifier = modifier.fillMaxWidth(),
    ) {
        // Segnalato senza icona: la citazione chiudeva la pagina da sola,
        // senza il piccolo segno che nel resto dell'app accompagna sempre
        // una frase riflessiva (Impostazioni → Esperienza di pausa, il
        // pallino verde della pausa attiva). SprigMark sopra il testo,
        // come nel mockup, non dentro la riga: qui non introduce niente,
        // è la firma della frase.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(20.dp),
        ) {
            SprigMark(markSize = 18.dp)
            Text(
                text = phrase,
                fontSize = 15.sp,
                lineHeight = 23.sp,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
