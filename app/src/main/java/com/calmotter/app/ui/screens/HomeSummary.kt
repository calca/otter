package com.calmotter.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.calmotter.app.R
import com.calmotter.app.SessionRecord
import com.calmotter.app.ui.mascot.SprigMark
import java.util.Calendar

/**
 * TODO.md "7": estratto da MainScreen.kt — la riga di riepilogo
 * streak/settimana e il calcolo dietro di lei, l'unico blocco di questo file
 * originale specifico della Home e non condiviso con nient'altro. Nessun
 * cambio di comportamento, stesso package.
 */

/**
 * Riga discreta con lo stato delle sessioni, che è anche l'unico ingresso
 * alla Cronologia dalla Home.
 *
 * Sostituisce la card con il grafico a barre degli ultimi 7 giorni, rimossa
 * su segnalazione ("il grafico e la cronologia in home creano ingombro").
 * Il vero argomento però non era lo spazio: la Home diceva **la stessa cosa
 * due volte**, una qui come riga di riepilogo e una nell'intestazione della
 * card, e la card aggiungeva solo la forma a barre. Una schermata che parla
 * di quanto *non* usi il telefono non ha motivo di somigliare a un cruscotto
 * di analytics.
 *
 * Lo streak non si perde: è l'unico dato che la riga non aveva già, e qui
 * assorbe la stessa logica che l'intestazione della card applicava — streak
 * se è di almeno un giorno, altrimenti il riepilogo della settimana. La
 * forma della settimana, quella sì, diventa un numero da leggere invece di
 * una sagoma da guardare: resta a un tap di distanza in Cronologia.
 */
@Composable
internal fun SessionsSummaryLink(
    streakDays: Int,
    weekSummary: WeekSummary,
    onHistory: () -> Unit,
) {
    // Il colore del link fa il lavoro che faceva il contenitore. Una riga
    // grigia al 70% non si capiva che fosse toccabile — era la ragione della
    // pastiglia — ma in `primary`, con il chevron accanto, si legge come un
    // link e basta: nessuno sfondo da giustificare.
    val linkColor = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .padding(top = 20.dp)
            .clip(RoundedCornerShape(50))
            // onClickLabel invece di una stringa in più: TalkBack annuncia
            // "apri Cronologia" come azione della riga, senza che il "›"
            // debba significare qualcosa per chi non lo vede.
            .clickable(
                onClickLabel = stringResource(R.string.history_title),
                onClick = onHistory,
            )
            // Senza sfondo il bersaglio non si vede più, quindi va imposto:
            // il testo è alto una ventina di dp, sotto i 48 che servono a un
            // dito. Il padding sta dopo `clickable` apposta, così l'area
            // toccata e quella che si illumina sono la stessa.
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        // La fogliolina resta: è l'unico segno che lega questa riga allo
        // stagno qui sopra, e senza pastiglia non compete più con nulla.
        SprigMark(markSize = 14.dp)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = if (streakDays >= 1) {
                pluralStringResource(R.plurals.streak_days, streakDays, streakDays)
            } else {
                weeklySummaryText(weekSummary)
            },
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            color = linkColor,
        )
        Text(
            text = "›",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = linkColor,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/** Riassunto degli ultimi 7 giorni: totale sessioni e minuti, per riusare
 * le stringhe weekly_summary_* già scritte per la Cronologia (vedi
 * HistoryScreen.kt — stessa finestra "ultimi 7 giorni", non settimana di
 * calendario). I dati giorno per giorno servivano al grafico a barre della
 * Home, rimosso: le barre degli ultimi 7 giorni restano in Cronologia, che
 * se li ricalcola per conto suo (vedi WeeklyChart.kt). */
internal data class WeekSummary(
    val totalSessions: Int,
    val totalMinutes: Int,
)

internal fun weekSummaryOf(records: List<SessionRecord>): WeekSummary {
    fun dayStart(timeMs: Long): Long = Calendar.getInstance().apply {
        timeInMillis = timeMs
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val minutesByDay = HashMap<Long, Int>()
    val sessionsByDay = HashMap<Long, Int>()
    for (record in records) {
        val day = dayStart(record.startTimeMs)
        minutesByDay[day] = (minutesByDay[day] ?: 0) + record.effectiveMinutes
        sessionsByDay[day] = (sessionsByDay[day] ?: 0) + 1
    }

    val cursor = Calendar.getInstance().apply { timeInMillis = dayStart(System.currentTimeMillis()) }
    cursor.add(Calendar.DATE, -6)

    var totalSessions = 0
    var totalMinutes = 0
    repeat(7) {
        val day = cursor.timeInMillis
        totalSessions += sessionsByDay[day] ?: 0
        totalMinutes += minutesByDay[day] ?: 0
        cursor.add(Calendar.DATE, 1)
    }
    return WeekSummary(totalSessions, totalMinutes)
}

@Composable
private fun weeklySummaryText(summary: WeekSummary): String = if (summary.totalSessions == 0) {
    stringResource(R.string.weekly_summary_none)
} else {
    pluralStringResource(
        R.plurals.weekly_summary_sessions,
        summary.totalSessions,
        summary.totalSessions,
        formatMinutes(summary.totalMinutes),
    )
}

/** Duplica HistoryScreen.kt's formatMinutes(): stessa resa "1h 30m", non
 * condivisa perché entrambe le funzioni sono private ai rispettivi file
 * (stesso pattern di last7DayMinutes/dayStart già documentato altrove). */
private fun formatMinutes(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "${m}m"
        m == 0 -> "${h}h"
        else -> "${h}h ${m}m"
    }
}
