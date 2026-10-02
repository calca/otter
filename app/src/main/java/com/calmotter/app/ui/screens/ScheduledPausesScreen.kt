package com.calmotter.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerColors
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calmotter.app.AllowedAppsProfile
import com.calmotter.app.DEFAULT_SESSION_DURATION_MINUTES
import com.calmotter.app.R
import com.calmotter.app.SESSION_DURATION_OPTIONS
import com.calmotter.app.ScheduledPause
import com.calmotter.app.durationPillLabel
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

private const val ALL_DAYS = 0b1111111
private const val WEEKDAYS = 0b0011111
private const val WEEKEND = 0b1100000

/** La pausa che "Aggiungi" propone: feriali alle 21, durata predefinita. */
fun newScheduledPause(): ScheduledPause = ScheduledPause(
    id = 0,
    days = WEEKDAYS,
    startMinuteOfDay = 21 * 60,
    durationMinutes = DEFAULT_SESSION_DURATION_MINUTES,
)

/**
 * Le pause programmate (specs/scheduled-pauses/): l'elenco, con un
 * interruttore per ciascuna, e "Aggiungi". Toccare una pausa apre
 * [ScheduleEditorScreen]. Le regole sulla password (cosa allenta il patto)
 * non stanno qui: la schermata segnala la modifica e l'Activity decide se
 * chiederla.
 */
@Composable
fun ScheduledPausesScreen(
    schedules: List<ScheduledPause>,
    onToggle: (ScheduledPause) -> Unit,
    onEdit: (ScheduledPause) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(
            text = stringResource(R.string.schedule_intro),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            modifier = Modifier.padding(bottom = 16.dp),
        )
        schedules.forEach { schedule ->
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { onEdit(schedule) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = timeLabel(schedule.startMinuteOfDay) + " · " + durationPillLabel(schedule.durationMinutes),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = daysLabel(schedule.days, locale),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        )
                    }
                    Switch(
                        checked = schedule.enabled,
                        onCheckedChange = { onToggle(schedule.copy(enabled = it)) },
                        colors = settingsSwitchColors(),
                    )
                }
            }
        }
        Button(
            onClick = { onEdit(newScheduledPause()) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(48.dp),
        ) {
            Text(stringResource(R.string.schedule_add))
        }
    }
}

/**
 * Nuova pausa programmata o modifica di una esistente, a tutta pagina.
 * Prima era un dialogo con dentro giorni, durata, profilo e azioni, che per
 * l'ora apriva un secondo dialogo sopra di sé: troppo per lo spazio di un
 * AlertDialog. Qui l'orologio di Material sta direttamente nella pagina, i
 * sette giorni stanno su una riga senza scorrere, e una frase di riepilogo
 * dice cosa si sta impostando prima di salvare.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleEditorScreen(
    original: ScheduledPause,
    profiles: List<AllowedAppsProfile>,
    onSave: (ScheduledPause) -> Unit,
    onDelete: () -> Unit,
    onSkipNext: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    var days by remember { mutableIntStateOf(original.days) }
    var duration by remember { mutableIntStateOf(original.durationMinutes) }
    var profileId by remember { mutableIntStateOf(original.profileId) }
    val time = rememberTimePickerState(
        initialHour = original.startMinuteOfDay / 60,
        initialMinute = original.startMinuteOfDay % 60,
        is24Hour = true,
    )
    val minute = time.hour * 60 + time.minute
    val isNew = original.id == 0

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        SetupLabel(stringResource(R.string.schedule_time_label))
        TimePicker(state = time, colors = calmTimePickerColors())

        SetupLabel(stringResource(R.string.schedule_days_label), topPadding = 8.dp)
        DayCircles(days = days, locale = locale, onChange = { days = it })
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 12.dp),
        ) {
            listOf(
                WEEKDAYS to R.string.schedule_weekdays,
                WEEKEND to R.string.schedule_weekend,
                ALL_DAYS to R.string.schedule_every_day,
            ).forEach { (mask, label) ->
                CalmPill(
                    label = stringResource(label),
                    selected = days == mask,
                    size = CalmPillSize.Small,
                    onClick = { days = mask },
                )
            }
        }

        SetupLabel(stringResource(R.string.schedule_duration_label), topPadding = 24.dp)
        ScrollingPillRow { selectedInView ->
            SESSION_DURATION_OPTIONS.forEach { option ->
                CalmPill(
                    label = durationPillLabel(option),
                    selected = option == duration,
                    modifier = if (option == duration) selectedInView else Modifier,
                ) { duration = option }
            }
        }

        if (profiles.size > 1) {
            SetupLabel(stringResource(R.string.schedule_profile_label), topPadding = 24.dp)
            ScrollingPillRow { selectedInView ->
                profiles.forEach { profile ->
                    CalmPill(
                        label = profile.name,
                        selected = profile.id == profileId,
                        modifier = if (profile.id == profileId) selectedInView else Modifier,
                    ) { profileId = profile.id }
                }
            }
        }

        Text(
            text = if (days == 0) {
                stringResource(R.string.schedule_pick_a_day)
            } else {
                stringResource(R.string.schedule_summary, daysLabel(days, locale), timeLabel(minute), durationPillLabel(duration))
            },
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
        )
        Button(
            enabled = days != 0,
            onClick = {
                onSave(
                    original.copy(
                        days = days,
                        startMinuteOfDay = minute,
                        durationMinutes = duration,
                        profileId = profileId,
                        enabled = if (isNew) true else original.enabled,
                    )
                )
            },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(52.dp),
        ) {
            Text(stringResource(R.string.allowed_profile_save))
        }
        if (!isNew) {
            if (original.enabled) {
                TextButton(onClick = onSkipNext, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.schedule_skip_next))
                }
            }
            // In rosso e in fondo, come "Elimina profilo": l'unica azione
            // distruttiva della pagina (e chiede comunque la password).
            TextButton(
                onClick = onDelete,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.schedule_delete))
            }
        }
    }
}

/**
 * I sette giorni come tondi su una riga intera, uno per giorno, con gli
 * stessi colori di [CalmPill]. Ognuno è un interruttore (si accende e si
 * spegne da solo) e si legge col nome intero del giorno.
 */
@Composable
private fun DayCircles(days: Int, locale: Locale, onChange: (Int) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        DayOfWeek.entries.forEach { day ->
            val bit = 1 shl (day.value - 1)
            val selected = days and bit != 0
            val fullName = day.getDisplayName(TextStyle.FULL, locale)
            Surface(
                checked = selected,
                onCheckedChange = { onChange(days xor bit) },
                shape = CircleShape,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f),
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .semantics { contentDescription = fullName },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = day.getDisplayName(TextStyle.SHORT, locale).trimEnd('.'),
                        fontSize = 13.sp,
                        maxLines = 1,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    )
                }
            }
        }
    }
}

/**
 * I colori di default di TimePicker vengono da ruoli che le palette non
 * personalizzano (primaryContainer, surfaceContainerHighest: vedi la nota
 * in CLAUDE.md), e sarebbero rimasti lilla-M3 su ogni palette. Qui sono
 * presi da primary/tertiary come il resto delle scelte dell'app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun calmTimePickerColors(): TimePickerColors {
    val scheme = MaterialTheme.colorScheme
    return TimePickerDefaults.colors(
        clockDialColor = scheme.tertiary.copy(alpha = 0.55f),
        clockDialSelectedContentColor = scheme.onPrimary,
        clockDialContentColor = scheme.onSurface,
        selectorColor = scheme.primary,
        timeSelectorSelectedContainerColor = scheme.primary,
        timeSelectorSelectedContentColor = scheme.onPrimary,
        timeSelectorContainerColor = scheme.tertiary.copy(alpha = 0.55f),
        timeSelectorContentColor = scheme.onSurface,
    )
}

private fun timeLabel(minuteOfDay: Int): String = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

@Composable
private fun daysLabel(days: Int, locale: Locale): String = when (days) {
    ALL_DAYS -> stringResource(R.string.schedule_every_day)
    WEEKDAYS -> stringResource(R.string.schedule_weekdays)
    WEEKEND -> stringResource(R.string.schedule_weekend)
    else -> DayOfWeek.entries
        .filter { days and (1 shl (it.value - 1)) != 0 }
        .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
}

/**
 * Una riga di pillole che scorre in orizzontale invece di andare a capo,
 * come le durate in Home: stesso gesto ovunque si scelga una durata. Il
 * bordo destro sfuma finché c'è altro da scorrere. [content] riceve il
 * modifier da dare alla pillola scelta, che all'apertura viene portata in
 * vista (una durata come "4 h" starebbe altrimenti fuori schermo) —
 * scorrendo solo la riga: un bringIntoView farebbe scorrere anche la
 * pagina fino a lei.
 */
@Composable
private fun ScrollingPillRow(content: @Composable (selectedInView: Modifier) -> Unit) {
    val scrollState = rememberScrollState()
    var selectedX by remember { mutableStateOf<Int?>(null) }
    val margin = with(LocalDensity.current) { 48.dp.roundToPx() }
    LaunchedEffect(selectedX) {
        selectedX?.let { scrollState.scrollTo((it - margin).coerceAtLeast(0)) }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .horizontalFadeEdge(visible = scrollState.canScrollForward),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        content(Modifier.onGloballyPositioned { if (selectedX == null) selectedX = it.positionInParent().x.toInt() })
    }
}
