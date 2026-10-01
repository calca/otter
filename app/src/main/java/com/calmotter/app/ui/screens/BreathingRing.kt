package com.calmotter.app.ui.screens

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos

// Ritmo del respiro della pausa breve (specs/breathing-pause/): inspiro più
// corto dell'espiro, che è ciò che rallenta davvero.
internal const val INHALE_MILLIS = 4_000L
internal const val EXHALE_MILLIS = 6_000L
private const val BREATH_CYCLE_MILLIS = INHALE_MILLIS + EXHALE_MILLIS

// Il raggio minimo, come frazione di quello massimo: l'anello si stringe,
// non sparisce.
private const val MIN_BREATH_FRACTION = 0.72f

/** `true` nella prima parte del ciclo, l'inspiro. */
internal fun isInhaling(elapsedMillis: Long): Boolean =
    elapsedMillis.mod(BREATH_CYCLE_MILLIS) < INHALE_MILLIS

/**
 * Quanto è "pieno" il respiro, da 0 (espirato) a 1 (inspirato), con un
 * andamento morbido agli estremi invece di un rimbalzo lineare.
 */
internal fun breathFullness(elapsedMillis: Long): Float {
    val t = elapsedMillis.mod(BREATH_CYCLE_MILLIS)
    val linear = if (t < INHALE_MILLIS) {
        t.toFloat() / INHALE_MILLIS
    } else {
        1f - (t - INHALE_MILLIS).toFloat() / EXHALE_MILLIS
    }
    return ((1 - cos(linear * PI)) / 2).toFloat()
}

/** Animazioni disattivate dall'utente nelle impostazioni di sistema. */
internal fun animationsDisabled(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/**
 * Tempo trascorso dall'inizio del respiro, aggiornato a scatti (10 al
 * secondo) e non a ogni fotogramma: stessa misura già presa per le
 * increspature dello stagno, vedi specs/group-pause/design.md ("The pulse
 * was implemented wrong the first time").
 */
@Composable
internal fun rememberBreathClock(): State<Long> = produceState(0L) {
    val start = System.currentTimeMillis()
    while (true) {
        value = System.currentTimeMillis() - start
        delay(100)
    }
}

/**
 * L'anello della pausa respiro, al posto di quello di avanzamento: si
 * allarga mentre si inspira e si stringe mentre si espira. [fullness] è letto
 * solo in fase di disegno, così ogni scatto ridisegna senza ricomporre.
 */
@Composable
internal fun BreathingRing(fullness: () -> Float, modifier: Modifier = Modifier) {
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val ringColor = MaterialTheme.colorScheme.primary
    val fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)

    Canvas(modifier = modifier) {
        val strokeWidth = 4.dp.toPx()
        val maxRadius = (size.minDimension - strokeWidth) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = maxRadius * (MIN_BREATH_FRACTION + (1f - MIN_BREATH_FRACTION) * fullness())
        drawCircle(color = trackColor, radius = maxRadius, center = center, style = Stroke(width = strokeWidth))
        drawCircle(color = fillColor, radius = radius, center = center)
        drawCircle(color = ringColor, radius = radius, center = center, style = Stroke(width = strokeWidth))
    }
}
