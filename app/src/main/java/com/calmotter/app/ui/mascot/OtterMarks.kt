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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
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

        // Coda, corpo e testa in un solo Path invece di quattro draw call
        // separate: si sovrappongono a coppie (coda~corpo, corpo~testa,
        // testa~orecchio), quindi disegnarle una per una farebbe sommare
        // l'alpha dello stesso "body" semi-trasparente proprio nelle zone
        // di sovrapposizione, scurendole — lo stesso artefatto già corretto
        // in OtterFloatMark. Un solo Path viene riempito in un'unica
        // passata, quindi l'alpha è applicata una sola volta ovunque.
        val silhouette = Path().apply {
            moveTo(v(235f), v(95f))
            quadraticTo(v(292f), v(84f), v(306f), v(122f))
            quadraticTo(v(292f), v(158f), v(235f), v(155f))
            close()
            addRoundRect(
                RoundRect(
                    rect = Rect(Offset(v(70f), v(90f)), Size(v(170f), v(80f))),
                    cornerRadius = CornerRadius(v(40f), v(40f)),
                )
            )
            addOval(Rect(center = Offset(v(60f), v(110f)), radius = v(34f)))
            addOval(Rect(center = Offset(v(48f), v(86f)), radius = v(11f)))
        }
        drawPath(silhouette, color = body)

        val eye = Path().apply {
            moveTo(v(46f), v(110f))
            quadraticTo(v(58f), v(118f), v(70f), v(110f))
        }
        drawPath(eye, color = ink, style = Stroke(width = v(5f), cap = StrokeCap.Round))
        drawOval(color = ink, topLeft = Offset(v(32f), v(116f)), size = Size(v(12f), v(8f)))

        // Le due zampe si sovrappongono leggermente al centro: stesso
        // motivo, un solo Path invece di due drawOval.
        val paws = Path().apply {
            addOval(Rect(Offset(v(82f), v(74f)), Size(v(30f), v(24f))))
            addOval(Rect(Offset(v(108f), v(74f)), Size(v(30f), v(24f))))
        }
        drawPath(paws, color = paw)

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

/**
 * Lontra vista dall'alto, a galla: il pulsante principale della Home
 * ("Living Pond", vedi specs/home-and-settings). Segue la stessa regola di
 * [OtterAtRestIllustration]: il corpo è "primary" a bassa opacità (rispetta
 * la palette scelta — Sage/Lavender/Terracotta — restando comunque tenue,
 * non un blocco di colore saturo), mentre il naso è "primary" a piena
 * intensità come piccolo accento (lo stesso ruolo del sassolino nell'altra
 * illustrazione). Il colore pieno resta comunque riservato per lo più
 * all'anello di avanzamento che le viene disegnato intorno durante una
 * sessione attiva, l'unico punto in cui porta un'informazione reale (quanto
 * tempo è passato) invece di essere decorazione.
 */
@Composable
fun OtterFloatMark(modifier: Modifier = Modifier, markSize: Dp = 96.dp) {
    val fur = MaterialTheme.colorScheme.primary.copy(alpha = 0.32f)
    val face = MaterialTheme.colorScheme.background
    val nose = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier.size(markSize)) {
        val s = size.width / 108f
        fun v(value: Float) = value * s

        // Corpo e orecchie in un solo Path, non tre drawCircle separate: il
        // corpo e le orecchie si sovrappongono (i loro centri sono più
        // vicini della somma dei raggi), quindi tre disegni separati dello
        // stesso colore semi-trasparente si sommerebbero lì dove si
        // sovrappongono, facendo apparire le orecchie più scure del resto
        // del corpo per puro artefatto di compositing — non un colore
        // diverso voluto. Un solo Path viene invece riempito in un'unica
        // passata, quindi l'alpha è applicata una sola volta ovunque.
        val silhouette = Path().apply {
            addOval(Rect(center = Offset(v(54f), v(58f)), radius = v(25f)))
            addOval(Rect(center = Offset(v(40f), v(42f)), radius = v(10f)))
            addOval(Rect(center = Offset(v(68f), v(42f)), radius = v(10f)))
        }
        drawPath(silhouette, color = fur)

        drawOval(color = face, topLeft = Offset(v(40f), v(59.8f)), size = Size(v(12f), v(4.4f)))
        drawOval(color = face, topLeft = Offset(v(56f), v(59.8f)), size = Size(v(12f), v(4.4f)))
        drawCircle(color = nose, radius = v(2.5f), center = Offset(v(54f), v(72f)))
    }
}
