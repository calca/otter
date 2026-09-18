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
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
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

/**
 * Silhouette dell'otter ridotta all'osso (stessa unione di 3 cerchi di
 * [OtterFloatMark]/[OtterHistoryIcon], nessun dettaglio del viso — stessa
 * scelta già fatta per l'icona monocromatica dell'app, "più ridotta della
 * versione a colori" invece di una semplice ricolorazione) — usata come
 * "otter satellite" nella lobby di Tempo Insieme (Fase 2): un'istanza per
 * partecipante collegato, disposta sull'anello attorno a [OtterFloatMark]
 * da chi la chiama (`GroupPauseBluetoothLobbyHostScreen`), non da questo
 * composable, che disegna solo la singola sagoma.
 */
@Composable
fun OtterSatelliteMark(modifier: Modifier = Modifier, markSize: Dp = 32.dp, tint: Color = MaterialTheme.colorScheme.primary) {
    Canvas(modifier = modifier.size(markSize)) {
        val s = size.width / 40f
        fun v(value: Float) = value * s

        val silhouette = Path().apply {
            addOval(Rect(center = Offset(v(20f), v(22f)), radius = v(13f)))
            addOval(Rect(center = Offset(v(10f), v(13f)), radius = v(6f)))
            addOval(Rect(center = Offset(v(30f), v(13f)), radius = v(6f)))
        }
        drawPath(silhouette, color = tint)
    }
}

/**
 * Due otter (stessa sagoma di [OtterSatelliteMark]) inclinati l'uno verso
 * l'altro con un piccolo arco "segnale" nel punto in cui si toccano — stessa
 * idea di inclinazione di [PactPawsMark], applicata a due otter interi
 * invece che a due zampe, per illustrare "avvicina i telefoni" nelle
 * schermate NFC di Tempo Insieme senza bisogno di testo per capirlo.
 */
@Composable
fun OtterTapMark(modifier: Modifier = Modifier, markSize: Dp = 96.dp) {
    val furA = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
    val furB = MaterialTheme.colorScheme.primary
    val signal = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier.size(markSize)) {
        val s = size.width / 96f
        fun v(value: Float) = value * s

        fun otterSilhouette(cx: Float, cy: Float): Path = Path().apply {
            addOval(Rect(center = Offset(v(cx), v(cy)), radius = v(13f)))
            addOval(Rect(center = Offset(v(cx - 10f), v(cy - 9f)), radius = v(6f)))
            addOval(Rect(center = Offset(v(cx + 10f), v(cy - 9f)), radius = v(6f)))
        }

        rotate(degrees = -14f, pivot = Offset(v(26f), v(48f))) {
            drawPath(otterSilhouette(26f, 48f), color = furA)
        }
        rotate(degrees = 14f, pivot = Offset(v(70f), v(48f))) {
            drawPath(otterSilhouette(70f, 48f), color = furB)
        }
        drawArc(
            color = signal,
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(v(38f), v(30f)),
            size = Size(v(20f), v(20f)),
            style = Stroke(width = v(2.2f), cap = StrokeCap.Round),
        )
    }
}

/**
 * Icona compatta per la Cronologia (vedi HistoryScreen.kt, sostituisce il
 * vecchio pallino verde/rosso + etichetta testuale "Pausa di gruppo"):
 * sagoma dell'otter da sola per una pausa singola, con due archi "segnale"
 * sopra la testa se [showSignal] è vero (pausa fatta insieme) — stessa forma
 * degli archi di [OtterTapMark], qui statica invece che a corredo di un
 * gesto. Il completamento/interruzione non è affare di questa icona: vedi il
 * chip di sfondo colorato che la ospita in HistoryScreen.kt.
 */
@Composable
fun OtterHistoryIcon(
    modifier: Modifier = Modifier,
    markSize: Dp = 22.dp,
    showSignal: Boolean,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Canvas(modifier = modifier.size(markSize)) {
        val s = size.width / 40f
        fun v(value: Float) = value * s

        val silhouette = Path().apply {
            addOval(Rect(center = Offset(v(20f), v(22f)), radius = v(13f)))
            addOval(Rect(center = Offset(v(10f), v(13f)), radius = v(6f)))
            addOval(Rect(center = Offset(v(30f), v(13f)), radius = v(6f)))
        }
        drawPath(silhouette, color = tint)

        if (showSignal) {
            drawArc(
                color = tint,
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(v(11f), v(6f)),
                size = Size(v(18f), v(18f)),
                style = Stroke(width = v(2.2f), cap = StrokeCap.Round),
            )
            drawArc(
                color = tint.copy(alpha = 0.55f),
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(v(5f), v(0f)),
                size = Size(v(30f), v(30f)),
                style = Stroke(width = v(1.8f), cap = StrokeCap.Round),
            )
        }
    }
}

// Geometria dell'icona "Time Together — Minimal Tandem Paws" del redesign
// (SVG con viewBox 24×24, progetto Stitch 3158702940609906617). Come per la
// lontra Zen, i path curvi sono i dati originali passati a PathParser invece
// di essere ridisegnati a occhio; i polpastrelli restano cerchi, che è
// esattamente quello che sono nel sorgente.
private const val TANDEM_PAW_FRONT_PATH =
    "M6 14.5 C4.5 12 6.5 9 9.5 9 C12.5 9 14.5 12 13 14.5 C11.8 16.5 7.2 16.5 6 14.5 Z"
private const val TANDEM_PAW_BACK_PATH =
    "M18 16 C19.2 13.8 17.5 11 14.8 11 C12.2 11 10.5 13.8 11.8 16 C12.8 17.8 17 17.8 18 16 Z"

// Polpastrelli: centro x, centro y, raggio — nel sistema del viewBox 24.
private val TANDEM_TOES_FRONT = listOf(
    Triple(6.5f, 7.2f, 1.3f),
    Triple(9.2f, 5.8f, 1.4f),
    Triple(12f, 6.2f, 1.4f),
    Triple(14.2f, 8f, 1.2f),
)
private val TANDEM_TOES_BACK = listOf(
    Triple(17.8f, 9.5f, 1.2f),
    Triple(15.5f, 8.2f, 1.3f),
    Triple(13f, 8.4f, 1.3f),
    Triple(10.8f, 10f, 1.1f),
)

/**
 * Due impronte affiancate, una che segue l'altra: il marchio di "Tempo
 * insieme". Sostituisce le due zampette stilizzate prese da [PactPawsMark],
 * che dicevano "zampe" ma non "in due" — qui sono due tracce distinte, una
 * davanti e una dietro, che è poi il senso di una pausa condivisa.
 *
 * [PactPawsMark] resta com'era: lì le due zampe stanno dentro un badge e
 * fanno da sigillo al patto, non da traccia.
 *
 * I due toni del mockup (#1B3B2B e #8FA693) **non** sono ripresi alla
 * lettera, come per tutti gli altri marchi: sarebbero verde salvia in tutte
 * e otto le combinazioni di palette e tema. L'impronta davanti prende
 * [tint], quella dietro la stessa tinta schiarita verso la superficie.
 *
 * Schiarita, non resa trasparente: le due impronte si sovrappongono, e con
 * l'alpha la parte comune diventerebbe una terza tinta più scura, cioè una
 * macchia proprio dove le due tracce si incrociano.
 *
 * Schiarita del 30% e non del 45% come diceva il mockup: là la zampa salvia
 * sta su bianco, qui su un contenitore che è già `primary` al 14%, e a 45%
 * la traccia dietro spariva dentro il bottone.
 */
@Composable
fun TogetherMark(modifier: Modifier = Modifier, markSize: Dp = 20.dp, tint: Color = MaterialTheme.colorScheme.primary) {
    val backTint = lerp(tint, MaterialTheme.colorScheme.surface, 0.3f)
    val front = remember { PathParser().parsePathString(TANDEM_PAW_FRONT_PATH).toPath() }
    val back = remember { PathParser().parsePathString(TANDEM_PAW_BACK_PATH).toPath() }
    Canvas(modifier = modifier.size(markSize)) {
        val s = size.width / 24f
        fun v(value: Float) = value * s

        scale(scale = s, pivot = Offset.Zero) {
            drawPath(path = front, color = tint)
        }
        TANDEM_TOES_FRONT.forEach { (cx, cy, r) ->
            drawCircle(color = tint, radius = v(r), center = Offset(v(cx), v(cy)))
        }
        scale(scale = s, pivot = Offset.Zero) {
            drawPath(path = back, color = backTint)
        }
        TANDEM_TOES_BACK.forEach { (cx, cy, r) ->
            drawCircle(color = backTint, radius = v(r), center = Offset(v(cx), v(cy)))
        }
    }
}

// ── Zen Otter (redesign) ─────────────────────────────────────────────────

// Geometria presa dal logo "Calm Otter Zen" del redesign (SVG con viewport
// 120×120, progetto Stitch 3158702940609906617). I path curvi sono i dati
// originali passati a PathParser invece di essere riprodotti a occhio —
// stesso metodo già usato per l'icona "occhio" di PasswordOutlinedTextField.
private const val ZEN_HEAD_PATH =
    "M34 56 C34 40 45 32 60 32 C75 32 86 40 86 56 C89 64 88 74 82 81 " +
        "C75 88 68 89 60 89 C52 89 45 88 38 81 C32 74 31 64 34 56 Z"
private const val ZEN_EYE_LEFT_PATH = "M44 54 Q49 50 54 54"
private const val ZEN_EYE_RIGHT_PATH = "M66 54 Q71 50 76 54"
private const val ZEN_SMILE_PATH = "M56 68.5 Q60 71 64 68.5"

// Le guance rosate sono l'unico colore del marchio che non segue la palette:
// è un incarnato, non un accento di marca — tinto di verde o di terracotta
// smetterebbe di leggersi come tale. Resta comunque al 45% di opacità.
private val ZenBlush = Color(0xFFD7A99C)

/**
 * La lontra del redesign: muso di fronte, occhi chiusi in due archi sereni,
 * guance rosate, alone tenue attorno. Sostituisce [OtterFloatMark] dentro
 * l'anello di Home e BlockScreen; [OtterFloatMark] resta però in vita, sia
 * perché ancora usata altrove, sia come versione precedente a cui tornare.
 *
 * Come le altre mascotte, i colori vengono dai ruoli di MaterialTheme e non
 * dagli hex del mockup, così la lontra segue la palette scelta e il tema
 * chiaro/scuro invece di restare verde salvia in tutti e otto i casi.
 *
 * L'inchiostro di occhi, naso e bocca non è un ruolo di tema ma `primary`
 * spinta verso il nero: `onSurface` sarebbe chiaro in tema scuro e
 * sparirebbe sul pelo (che lì è chiaro anch'esso), mentre un nero fisso non
 * seguirebbe la palette. Mescolato così resta scuro in entrambi i temi e
 * mantiene la tinta di quello scelto.
 */
@Composable
fun OtterZenMark(modifier: Modifier = Modifier, markSize: Dp = 96.dp) {
    val primary = MaterialTheme.colorScheme.primary
    // Il pelo è `secondary` del design system (#8fa693 nelle palette verdi),
    // non `primary` sbiadita con l'alpha: quella dava un grigio-verde, questo
    // è il verde che il logo ha davvero.
    val accent = MaterialTheme.colorScheme.secondary
    val veil = MaterialTheme.colorScheme.tertiary
    val surface = MaterialTheme.colorScheme.surface
    // Chiari *tinti*, non la superficie pura: nel mockup muso e padiglioni
    // sono #EEF3ED e #D3DDD4, cioè bianchi virati di verde. Con `surface`
    // nuda le orecchie diventavano due ciambelle bianche staccate dalla
    // testa e il muso un ovale candido — visto su emulatore.
    val muzzle = veil.copy(alpha = 0.35f).compositeOver(surface)
    val innerEar = veil.compositeOver(surface)
    val ink = lerp(primary, Color.Black, 0.45f)

    val head = remember { PathParser().parsePathString(ZEN_HEAD_PATH).toPath() }
    val eyeLeft = remember { PathParser().parsePathString(ZEN_EYE_LEFT_PATH).toPath() }
    val eyeRight = remember { PathParser().parsePathString(ZEN_EYE_RIGHT_PATH).toPath() }
    val smile = remember { PathParser().parsePathString(ZEN_SMILE_PATH).toPath() }

    Canvas(modifier = modifier.size(markSize)) {
        val s = size.width / 120f
        fun v(value: Float) = value * s

        val fur = Brush.linearGradient(
            colors = listOf(accent, lerp(accent, primary, 0.35f)),
            start = Offset.Zero,
            end = Offset(size.width, size.height),
        )

        // Alone: lo stesso "respiro" dello stagno della Home, qui fermo.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(accent.copy(alpha = 0.22f), Color.Transparent),
                center = Offset(v(60f), v(60f)),
                radius = v(54f),
            ),
            radius = v(54f),
            center = Offset(v(60f), v(60f)),
        )

        // Orecchie, con il padiglione più chiaro.
        listOf(36f, 84f).forEach { cx ->
            drawCircle(brush = fur, radius = v(9f), center = Offset(v(cx), v(38f)))
            drawCircle(color = innerEar, radius = v(4.5f), center = Offset(v(cx), v(38f)))
        }

        scale(s, s, pivot = Offset.Zero) {
            drawPath(head, brush = fur)
        }

        drawOval(
            color = muzzle,
            topLeft = Offset(v(44f), v(55f)),
            size = Size(v(32f), v(24f)),
        )

        listOf(41f, 79f).forEach { cx ->
            drawCircle(
                color = ZenBlush.copy(alpha = 0.45f),
                radius = v(4.5f),
                center = Offset(v(cx), v(63f)),
            )
        }

        scale(s, s, pivot = Offset.Zero) {
            drawPath(eyeLeft, color = ink, style = Stroke(width = 2.5f, cap = StrokeCap.Round))
            drawPath(eyeRight, color = ink, style = Stroke(width = 2.5f, cap = StrokeCap.Round))
            drawPath(smile, color = ink, style = Stroke(width = 1.8f, cap = StrokeCap.Round))
        }

        drawOval(
            color = ink,
            topLeft = Offset(v(56.5f), v(60.4f)),
            size = Size(v(7f), v(5.2f)),
        )
        drawLine(
            color = ink,
            start = Offset(v(60f), v(65.5f)),
            end = Offset(v(60f), v(68f)),
            strokeWidth = v(2f),
            cap = StrokeCap.Round,
        )
    }
}
