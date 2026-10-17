package com.calmotter.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Il fumetto con la punta verso l'otter (vedi OtterHint per quando compare e
 * cosa dice). Arriva dopo due secondi, per non sovrapporsi all'arrivo in
 * Home; un tocco chiama [onTap] (chiuderlo, o aprire le pause programmate).
 * Con [autoHideMillis] sparisce anche da solo: quello del giorno è un
 * invito, non deve restare lì a coprire la Home.
 *
 * Aspetto (scelto fra quattro prove, segnalato "pesante"): non più un
 * blocco verde pieno con un triangolo attaccato sotto, che in scuro
 * diventava una macchia chiara, ma un fumetto vero — corpo e punta in un
 * solo tracciato ([BubbleShape]), chiaro con un'ombra leggera, bordo sottile,
 * testo in `primary`, come una voce dell'otter. Niente icona davanti (il
 * rametto è stato provato e tolto): chi parla lo dice già la punta. In scuro
 * un fondo bianco sarebbe di nuovo una macchia: lì è un verde tenue. Entra
 * crescendo dalla punta e galleggia con l'otter ([floatOffset], la stessa
 * oscillazione), così sembra detto da lui e non appoggiato sopra.
 */
@Composable
fun OtterHintBubble(
    text: String?,
    onTap: () -> Unit,
    onHidden: () -> Unit,
    modifier: Modifier = Modifier,
    autoHideMillis: Long? = null,
    floatOffset: State<Float>? = null,
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

    val context = LocalContext.current
    val reduceMotion = remember { animationsDisabled(context) }
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    val fill = if (dark) scheme.primary.copy(alpha = 0.22f).compositeOver(scheme.background) else scheme.surface

    AnimatedVisibility(
        visible = shown,
        enter = if (reduceMotion) {
            fadeIn(tween(600))
        } else {
            fadeIn(tween(400)) + scaleIn(
                animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
                initialScale = 0.85f,
                // Cresce dalla punta, cioè dall'otter.
                transformOrigin = TransformOrigin(0.5f, 1f),
            )
        },
        exit = fadeOut(tween(300)),
        modifier = modifier
            .offset(y = 6.dp)
            // Lettura in fase di disegno, come l'otter: nessuna ricomposizione
            // a ogni scatto dell'oscillazione.
            .graphicsLayer { translationY = (floatOffset?.value ?: 0f) * density },
    ) {
        Surface(
            shape = BubbleShape,
            color = fill,
            shadowElevation = if (dark) 0.dp else 3.dp,
            border = BorderStroke(1.dp, scheme.primary.copy(alpha = 0.12f)),
            modifier = Modifier.clickable(onClick = onTap),
        ) {
            Text(
                text = lastText,
                color = scheme.primary,
                fontWeight = FontWeight.Medium,
                // Le frasi hanno lunghezze diverse: le più lunghe vanno a
                // capo centrate invece di allargarsi fino ai bordi.
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .widthIn(max = 260.dp)
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp + TailHeight),
            )
        }
    }
}

private val TailHeight = 11.dp

/** Corpo e punta in un solo tracciato: la punta esce dal fumetto, senza giunzione. */
private val BubbleShape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        with(density) {
            val radius = 18.dp.toPx()
            val tailHeight = TailHeight.toPx()
            val tailWidth = 26.dp.toPx()
            val bodyHeight = size.height - tailHeight
            val cx = size.width / 2f
            val body = Path().apply {
                addRoundRect(RoundRect(0f, 0f, size.width, bodyHeight, CornerRadius(radius)))
            }
            // La punta arrotondata: due curve che si stringono verso il basso.
            val tail = Path().apply {
                moveTo(cx - tailWidth / 2f, bodyHeight - 1f)
                cubicTo(cx - tailWidth / 4f, bodyHeight, cx - tailWidth * 0.1f, bodyHeight + tailHeight, cx, bodyHeight + tailHeight)
                cubicTo(cx + tailWidth * 0.1f, bodyHeight + tailHeight, cx + tailWidth / 4f, bodyHeight, cx + tailWidth / 2f, bodyHeight - 1f)
                close()
            }
            return Outline.Generic(Path.combine(PathOperation.Union, body, tail))
        }
    }
}
