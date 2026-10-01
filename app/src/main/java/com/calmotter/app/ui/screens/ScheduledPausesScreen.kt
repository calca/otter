package com.calmotter.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
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

/**
 * Le pause programmate (specs/scheduled-pauses/): l'elenco, con un
 * interruttore per ciascuna, e "Aggiungi". Toccare una pausa apre l'editor.
 * Le regole sulla password (cosa allenta il patto) non stanno qui: la
 * schermata segnala la modifica e l'Activity decide se chiederla.
 */
@Composable
fun ScheduledPausesScreen(
    schedules: List<ScheduledPause>,
    profiles: List<AllowedAppsProfile>,
    onSave: (ScheduledPause) -> Unit,
    onDelete: (ScheduledPause) -> Unit,
    onSkipNext: (ScheduledPause) -> Unit,
) {
    var editing by remember { mutableStateOf<ScheduledPause?>(null) }
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
                        .clickable { editing = schedule }
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
                        onCheckedChange = { onSave(schedule.copy(enabled = it)) },
                        colors = settingsSwitchColors(),
                    )
                }
            }
        }
        Button(
            onClick = {
                editing = ScheduledPause(
                    id = 0,
                    days = WEEKDAYS,
                    startMinuteOfDay = 21 * 60,
                    durationMinutes = DEFAULT_SESSION_DURATION_MINUTES,
                )
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(48.dp),
        ) {
            Text(stringResource(R.string.schedule_add))
        }
    }

    editing?.let { original ->
        ScheduleEditorDialog(
            original = original,
            profiles = profiles,
            locale = locale,
            onDismiss = { editing = null },
            onSave = { onSave(it); editing = null },
            onDelete = { onDelete(original); editing = null },
            onSkipNext = { onSkipNext(original); editing = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleEditorDialog(
    original: ScheduledPause,
    profiles: List<AllowedAppsProfile>,
    locale: Locale,
    onDismiss: () -> Unit,
    onSave: (ScheduledPause) -> Unit,
    onDelete: () -> Unit,
    onSkipNext: () -> Unit,
) {
    var days by remember { mutableIntStateOf(original.days) }
    var minute by remember { mutableIntStateOf(original.startMinuteOfDay) }
    var duration by remember { mutableIntStateOf(original.durationMinutes) }
    var profileId by remember { mutableIntStateOf(original.profileId) }
    var pickingTime by remember { mutableStateOf(false) }
    val isNew = original.id == 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isNew) R.string.schedule_new_title else R.string.schedule_edit_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                SetupLabel(stringResource(R.string.schedule_days_label))
                ScrollingPillRow {
                    DayOfWeek.entries.forEach { day ->
                        val bit = 1 shl (day.value - 1)
                        CalmPill(
                            size = CalmPillSize.Small,
                            label = day.getDisplayName(TextStyle.SHORT, locale),
                            selected = days and bit != 0,
                            onClick = { days = days xor bit },
                        )
                    }
                }
                SetupLabel(stringResource(R.string.schedule_time_label), topPadding = 16.dp)
                TextButton(onClick = { pickingTime = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(timeLabel(minute), fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                }
                SetupLabel(stringResource(R.string.schedule_duration_label), topPadding = 8.dp)
                ScrollingPillRow { selectedInView ->
                    SESSION_DURATION_OPTIONS.forEach { option ->
                        CalmPill(
                            size = CalmPillSize.Small,
                            label = durationPillLabel(option),
                            selected = option == duration,
                            modifier = if (option == duration) selectedInView else Modifier,
                        ) { duration = option }
                    }
                }
                if (profiles.size > 1) {
                    SetupLabel(stringResource(R.string.schedule_profile_label), topPadding = 16.dp)
                    ScrollingPillRow { selectedInView ->
                        profiles.forEach { profile ->
                            CalmPill(
                                size = CalmPillSize.Small,
                                label = profile.name,
                                selected = profile.id == profileId,
                                modifier = if (profile.id == profileId) selectedInView else Modifier,
                            ) { profileId = profile.id }
                        }
                    }
                }
                if (!isNew) {
                    Row(modifier = Modifier.padding(top = 12.dp)) {
                        if (original.enabled) {
                            TextButton(onClick = onSkipNext) { Text(stringResource(R.string.schedule_skip_next)) }
                        }
                        TextButton(onClick = onDelete) { Text(stringResource(R.string.schedule_delete)) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
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
            ) { Text(stringResource(R.string.allowed_profile_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )

    if (pickingTime) {
        val state = rememberTimePickerState(initialHour = minute / 60, initialMinute = minute % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickingTime = false },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    minute = state.hour * 60 + state.minute
                    pickingTime = false
                }) { Text(stringResource(R.string.allowed_profile_save)) }
            },
            dismissButton = {
                TextButton(onClick = { pickingTime = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
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
 * come le durate in Home: stesso gesto ovunque si scelga una durata, e il
 * dialogo non si allunga su più righe. Il bordo destro sfuma finché c'è
 * altro da scorrere. [content] riceve il modifier da dare alla pillola
 * scelta, che all'apertura viene portata in vista (una durata come "4 h"
 * starebbe altrimenti fuori schermo).
 */
@Composable
private fun ScrollingPillRow(content: @Composable (selectedInView: Modifier) -> Unit) {
    val scrollState = rememberScrollState()
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(Unit) { requester.bringIntoView() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .horizontalFadeEdge(visible = scrollState.canScrollForward),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        content(Modifier.bringIntoViewRequester(requester))
    }
}

