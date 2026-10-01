package com.calmotter.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calmotter.app.R
import com.calmotter.app.SessionRecord
import com.calmotter.app.companionTotals
import java.util.Calendar

// La parte "insieme" della Cronologia (specs/together-history/): il tempo
// passato in pausa con ciascuna persona e il filtro sulle sole pause di
// gruppo.

/** "Tutte / Insieme": con "Insieme" tutta la pagina guarda solo le pause di gruppo. */
@Composable
internal fun HistoryFilterRow(togetherOnly: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        SmallPill(stringResource(R.string.history_filter_all), selected = !togetherOnly) { onChange(false) }
        SmallPill(stringResource(R.string.history_filter_together), selected = togetherOnly) { onChange(true) }
    }
}

/**
 * La scheda "Insieme": per ogni persona tempo e numero di pause, e le icone
 * delle attività più frequenti. Mese corrente di default, "Sempre" a un
 * tocco. Nessuna classifica, nessun confronto: è un ricordo, non un
 * punteggio.
 */
@Composable
internal fun TogetherCard(sessions: List<SessionRecord>, modifier: Modifier = Modifier) {
    var allTime by remember { mutableStateOf(false) }
    val monthStart = remember {
        Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val totals = remember(sessions, allTime) {
        companionTotals(sessions, fromMs = if (allTime) null else monthStart)
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.together_card_title),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                SmallPill(stringResource(R.string.together_period_month), selected = !allTime) { allTime = false }
                SmallPill(
                    stringResource(R.string.together_period_all),
                    selected = allTime,
                    modifier = Modifier.padding(start = 6.dp),
                ) { allTime = true }
            }
            if (totals.isEmpty()) {
                Text(
                    text = stringResource(R.string.together_none_month),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            totals.forEach { total ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) {
                    Text(
                        text = total.name,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = " · " + pluralStringResource(
                            R.plurals.together_companion_line,
                            total.pauses,
                            formatHistoryMinutes(total.minutes),
                            total.pauses,
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        modifier = Modifier.weight(1f),
                    )
                    total.topCategories.forEach { category ->
                        Icon(
                            painter = painterResource(category.icon),
                            contentDescription = stringResource(category.label),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 6.dp).size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallPill(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f),
        modifier = modifier,
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
