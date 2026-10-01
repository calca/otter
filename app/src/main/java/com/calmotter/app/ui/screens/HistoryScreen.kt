package com.calmotter.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calmotter.app.EndReason
import com.calmotter.app.Mood
import com.calmotter.app.R
import com.calmotter.app.SessionRecord
import com.calmotter.app.SessionStreak
import com.calmotter.app.TogetherActivities
import com.calmotter.app.WeeklyGoal
import com.calmotter.app.historyMonths
import com.calmotter.app.ui.mascot.OtterZenMark
import com.calmotter.app.ui.mascot.SprigMark
import com.calmotter.app.ui.mascot.OtterHistoryIcon
import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

// Formatter con giorno della settimana esteso. Locale.getDefault(), non
// Locale.ITALY: era hardcoded in italiano, quindi ogni riga mostrava sempre
// "Domenica 13 set" anche con l'app in inglese (dove il resto della
// schermata usa correttamente values-en/strings.xml) — stesso bug di
// WeeklyChart.kt ("Lu"/"Ma"/... e "oggi" hardcoded).
//
// **Non più un `val` a livello di file.** Un `val` top-level in Kotlin
// gira nell'inizializzatore statico della classe che lo racchiude,
// eseguito una sola volta per processo — non a ogni ricomposizione né a
// ogni ricreazione di HistoryActivity. `Locale.getDefault()` veniva quindi
// letto una volta sola e restava quello per tutta la vita del processo:
// chi cambiava lingua di sistema mentre l'app era già in esecuzione (senza
// forzarne la chiusura, cosa che Android non fa per un cambio lingua)
// continuava a vedere le date nella lingua di prima. Segnalato in
// TODO.md ("1.3").
//
// **Non `Locale.getDefault()` nemmeno dentro la composable.** Il primo
// tentativo lo leggeva lì (con `remember(locale)` per ricreare il
// formatter solo al cambio) — lint lo boccia comunque
// (`NonObservableLocale`): `Locale.getDefault()` non è stato letto tramite
// stato osservabile da Compose, quindi anche dentro una composable non è
// garantito che la ricomposizione lo rilegga quando cambia. `LocalLocale
// .current` è la fonte corretta — è un `CompositionLocal`, quindi *è*
// stato osservabile, e la ricomposizione scatta davvero quando il locale
// cambia.
@Composable
private fun rememberHistoryDateFormat(): SimpleDateFormat {
    val locale = LocalLocale.current.platformLocale
    return remember(locale) { SimpleDateFormat("EEEE d MMM · HH:mm", locale) }
}

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

    // "Insieme" (specs/together-history/): con il filtro acceso statistiche,
    // settimana ed elenco guardano solo le pause di gruppo. Filtro e scheda
    // compaiono solo se ce n'è almeno una.
    val hasTogether = remember(sessions) { sessions.any { it.isGroupSession } }
    var togetherOnly by remember { mutableStateOf(false) }
    val shown = if (togetherOnly && hasTogether) sessions.filter { it.isGroupSession } else sessions

    val streak = SessionStreak.currentStreakDays(shown)
    val (minutesByDay, weekSessions, weekMinutes) = weeklyChartData(shown)

    val summaryText = if (weekSessions == 0) {
        stringResource(R.string.weekly_summary_none)
    } else {
        pluralStringResource(R.plurals.weekly_summary_sessions, weekSessions, weekSessions, formatHistoryMinutes(weekMinutes))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
    ) {
        if (hasTogether) {
            HistoryFilterRow(
                togetherOnly = togetherOnly,
                onChange = { togetherOnly = it },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
            )
        }
        StatsBar(
            sessions = shown,
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

        if (hasTogether) {
            TogetherCard(
                sessions = sessions,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        Text(
            text = stringResource(R.string.history_all_sessions),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 4.dp)
        )

        // Per mese (specs/history-by-month/): all'apertura il mese corrente e
        // il precedente, poi un mese in più a ogni tocco. Con una pausa al
        // giorno la lista intera diventava lunga e lenta da comporre; le
        // statistiche qui sopra contano comunque tutto.
        var extraMonths by remember(togetherOnly) { mutableIntStateOf(0) }
        val view = remember(shown, extraMonths) { historyMonths(shown, System.currentTimeMillis(), extraMonths) }
        val locale = LocalConfiguration.current.locales[0]
        val monthFormat = remember(locale) { DateTimeFormatter.ofPattern("LLLL yyyy", locale) }
        view.months.forEach { group ->
            Text(
                text = group.month.format(monthFormat).replaceFirstChar { it.uppercase(locale) },
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 4.dp),
            )
            if (group.sessions.isEmpty()) {
                Text(
                    text = stringResource(R.string.history_month_empty),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            group.sessions.forEach { session -> SessionRow(session) }
        }
        if (view.hasEarlier) {
            TextButton(
                onClick = { extraMonths++ },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.history_show_earlier))
            }
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
                value = formatHistoryMinutes(totalMinutes),
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
                val rawDate = rememberHistoryDateFormat().format(Date(session.startTimeMs))
                Text(
                    text = rawDate.replaceFirstChar { it.uppercase() },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = when {
                        session.completedNaturally -> stringResource(R.string.history_item_natural)
                        session.endReason == EndReason.SLOW_EXIT -> stringResource(R.string.history_item_slow_exit)
                        else -> stringResource(R.string.history_item_early, session.plannedMinutes)
                    },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 2.dp)
                )
                // Con chi, quando lo si sa. L'icona diceva già "di gruppo" ma
                // non con chi: a distanza di settimane è proprio quella la
                // cosa che si vuole ritrovare. Vuoto per le pause di gruppo
                // nate da QR/codice, che non hanno nomi da registrare.
                val companions = session.companions.split("\n").filter { it.isNotBlank() }
                // L'attività proposta, come icona della sua categoria
                // (specs/together-activity/): la frase intera non starebbe in
                // una riga. TalkBack legge il nome della categoria.
                val category = TogetherActivities.byId(session.activityId)?.category
                if (companions.isNotEmpty() || category != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp),
                    ) {
                        if (companions.isNotEmpty()) {
                            Text(
                                text = stringResource(
                                    R.string.history_with_companions,
                                    companions.joinToString(", "),
                                ),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (category != null) {
                            Icon(
                                painter = painterResource(category.icon),
                                contentDescription = stringResource(category.label),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(start = if (companions.isNotEmpty()) 6.dp else 0.dp)
                                    .size(14.dp),
                            )
                        }
                    }
                }
                // Il momento di chiusura (specs/closing-moment/): la risposta e
                // la nota, se ci sono. Senza nessuna delle due la riga resta
                // com'era.
                val moodLabel = when (session.mood) {
                    Mood.CALM -> stringResource(R.string.mood_calm)
                    Mood.ORDINARY -> stringResource(R.string.mood_ordinary)
                    Mood.HARD -> stringResource(R.string.mood_hard)
                    else -> null
                }
                val reflection = listOfNotNull(moodLabel, session.note.takeIf { it.isNotBlank() })
                if (reflection.isNotEmpty()) {
                    Text(
                        text = reflection.joinToString(" · "),
                        fontSize = 13.sp,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Text(
                text = formatHistoryMinutes(session.effectiveMinutes),
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

internal fun formatHistoryMinutes(minutes: Int): String {
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
