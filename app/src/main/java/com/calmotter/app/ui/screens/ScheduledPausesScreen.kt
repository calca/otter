package com.calmotter.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerColors
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TimePickerDialogDefaults
import androidx.compose.material3.TimePickerDisplayMode
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimeInputColors
import androidx.compose.material3.TimeInputDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = timeLabel(schedule.startMinuteOfDay) + " · " + durationPillLabel(schedule.durationMinutes),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (schedule.locked) {
                                Icon(
                                    Icons.Filled.Lock,
                                    contentDescription = stringResource(R.string.schedule_locked_label),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(start = 8.dp).size(16.dp),
                                )
                            }
                        }
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
 * Nuova pausa programmata o modifica di una esistente, a tutta pagina, sul
 * modello della sveglia di Android: in cima l'ora grande (apre il
 * TimePickerDialog di Material) con sotto il riepilogo in una riga, poi
 * giorni, durata e, se ce n'è più d'uno, il profilo. "Salva" sta fisso in
 * fondo, fuori dallo scroll (regola di design, vedi CLAUDE.md); "Elimina"
 * è il cestino nella barra in alto, gestito dall'Activity.
 *
 * Arrivata così dopo due tentativi: il quadrante dentro la pagina ne
 * riempiva metà, e TimeInput apriva la tastiera da solo e con "00" già
 * scritto trasformava "30" in "03". Tolte anche le scorciatoie Lun–Ven /
 * Sab–Dom / Ogni giorno: con i sette tondi erano una riga di pillole in più
 * per risparmiare un paio di tocchi.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleEditorScreen(
    original: ScheduledPause,
    profiles: List<AllowedAppsProfile>,
    onSave: (ScheduledPause) -> Unit,
    onSkipNext: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    var days by remember { mutableIntStateOf(original.days) }
    var duration by remember { mutableIntStateOf(original.durationMinutes) }
    var profileId by remember { mutableIntStateOf(original.profileId) }
    var minute by remember { mutableIntStateOf(original.startMinuteOfDay) }
    var pickingTime by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(original.locked) }
    var pickingProfile by remember { mutableStateOf(false) }
    val isNew = original.id == 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
            ) {
                Surface(
                    onClick = { pickingTime = true },
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f),
                ) {
                    Text(
                        text = timeLabel(minute),
                        fontSize = 56.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp),
                    )
                }
                Text(
                    text = if (days == 0) {
                        stringResource(R.string.schedule_pick_a_day)
                    } else {
                        stringResource(R.string.schedule_summary_short, daysLabel(days, locale), durationPillLabel(duration))
                    },
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            // Due blocchi, come le card di Impostazioni: "quando" (giorni e
            // durata) e "come" (app consentite, protezione, salta la
            // prossima). Prima ogni campo aveva un aspetto suo — tondi,
            // pillole, un link, una card — e la pagina non stava insieme.
            EditorCard(modifier = Modifier.padding(top = 24.dp)) {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    FieldLabel(stringResource(R.string.schedule_days_label), topPadding = 16.dp)
                    DayCircles(days = days, locale = locale, onChange = { days = it })
                    Spacer(Modifier.height(16.dp))
                    CardDivider()
                    DurationStepper(duration = duration, onChange = { duration = it })
                }
            }

            EditorCard(modifier = Modifier.padding(top = 16.dp)) {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    // Il profilo con lo stesso dialogo della Home; la riga
                    // c'è solo con più di un profilo, come in Home.
                    if (profiles.size > 1) {
                        SettingsActionRow(
                            label = stringResource(R.string.schedule_profile_label),
                            value = profiles.firstOrNull { it.id == profileId }?.name,
                            onClick = { pickingProfile = true },
                            icon = { RowIcon(Icons.AutoMirrored.Filled.List) },
                        )
                        CardDivider()
                    }
                    // Spenta di default: la pausa è di chi l'ha messa. Accesa
                    // è un patto; la password la chiede l'Activity al
                    // salvataggio, non qui.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { locked = !locked }
                            .padding(vertical = 14.dp),
                    ) {
                        SettingsRowIcon { RowIcon(Icons.Filled.Lock) }
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(stringResource(R.string.schedule_locked_label), color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                text = stringResource(R.string.schedule_locked_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                            )
                        }
                        Switch(checked = locked, onCheckedChange = { locked = it }, colors = settingsSwitchColors())
                    }
                    if (!isNew && original.enabled) {
                        CardDivider()
                        SettingsActionRow(
                            label = stringResource(R.string.schedule_skip_next),
                            onClick = onSkipNext,
                            icon = { RowIcon(Icons.Filled.DateRange) },
                        )
                    }
                }
            }
        }

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
                        locked = locked,
                    )
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .height(52.dp),
        ) {
            Text(stringResource(R.string.allowed_profile_save))
        }
    }

    if (pickingProfile) {
        AllowedProfileDialog(
            profiles = profiles,
            selectedProfileId = profileId,
            onSelect = { profileId = it; pickingProfile = false },
            onDismiss = { pickingProfile = false },
        )
    }
    if (pickingTime) {
        TimeDialog(
            initialMinute = minute,
            onDismiss = { pickingTime = false },
            onConfirm = { minute = it; pickingTime = false },
        )
    }
}

/** Etichetta di un campo della pagina: discreta, a sinistra, come un riferimento e non un titolo. */
@Composable
private fun FieldLabel(text: String, topPadding: Dp = 20.dp) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        modifier = Modifier.padding(top = topPadding, bottom = 10.dp),
    )
}

/** La card delle sezioni di Impostazioni, qui per i due blocchi della pagina. */
@Composable
private fun EditorCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
        modifier = modifier.fillMaxWidth(),
        content = content,
    )
}

@Composable
private fun CardDivider() = HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

@Composable
private fun RowIcon(icon: ImageVector) = Icon(
    imageVector = icon,
    contentDescription = null,
    tint = MaterialTheme.colorScheme.primary,
    modifier = Modifier.size(18.dp),
)

/**
 * Il TimePickerDialog di Material: quadrante a 24 ore, e in basso a sinistra
 * l'icona per scrivere l'ora con la tastiera.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initialMinute: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val state = rememberTimePickerState(
        initialHour = initialMinute / 60,
        initialMinute = initialMinute % 60,
        is24Hour = true,
    )
    var mode by remember { mutableStateOf(TimePickerDisplayMode.Picker) }
    TimePickerDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.schedule_time_label)) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) {
                Text(stringResource(R.string.allowed_profile_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
        modeToggleButton = {
            TimePickerDialogDefaults.DisplayModeToggle(
                onDisplayModeChange = {
                    mode = if (mode == TimePickerDisplayMode.Picker) TimePickerDisplayMode.Input else TimePickerDisplayMode.Picker
                },
                displayMode = mode,
            )
        },
    ) {
        if (mode == TimePickerDisplayMode.Picker) {
            TimePicker(state = state, colors = calmTimePickerColors())
        } else {
            TimeInput(state = state, colors = calmTimeInputColors())
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
 * I colori di default di TimePicker e TimeInput vengono da ruoli che le palette non
 * personalizzano (primaryContainer, surfaceContainerHighest: vedi la nota
 * in CLAUDE.md), e sarebbero rimasti lilla-M3 su ogni palette. Qui sono
 * presi da primary/tertiary come il resto delle scelte dell'app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun calmTimeInputColors(): TimeInputColors {
    val scheme = MaterialTheme.colorScheme
    return TimeInputDefaults.colors(
        timeTextFieldColors = TextFieldDefaults.colors(
            focusedContainerColor = scheme.primary.copy(alpha = 0.14f),
            unfocusedContainerColor = scheme.tertiary.copy(alpha = 0.55f),
            focusedTextColor = scheme.primary,
            unfocusedTextColor = scheme.onSurface,
            cursorColor = scheme.primary,
            focusedIndicatorColor = scheme.primary,
            unfocusedIndicatorColor = Color.Transparent,
            focusedSupportingTextColor = scheme.onSurface.copy(alpha = 0.6f),
            unfocusedSupportingTextColor = scheme.onSurface.copy(alpha = 0.6f),
        ),
    )
}

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
    else -> compactDays(days) { day ->
        day.getDisplayName(TextStyle.SHORT, locale).trimEnd('.').replaceFirstChar { it.titlecase(locale) }
    }
}

/**
 * I giorni scelti, con i tratti di tre o più giorni consecutivi compattati
 * in un intervallo: "Lun–Sab", "Lun–Mer, Ven", "Mar, Gio". Due giorni di
 * fila restano separati ("Sab, Dom" si legge meglio di "Sab–Dom" quando
 * non è la scorciatoia del weekend, che ha già la sua etichetta). La
 * settimana parte dal lunedì e non si richiude sulla domenica.
 */
internal fun compactDays(days: Int, name: (DayOfWeek) -> String): String {
    val chosen = DayOfWeek.entries.filter { days and (1 shl (it.value - 1)) != 0 }
    val runs = mutableListOf<MutableList<DayOfWeek>>()
    chosen.forEach { day ->
        val last = runs.lastOrNull()
        if (last != null && last.last().value + 1 == day.value) last += day else runs += mutableListOf(day)
    }
    return runs.joinToString(", ") { run ->
        if (run.size >= 3) "${name(run.first())}–${name(run.last())}" else run.joinToString(", ") { name(it) }
    }
}

/**
 * La durata come contatore in una riga della card: "–" e "+" scorrono le
 * stesse durate della Home ([SESSION_DURATION_OPTIONS]) e si spengono ai due
 * estremi. Prima era una riga di pillole che dentro la card scorreva e
 * restava tagliata sul bordo; la durata è anche il valore che si cambia più
 * spesso, e così si regola senza aprire nulla.
 */
@Composable
private fun DurationStepper(duration: Int, onChange: (Int) -> Unit) {
    val index = SESSION_DURATION_OPTIONS.indexOf(duration).coerceAtLeast(0)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
    ) {
        SettingsRowIcon {
            Icon(
                painter = painterResource(R.drawable.ic_hourglass),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = stringResource(R.string.schedule_duration_label),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        StepButton(
            plus = false,
            description = stringResource(R.string.schedule_duration_shorter),
            enabled = index > 0,
            onClick = { onChange(SESSION_DURATION_OPTIONS[index - 1]) },
        )
        Text(
            text = durationPillLabel(SESSION_DURATION_OPTIONS[index]),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(76.dp),
        )
        StepButton(
            plus = true,
            description = stringResource(R.string.schedule_duration_longer),
            enabled = index < SESSION_DURATION_OPTIONS.lastIndex,
            onClick = { onChange(SESSION_DURATION_OPTIONS[index + 1]) },
        )
    }
}

/**
 * Un tasto tondo del contatore, nella stessa pastiglia tinta delle icone di
 * riga. Segni disegnati e non caratteri: "+" e "–" del font stanno su
 * altezze diverse e i due tasti sembravano storti.
 */
@Composable
private fun StepButton(plus: Boolean, description: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 0.12f else 0.05f),
        modifier = Modifier.size(36.dp).semantics { contentDescription = description },
    ) {
        val tint = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.35f)
        Box(contentAlignment = Alignment.Center) {
            if (plus) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            } else {
                Box(Modifier.size(width = 14.dp, height = 2.5.dp).background(tint, RoundedCornerShape(2.dp)))
            }
        }
    }
}
