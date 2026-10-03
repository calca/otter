package com.calmotter.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calmotter.app.R
import com.calmotter.app.TogetherActivities

/**
 * La proposta di attività dell'host (specs/together-activity/): l'id
 * corrente e "Un'altra". Ricorda quelle già viste, così non si ripetono
 * finché non sono finite, e l'ultima categoria, così l'alternativa è
 * davvero diversa.
 */
@Stable
class ActivitySuggestion(initialId: Int) {
    var activityId by mutableIntStateOf(initialId)
        private set
    private var seen by mutableStateOf(setOf(initialId))

    fun another(durationMinutes: Int) {
        val current = TogetherActivities.byId(activityId)
        val next = TogetherActivities.next(durationMinutes, seen, current?.category)
        activityId = next.id
        seen = if (next.id in seen) setOf(next.id) else seen + next.id
    }

    /** Se la durata cambia e la proposta non le si adatta più, ne sceglie un'altra. */
    fun fitTo(durationMinutes: Int) {
        if (TogetherActivities.byId(activityId)?.fits(durationMinutes) != true) another(durationMinutes)
    }
}

@Composable
fun rememberActivitySuggestion(durationMinutes: Int, initialId: Int = TogetherActivities.NONE): ActivitySuggestion {
    val suggestion = remember {
        ActivitySuggestion(
            if (initialId != TogetherActivities.NONE) initialId
            else TogetherActivities.next(durationMinutes).id
        )
    }
    LaunchedEffect(durationMinutes) { suggestion.fitTo(durationMinutes) }
    return suggestion
}

/**
 * "Cosa facciamo?" e la frase dell'attività. Con [onAnother] (solo chi
 * propone) "Cosa facciamo?" diventa un bottone con ↻ a sinistra che cambia
 * proposta. Niente se [activityId] è 0.
 */
@Composable
fun TogetherActivityCard(
    activityId: Int,
    modifier: Modifier = Modifier,
    onAnother: (() -> Unit)? = null,
) {
    val activity = TogetherActivities.byId(activityId) ?: return
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (onAnother == null) {
            SetupLabel(stringResource(R.string.together_activity_label), topPadding = 20.dp)
        } else {
            // Un solo bottone, icona a sinistra e "Cosa facciamo?": prima
            // erano l'etichetta in maiuscoletto e un'icona ↻ separata
            // accanto, due cose da capire invece di una (segnalato). Tocca e
            // cambia proposta; TalkBack annuncia l'azione "Un'altra".
            val anotherLabel = stringResource(R.string.together_activity_another)
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                modifier = Modifier
                    .padding(top = 16.dp, bottom = 10.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(onClickLabel = anotherLabel, onClick = onAnother),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.together_activity_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        Text(
            text = stringResource(activity.text),
            color = MaterialTheme.colorScheme.primary,
            fontSize = 16.sp,
            fontStyle = FontStyle.Italic,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        )
    }
}
