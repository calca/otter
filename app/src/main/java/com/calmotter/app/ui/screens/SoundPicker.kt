package com.calmotter.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import com.calmotter.app.R
import com.calmotter.app.sound.RelaxSound
import com.calmotter.app.ui.theme.muted

/**
 * Scelta del suono di relax (specs/relax-sound/): pillole come per durata e
 * modalità, nessun componente nuovo. Con [allowDefault] c'è anche
 * "Predefinito" (= il suono generale, scelto nelle Impostazioni): lo usa il
 * profilo, dove [selected] null significa proprio quello.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SoundPicker(
    selected: RelaxSound?,
    allowDefault: Boolean,
    onChange: (RelaxSound?) -> Unit,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (showLabel) {
            Text(
                text = stringResource(R.string.relax_sound_label),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.muted(0.7f),
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = if (showLabel) 6.dp else 0.dp),
        ) {
            if (allowDefault) {
                CalmPill(
                    label = stringResource(R.string.relax_sound_default),
                    selected = selected == null,
                    onClick = { onChange(null) },
                )
            }
            RelaxSound.entries.forEach { sound ->
                CalmPill(
                    label = stringResource(sound.labelRes),
                    selected = selected == sound,
                    onClick = { onChange(sound) },
                )
            }
        }
    }
}

/**
 * Interruttore del suono sulla schermata di blocco: spegne o riaccende il
 * suono per il resto della pausa. Non chiede la password: non allenta il
 * blocco. Un solo elemento con ruolo Switch (nome e 48 dp per TalkBack).
 */
@Composable
fun SoundToggle(
    sound: RelaxSound,
    playing: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .heightIn(min = 48.dp)
            .toggleable(value = playing, role = Role.Switch, onValueChange = onToggle),
    ) {
        Text(
            text = stringResource(
                if (playing) R.string.relax_sound_playing else R.string.relax_sound_stopped,
                stringResource(sound.labelRes),
            ),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
