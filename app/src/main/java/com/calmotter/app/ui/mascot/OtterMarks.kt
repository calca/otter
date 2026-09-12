package com.calmotter.app.ui.mascot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Le due mascotte "otter" usate dentro l'app (in aggiunta all'icona
 * dell'app stessa, "Still Otter", che vive come vector drawable in
 * res/drawable/ic_launcher_foreground.xml perché deve poter essere
 * referenziata anche da un adaptive-icon XML e dal widget Glance, non
 * solo da Compose).
 *
 * Entrambe disegnano con Canvas usando i ruoli colore di MaterialTheme
 * (non hex fissi come nel mockup di design): seguono così automaticamente
 * la palette scelta dall'utente (Sage/Lavender/Terracotta) e il tema
 * chiaro/scuro, invece di essere fisse su un'unica combinazione.
 */

/**
 * "Otter at Rest": lontra sdraiata sulla schiena con la pietra sul petto.
 * Sostituisce l'emoji 🦦 nel primo step dell'onboarding, dove c'è spazio
 * per un'illustrazione invece di una semplice icona.
 */
@Composable
fun OtterAtRestIllustration(modifier: Modifier = Modifier) {
    // CalmOtterTheme imposta esplicitamente solo background/surface/
    // onBackground/onSurface/primary/onPrimary/error/onError per palette
    // (vedi ui/theme/CalmOtterTheme.kt): surfaceVariant/primaryContainer
    // NON sono personalizzati e restano al default M3 (un viola-grigio
    // fisso), quindi non seguono Sage/Lavender/Terracotta — non vanno usati
    // qui. Corpo e zampe sono invece due intensità della stessa tinta
    // "primary", garantite entrambe legate alla palette scelta.
    val body = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    val paw = MaterialTheme.colorScheme.primary.copy(alpha = 0.32f)
    val ink = MaterialTheme.colorScheme.onSurface
    val pebble = MaterialTheme.colorScheme.primary
    val water = MaterialTheme.colorScheme.onSurface

    Canvas(
        modifier = modifier
            .width(220.dp)
            .aspectRatio(320f / 200f)
    ) {
        // Coordinate derivate dal mockup (viewBox 320x200): un solo
        // fattore di scala uniforme, dato che l'aspect ratio è fissato.
        val s = size.width / 320f
        fun v(value: Float) = value * s

        val waterLine = Path().apply {
            moveTo(v(10f), v(166f))
            quadraticTo(v(150f), v(182f), v(290f), v(166f))
        }
        drawPath(waterLine, color = water, alpha = 0.35f, style = Stroke(width = v(3f), cap = StrokeCap.Round))

        val tail = Path().apply {
            moveTo(v(235f), v(95f))
            quadraticTo(v(292f), v(84f), v(306f), v(122f))
            quadraticTo(v(292f), v(158f), v(235f), v(155f))
            close()
        }
        drawPath(tail, color = body)

        drawRoundRect(
            color = body,
            topLeft = Offset(v(70f), v(90f)),
            size = Size(v(170f), v(80f)),
            cornerRadius = CornerRadius(v(40f), v(40f))
        )

        drawCircle(color = body, radius = v(34f), center = Offset(v(60f), v(110f)))
        drawCircle(color = body, radius = v(11f), center = Offset(v(48f), v(86f)))

        val eye = Path().apply {
            moveTo(v(46f), v(110f))
            quadraticTo(v(58f), v(118f), v(70f), v(110f))
        }
        drawPath(eye, color = ink, style = Stroke(width = v(5f), cap = StrokeCap.Round))
        drawOval(color = ink, topLeft = Offset(v(32f), v(116f)), size = Size(v(12f), v(8f)))

        drawOval(color = paw, topLeft = Offset(v(82f), v(74f)), size = Size(v(30f), v(24f)))
        drawOval(color = paw, topLeft = Offset(v(108f), v(74f)), size = Size(v(30f), v(24f)))
        drawCircle(color = pebble, radius = v(10f), center = Offset(v(110f), v(78f)))
    }
}

/**
 * "Paws Together": le due zampe che si tengono (comportamento reale delle
 * lontre marine mentre dormono, per non allontanarsi alla deriva) disegnate
 * come due barre arrotondate — leggibili anche come simbolo ⏸. Usata sulla
 * schermata di blocco, l'unico punto dell'app dove "in pausa" è letteralmente
 * il contenuto dello schermo.
 */
@Composable
fun PausePawsMark(modifier: Modifier = Modifier, markSize: Dp = 72.dp) {
    val bg = MaterialTheme.colorScheme.primary
    val face = MaterialTheme.colorScheme.onPrimary
    val ink = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier.size(markSize)) {
        val s = size.width / 240f
        fun v(value: Float) = value * s

        drawCircle(color = bg, radius = size.width / 2f, center = center)

        drawCircle(color = face, radius = v(17f), center = Offset(v(92f), v(64f)))
        drawCircle(color = face, radius = v(17f), center = Offset(v(148f), v(64f)))
        drawCircle(color = face, radius = v(46f), center = Offset(v(120f), v(92f)))

        val leftEye = Path().apply {
            moveTo(v(96f), v(91f))
            quadraticTo(v(104f), v(97f), v(112f), v(91f))
        }
        val rightEye = Path().apply {
            moveTo(v(128f), v(91f))
            quadraticTo(v(136f), v(97f), v(144f), v(91f))
        }
        drawPath(leftEye, color = ink, style = Stroke(width = v(5f), cap = StrokeCap.Round))
        drawPath(rightEye, color = ink, style = Stroke(width = v(5f), cap = StrokeCap.Round))
        drawOval(color = ink, topLeft = Offset(v(115f), v(102.5f)), size = Size(v(10f), v(7f)))

        drawRoundRect(
            color = face,
            topLeft = Offset(v(98f), v(140f)),
            size = Size(v(18f), v(58f)),
            cornerRadius = CornerRadius(v(9f), v(9f))
        )
        drawRoundRect(
            color = face,
            topLeft = Offset(v(124f), v(140f)),
            size = Size(v(18f), v(58f)),
            cornerRadius = CornerRadius(v(9f), v(9f))
        )
        drawCircle(color = bg, radius = v(2.6f), center = Offset(v(107f), v(150f)))
        drawCircle(color = bg, radius = v(2.6f), center = Offset(v(133f), v(150f)))
    }
}
