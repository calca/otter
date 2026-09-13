package com.calmotter.app.ui.mascot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Le mascotte "otter" usate dentro l'app (in aggiunta all'icona dell'app
 * stessa, "Still Otter", che vive come vector drawable in
 * res/drawable/ic_launcher_foreground.xml perché deve poter essere
 * referenziata anche da un adaptive-icon XML e dal widget Glance, non
 * solo da Compose).
 *
 * Tutte disegnano con Canvas usando i ruoli colore di MaterialTheme
 * (non hex fissi come nel mockup di design): seguono così automaticamente
 * la palette scelta dall'utente (Sage/Lavender/Terracotta) e il tema
 * chiaro/scuro, invece di essere fisse su un'unica combinazione.
 *
 * Fino a [OtterFloatMark] c'era anche "Otter at Rest"
 * (`OtterAtRestIllustration`), un'illustrazione separata per il primo step
 * dell'onboarding — rimossa: l'onboarding ora riusa [OtterFloatMark], la
 * stessa mascotte della Home, così la primissima cosa che l'utente vede è
 * già l'otter che ritroverà a ogni apertura dell'app.
 */

/**
 * Lontra vista dall'alto, a galla: il pulsante principale della Home
 * ("Living Pond", vedi specs/home-and-settings), riusata anche come
 * illustrazione del primo step dell'onboarding (vedi OnboardingScreen.kt).
 * Il corpo è "primary" a bassa opacità (rispetta la palette scelta —
 * Sage/Lavender/Terracotta — restando comunque tenue, non un blocco di
 * colore saturo), mentre il naso è "primary" a piena intensità come piccolo
 * accento. Il colore pieno resta comunque riservato per lo più all'anello
 * di avanzamento che le viene disegnato intorno durante una sessione
 * attiva sulla Home, l'unico punto in cui porta un'informazione reale
 * (quanto tempo è passato) invece di essere decorazione.
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

/**
 * "Pact Paws": due zampe in un badge circolare pieno (`primary` + contenuto
 * `onPrimary`), inclinate l'una verso l'altra come una stretta di mano —
 * originariamente la stessa costruzione della mascotte "Paws Together" di
 * `BlockScreen` (rimossa dal redesign di quella schermata, che ora riusa
 * [OtterFloatMark]+l'anello di avanzamento della Home), solo ruotate.
 * Usata nello step "Scegli la password insieme" dell'onboarding al posto
 * dell'emoji 🔒 di sistema, per legare l'icona al significato di quello
 * step (un patto tra due persone) invece che a un generico simbolo di
 * sicurezza. Vedi specs/mascot-marks/ per il confronto con le altre
 * proposte scartate.
 */
@Composable
fun PactPawsMark(modifier: Modifier = Modifier, markSize: Dp = 96.dp) {
    val bg = MaterialTheme.colorScheme.primary
    val paw = MaterialTheme.colorScheme.onPrimary
    val knuckle = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier.size(markSize)) {
        val s = size.width / 80f
        fun v(value: Float) = value * s

        drawCircle(color = bg, radius = size.width / 2f, center = center)

        rotate(degrees = -18f, pivot = Offset(v(28f), v(41f))) {
            drawRoundRect(
                color = paw,
                topLeft = Offset(v(21f), v(24f)),
                size = Size(v(14f), v(34f)),
                cornerRadius = CornerRadius(v(7f), v(7f)),
            )
        }
        rotate(degrees = 18f, pivot = Offset(v(52f), v(41f))) {
            drawRoundRect(
                color = paw,
                topLeft = Offset(v(45f), v(24f)),
                size = Size(v(14f), v(34f)),
                cornerRadius = CornerRadius(v(7f), v(7f)),
            )
        }
        drawCircle(color = knuckle, radius = v(1.4f), center = Offset(v(32f), v(30f)))
        drawCircle(color = knuckle, radius = v(1.4f), center = Offset(v(48f), v(30f)))
    }
}

/**
 * "Sprig": un rametto a due foglie disegnato a mano (Path piatto, non
 * un'emoji di sistema) — usato nell'ultimo step dell'onboarding ("Tutto
 * pronto") al posto dell'emoji 🌿. Stesso motivo del testo/emoji originale
 * (un rametto rigoglioso), ma nello stile Canvas del resto delle mascotte:
 * stelo "primary" pieno, foglie "primary" a bassa opacità come il pelo di
 * [OtterFloatMark].
 */
@Composable
fun SprigMark(modifier: Modifier = Modifier, markSize: Dp = 96.dp) {
    val leaf = MaterialTheme.colorScheme.primary.copy(alpha = 0.32f)
    val stem = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier.size(markSize)) {
        val s = size.width / 80f
        fun v(value: Float) = value * s

        val stemPath = Path().apply {
            moveTo(v(40f), v(62f))
            cubicTo(v(40f), v(45f), v(40f), v(30f), v(30f), v(20f))
        }
        drawPath(stemPath, color = stem, style = Stroke(width = v(2.4f), cap = StrokeCap.Round))

        val leafOne = Path().apply {
            moveTo(v(40f), v(50f))
            cubicTo(v(30f), v(46f), v(24f), v(36f), v(26f), v(26f))
            cubicTo(v(36f), v(28f), v(44f), v(36f), v(42f), v(48f))
            close()
        }
        drawPath(leafOne, color = leaf)

        val leafTwo = Path().apply {
            moveTo(v(40f), v(38f))
            cubicTo(v(50f), v(34f), v(56f), v(24f), v(54f), v(14f))
            cubicTo(v(44f), v(16f), v(36f), v(24f), v(38f), v(36f))
            close()
        }
        drawPath(leafTwo, color = leaf)
    }
}
