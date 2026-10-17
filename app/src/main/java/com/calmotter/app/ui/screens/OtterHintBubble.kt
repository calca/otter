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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Il fumetto con la punta verso l'otter (vedi OtterHint per quando compare e
 * cosa dice). Arriva con una dissolvenza dopo due secondi, per non
 * sovrapporsi all'arrivo in Home; un tocco chiama [onTap] (chiuderlo, o
 * aprire le pause programmate). Con [autoHideMillis] sparisce anche da solo:
 * quello del giorno è un invito, non deve restare lì a coprire la Home.
 */
@Composable
fun OtterHintBubble(
    text: String?,
    onTap: () -> Unit,
    onHidden: () -> Unit,
    modifier: Modifier = Modifier,
    autoHideMillis: Long? = null,
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(text) {
        if (text != null) {
            delay(2_000)
            shown = true
            if (autoHideMillis != null) {
                delay(autoHideMillis)
                shown = false
                onHidden()
            }
        } else {
            shown = false
        }
    }
    // L'ultimo testo resta durante la dissolvenza in uscita.
    var lastText by remember { mutableStateOf("") }
    if (text != null) lastText = text
    val color = MaterialTheme.colorScheme.primary
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(600)),
        exit = fadeOut(tween(300)),
        modifier = modifier.offset(y = 6.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable(onClick = onTap),
        ) {
            Surface(shape = RoundedCornerShape(16.dp), color = color, shadowElevation = 2.dp) {
                Text(
                    text = lastText,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Medium,
                    // Le frasi del giorno hanno lunghezze diverse: le più
                    // lunghe vanno a capo centrate invece di allargarsi fino
                    // ai bordi della Home.
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .widthIn(max = 260.dp)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
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
