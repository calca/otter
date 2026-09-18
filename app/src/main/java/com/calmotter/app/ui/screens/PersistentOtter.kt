package com.calmotter.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.calmotter.app.ui.mascot.OtterZenMark

/**
 * L'otter, come **unico nodo** che non partecipa allo scambio fra Home e
 * schermata di blocco.
 *
 * ## Perché non è più un elemento condiviso
 *
 * Prima ogni schermata disegnava il proprio otter e [MainActivity][com.calmotter.app.MainActivity]
 * li legava con `SharedTransitionLayout`/`sharedElement`, che ne animava i
 * bounds da una posizione all'altra. Erano tre meccanismi che dovevano
 * mettersi d'accordo sulla stessa cosa:
 *
 * 1. [OtterAnchoredScreen], che esiste per mettere l'otter **nello stesso
 *    punto** nelle due schermate;
 * 2. l'elemento condiviso, che esiste per **animarne lo spostamento** fra
 *    le due;
 * 3. le trasformazioni `graphicsLayer` (oscillazione, ingrandimento al
 *    tocco), che il layout **non vede**.
 *
 * I primi due lavoravano uno contro l'altro: il secondo animava un
 * movimento che il primo esisteva per impedire. Il terzo è quello che
 * perdeva: l'ingrandimento a 1,18 stava su un `Box` genitore, l'overlay
 * dell'elemento condiviso leggeva i bounds di layout, e allo scambio quel
 * fattore spariva in un fotogramma — misurato sul dispositivo, inchiostro
 * dell'otter largo 119px in uno scatto e 101 in quello dopo, e
 * 101 × 1,18 = 119.
 *
 * Qui non c'è più niente da consegnare. L'otter sta **sopra** la
 * dissolvenza fra le due schermate, che scambiano tutto il resto; il nodo è
 * lo stesso prima, durante e dopo, quindi non può né saltare né scivolare
 * né perdere una trasformazione. Oscillazione e ingrandimento tornano
 * modifier normali su un nodo qualunque.
 *
 * Conseguenza dichiarata: questo disegno **vieta** che Home e blocco
 * mettano l'otter in punti diversi. Oggi è l'invariante voluta — se un
 * domani servisse divergere, va rifatto, non aggirato con un secondo otter.
 *
 * ## Dove finisce
 *
 * Il chiamante lo posiziona con [otterCenterY], la stessa funzione che
 * [OtterAnchoredScreen] usa per allineare lo slot vuoto che le due
 * schermate continuano a riservargli nella colonna. Una formula sola, due
 * chiamanti.
 *
 * Nota che lo slot resta **vuoto ma della stessa altezza**: serve ancora a
 * far scorrere il contenuto attorno all'otter, e a tenere identico il
 * layout delle due schermate quando vengono usate da sole (vedi
 * [BlockScreen] da `BlockOverlayActivity` e dal servizio di accessibilità,
 * che non hanno nessuna transizione da cui arrivare e disegnano il proprio
 * otter dentro lo slot).
 */
@Composable
fun PersistentOtter(
    floatOffset: State<Float>,
    enabled: Boolean,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Stato interno e non issato: questo Composable non esce mai di scena,
    // quindi non c'è il rischio che si perda a metà di un tocco — che è poi
    // tutto il punto di questo file.
    var isStarting by remember { mutableStateOf(false) }

    val otterScale by animateFloatAsState(
        targetValue = if (isStarting) 1.18f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "otterTapScale",
    )
    val burstProgress = remember { Animatable(0f) }
    LaunchedEffect(isStarting) {
        if (isStarting) {
            burstProgress.snapTo(0f)
            burstProgress.animateTo(1f, animationSpec = tween(380, easing = FastOutSlowInEasing))
            onStart()
            // Se `onStart()` ha davvero avviato la sessione, la schermata
            // sotto sta cambiando ma **questo nodo resta**: senza il
            // ripristino l'otter resterebbe ingrandito per sempre. Se invece
            // mancano i permessi, `onStart()` mostra soltanto il dialogo e
            // qui si torna comunque toccabili — era un bug reale nella
            // versione precedente (un tocco senza permessi e l'otter non
            // rispondeva più).
            isStarting = false
        }
    }

    Box(
        modifier = modifier.size(OtterHaloSize),
        contentAlignment = Alignment.Center,
    ) {
        if (isStarting) {
            TapConfirmBurst(progress = burstProgress.value, modifier = Modifier.size(OtterHaloSize))
        }
        Box(
            modifier = Modifier
                // Le due trasformazioni stanno sullo **stesso nodo** che
                // disegna il marchio, non su un genitore: è la lezione del
                // bug che questo file chiude.
                .graphicsLayer { translationY = floatOffset.value * density }
                .scale(otterScale)
                .clip(CircleShape)
                .clickable(enabled = enabled && !isStarting, onClick = { isStarting = true }),
            contentAlignment = Alignment.Center,
        ) {
            OtterZenMark(markSize = OtterMarkSize)
        }
    }
}

/** Il marchio dell'otter, della stessa misura in Home e in pausa. */
internal val OtterMarkSize = 118.dp

/** Riquadro attorno al marchio, quanto basta al lampo del tocco. */
internal val OtterHaloSize = 260.dp
