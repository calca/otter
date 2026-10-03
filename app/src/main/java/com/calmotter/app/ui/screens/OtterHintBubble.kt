package com.calmotter.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.calmotter.app.R
import kotlinx.coroutines.delay

/**
 * "Toccami quando vuoi una pausa", un fumetto con la punta verso l'otter
 * (vedi OtterHint per quando compare). Arriva con una dissolvenza dopo due
 * secondi, per non sovrapporsi all'arrivo in Home; un tocco lo chiude.
 */
@Composable
fun OtterHintBubble(visible: Boolean, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) {
            delay(2_000)
            shown = true
        } else {
            shown = false
        }
    }
    val color = MaterialTheme.colorScheme.primary
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(600)),
        exit = fadeOut(tween(300)),
        modifier = modifier.offset(y = 6.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable(onClick = onDismiss),
        ) {
            Surface(shape = RoundedCornerShape(16.dp), color = color, shadowElevation = 2.dp) {
                Text(
                    text = stringResource(R.string.otter_hint),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
            // La punta, verso l'otter.
            Canvas(modifier = Modifier.size(width = 16.dp, height = 8.dp)) {
                val tip = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width / 2f, size.height)
                    close()
                }
                drawPath(tip, color)
            }
        }
    }
}
