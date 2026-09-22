package com.calmotter.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.calmotter.app.R
import com.calmotter.app.ui.mascot.OtterZenMark
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/**
 * TODO.md "7": estratto da MainScreen.kt (era 1171 righe, la più grande del
 * progetto) — questo file raggruppa "lo stagno": otter+anello+increspature,
 * lo stesso linguaggio visivo condiviso da [MainScreen] (increspature +
 * anello di avanzamento) e `BlockScreen` (solo l'anello + [rememberOtterFloatOffset],
 * vedi i commenti sulle singole funzioni per chi usa cosa). Nessun cambio di
 * comportamento: stesso codice, stesso package (`com.calmotter.app.ui.screens`),
 * quindi nessun import è cambiato altrove nel progetto.
 */

/**
 * Lo "stagno": increspature ambientali a riposo (puramente decorative, si
 * calmano appena parte una sessione) o anello di avanzamento funzionale
 * durante la pausa, con l'otter — il pulsante di avvio — sempre al centro.
 * L'otter è sempre toccabile quando non c'è una sessione attiva: [onStart]
 * (in MainScreen) decide se questo significhi avviare la pausa o spiegare
 * quali permessi mancano ancora.
 *
 * Il tap che avvia la pausa ha un'animazione di conferma propria (richiesta
 * esplicita — prima [onStart] scattava all'istante, un salto secco verso
 * BlockScreen senza alcun feedback sul tap stesso): [isStarting] blocca
 * ulteriori tap, fa "saltare" l'otter con una molla rimbalzante
 * ([otterScale]) e fa partire un impulso che si espande e sfuma
 * ([TapConfirmBurst], più marcato delle [AmbientRipples] continue di
 * sfondo) — solo al termine di quell'animazione [onStart] viene invocata
 * davvero, quindi lo scambio con BlockScreen avviene a gesto già "visto",
 * non a scapito della reattività (la sessione parte comunque in meno di
 * mezzo secondo).
 */
@Composable
internal fun PondOtter(
    sessionActive: Boolean,
    remainingMillis: Long,
    totalMillis: Long,
    onStart: () -> Unit,
    otterFloatOffset: State<Float>? = null,
    // false quando il marchio lo disegna [PersistentOtter] sopra la
    // dissolvenza: qui resta solo l'anello, che appartiene a questa
    // schermata e deve sfumare con lei.
    drawMark: Boolean = true,
    // Cambia a ogni onResume: fa ripartire l'oscillazione quando si torna
    // sulla schermata, vedi [rememberOtterFloatOffset].
    restartKey: Int = 0,
) {
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
            // Se onStart() ha davvero avviato la sessione, questo Composable
            // sta per uscire di scena (MainActivity passa a BlockScreen) e il
            // reset è ininfluente. Se invece mancano i permessi, onStart()
            // si limita a mostrare il dialogo esplicativo: questa schermata
            // resta a video, e senza il reset l'otter restava permanentemente
            // non toccabile (clickable è enabled solo quando !isStarting) —
            // bug reale: tap sull'otter senza permessi concessi, poi più
            // nessun tap ha effetto, nemmeno chiudendo il dialogo.
            isStarting = false
        }
    }

    Box(
        modifier = Modifier.size(260.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (!sessionActive) {
            // Le increspature vere e proprie sono ora uno sfondo a piena
            // pagina passato a [OtterAnchoredScreen] (vedi il parametro
            // `background` nella chiamata in [MainScreen]) invece che un
            // figlio di questo Box: qui restava solo [TapConfirmBurst].
            if (isStarting) {
                TapConfirmBurst(progress = burstProgress.value, modifier = Modifier.matchParentSize())
            }
        } else {
            val fraction = if (totalMillis > 0) {
                (1f - remainingMillis.toFloat() / totalMillis.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
            ProgressRing(fraction = fraction, modifier = Modifier.size(182.dp))
        }

        if (drawMark) {
            val floatOffset = otterFloatOffset
                ?: rememberOtterFloatOffset(
                    periodMillis = if (sessionActive) 5200 else 3200,
                    restartKey = restartKey,
                )

            // TODO.md "4.2": l'otter è il tasto di avvio principale
            // dell'app, ma OtterZenMark è disegnato con Canvas — senza
            // un'etichetta esplicita TalkBack lo annuncia come un
            // "Pulsante" muto, senza dire cosa fa. La stessa frase già
            // visibile sotto ("Tap Otter to start", home_start_hint) fa
            // anche da contentDescription — non serve inventarne una
            // seconda. Solo quando è davvero toccabile: durante una pausa
            // (sessionActive) l'otter non è un pulsante, non deve leggersi
            // come tale.
            val otterContentDescription = if (!sessionActive && !isStarting) {
                // .text, non la stringa grezza — vedi lo stesso commento in
                // PersistentOtter.kt.
                boldAnnotatedString(stringResource(R.string.home_start_hint)).text
            } else {
                null
            }
            Box(
                modifier = Modifier
                    .graphicsLayer { translationY = floatOffset.value * density }
                    .scale(otterScale)
                    .clip(CircleShape)
                    .clickable(enabled = !sessionActive && !isStarting, onClick = { isStarting = true })
                    .then(
                        if (otterContentDescription != null) {
                            Modifier.semantics { contentDescription = otterContentDescription }
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                OtterZenMark(markSize = OtterMarkSize)
            }
        }
    }
}

/**
 * I quattro dischi fermi dello stagno, in una `Canvas` **separata da quella
 * animata**.
 *
 * Non è una divisione estetica ma di costo: stando dentro [AmbientRipples]
 * venivano riempiti da capo a ogni fotogramma su tutta la pagina, e su
 * emulatore la sola Home teneva la CPU al 70-85% (misurato con `top`:
 * Impostazioni, che non anima nulla, stava a 0%). Qui dentro non si legge
 * alcuno stato animato, quindi Compose la disegna una volta e la riusa.
 */
@Composable
internal fun PondStill(centerY: Dp, modifier: Modifier = Modifier) {
    val ringColor = MaterialTheme.colorScheme.tertiary
    val brightColor = MaterialTheme.colorScheme.surfaceBright

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, centerY.toPx())

        // Lo stagno fermo: **tre dischi pieni** concentrici, di tonalità
        // alternate — pallido, chiaro, pallido — come nel design (prima erano
        // due anelli di contorno: segnalato con l'immagine alla mano, "sono
        // 3 cerchi concentrici"). Il disco di mezzo usa `surfaceBright`
        // perché nel mockup è più chiaro dello sfondo, non uguale.
        // Misurati sul mockup pixel per pixel, sulla riga che passa per il
        // **centro vero** dello stagno (y=145 del mockup, non y=127: la prima
        // lettura era presa più in alto e leggeva il disco sbagliato).
        // Confini a 27, 48, 64 e 80px dal centro su 226px di larghezza,
        // cioè 47, 83, 110 e 138dp su 390dp — qui riscalati.
        //
        // Sono **quattro** dischi e il bianco è il secondo: pallido attorno
        // alla mascotte, poi bianco, poi pallido, poi velo. Le due letture
        // precedenti sbagliavano proprio qui, una invertendo l'ordine e
        // l'altra scambiando il disco pallido interno per l'alone che
        // [OtterZenMark] si disegna da sé.
        val innerPaleRadius = 54.dp.toPx()
        val whiteRadius = 91.dp.toPx()
        val paleRadius = 121.dp.toPx()
        val veilRadius = 151.dp.toPx()
        drawCircle(color = ringColor, radius = veilRadius, center = center, alpha = 0.28f)
        drawCircle(color = ringColor, radius = paleRadius, center = center, alpha = 0.75f)
        drawCircle(color = brightColor, radius = whiteRadius, center = center)
        drawCircle(color = ringColor, radius = innerPaleRadius, center = center, alpha = 0.75f)

    }
}

/**
 * Increspature ambientali (3 anelli sfasati che si espandono e svaniscono in
 * loop): puramente decorative, segnalano "stagno in attesa". Tinte di
 * "primary" a opacità molto bassa (max ~0.18) — seguono la palette scelta
 * (Sage/Lavender/Terracotta) restando comunque tenui, non un colore acceso.
 *
 * Disegnate su un `Canvas` a piena pagina (passato come `background` a
 * [OtterAnchoredScreen], non più un figlio del piccolo `Box` dell'otter —
 * richiesto esplicitamente: "le onde dietro l'otter si estendono su tutta
 * la pagina, anche uscendo"), quindi il centro non è più quello del proprio
 * riquadro ma [centerY] passato dal chiamante, l'unico punto che
 * [OtterAnchoredScreen] garantisce identico in entrambe le schermate. Il
 * raggio massimo è ancorato a `size.height` (non più a `size.minDimension`
 * del vecchio riquadro 260dp) apposta perché ecceda le dimensioni della
 * pagina prima che l'ultimo anello sparisca: a quel punto l'alpha è già
 * vicina a zero, quindi "uscire dai bordi" si vede come una dissolvenza sul
 * limite dello schermo, non come un cerchio che si taglia di netto.
 */
@Composable
internal fun AmbientRipples(centerY: Dp, restartKey: Int, modifier: Modifier = Modifier) {
    // **Lo stagno si acquieta.** Dopo [SCENE_QUIET_AFTER_MILLIS] le onde
    // svaniscono e l'animazione si ferma del tutto: a schermata ferma il
    // consumo va a zero invece di restare lì a ridipingere per sempre.
    //
    // Non è solo una misura di risparmio, è ciò che fa l'acqua: un sasso
    // produce onde che *finiscono*. Quelle infinite erano l'artificio.
    //
    // Riparte quando si rientra nella schermata ([restartKey] è il
    // `resumeSignal` della Home, cambia a ogni onResume) — cioè quando
    // qualcuno torna a guardare lo stagno.
    var active by remember(restartKey) { mutableStateOf(true) }
    LaunchedEffect(restartKey) {
        active = true
        delay(SCENE_QUIET_AFTER_MILLIS)
        active = false
    }
    // Le onde in corso non si congelano a mezz'aria: sfumano in due secondi
    // e mezzo, poi l'orologio si spegne.
    val fade by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(2500, easing = LinearEasing),
        label = "rippleFade",
    )
    val running = active || fade > 0.01f
    // 9 secondi per giro, non 3,6: è un sasso caduto nell'acqua, non una
    // scansione radar. L'avanzamento è a scatti, vedi [steppedFraction].
    val tState = steppedFraction(RIPPLE_PERIOD_MILLIS, stepsPerSecond = RIPPLE_STEPS_PER_SECOND, running = running)
    if (!running) return
    // Le onde usano `tertiary` (#d5e0d5 nelle palette verdi), che nel design
    // system è esattamente il colore dei "ripple borders" — non `primary` a
    // bassa opacità, che dava un grigio.
    val ringColor = MaterialTheme.colorScheme.tertiary

    // Nessun `graphicsLayer()` qui: misurato su Galaxy S22, con e senza, la
    // CPU del processo resta sul 30% — un livello proprio non aiuta, perché
    // il costo non è propagare l'invalidazione ai vicini ma ridisegnare
    // questa tela, che è grande quanto lo schermo. Non aggiungiamo un
    // livello che non paga.
    Canvas(modifier = modifier) {
        val t = tState.value
        val center = Offset(size.width / 2f, centerY.toPx())
        val veilRadius = 151.dp.toPx()
        // Quanto cresce un anello prima di spegnersi. **Non** piu'
        // `size.height / 1.1` (circa 865dp): a quella velocita' l'onda usciva
        // dallo schermo dopo un quinto della sua vita e le altre due
        // continuavano fuori campo — se ne vedeva una sola, mentre di anelli
        // ce ne sono sempre tre. Con 300dp l'ultimo arco e' ancora dentro i
        // bordi quando svanisce, quindi se ne vedono due o tre insieme, che e'
        // cio' che fa uno stagno.
        val maxExtra = 300.dp.toPx()

        // Tre anelli sfasati, ciascuno con la sua vita indipendente. Ogni
        // anello imita ciò che fa un'increspatura vera:
        //
        // - **rallenta**: il raggio segue 1-(1-t)^2 invece di t, quindi parte
        //   svelto dal bordo dello stagno e si allarga sempre più piano.
        // - **si assottiglia**: il tratto passa da 3dp a 0,8dp mentre si
        //   allarga, perché la stessa energia si distribuisce su una
        //   circonferenza più lunga.
        // - **svanisce prima della fine**: l'opacità cala con (1-t)^1.8, così
        //   l'onda è già sparita quando arriverebbe ai bordi, invece di
        //   spegnersi di colpo là dove il cerchio si taglierebbe.
        listOf(0f, 0.33f, 0.66f).forEach { phase ->
            val localT = (t + phase) % 1f
            // t^0.8: rallenta, ma appena — con una decelerazione forte
            // (1-(1-t)^2) l'anello schizzava via dal bordo e passava quasi
            // tutta la sua vita lontano e sbiadito, cioè invisibile.
            val eased = localT.pow(0.8f)
            drawCircle(
                color = ringColor,
                radius = veilRadius + eased * maxExtra,
                center = center,
                // Dissolvenza quasi lineare: con (1-t)^1.3 il terzo anello
                // era gia' a un quinto di opacita' su un colore che di suo ha
                // poco contrasto, cioe' invisibile.
                alpha = (1f - localT) * 0.95f * fade,
                style = Stroke(width = (3.5f - 2.3f * eased).dp.toPx()),
            )
        }
    }
}

/**
 * Frazione 0f→1f che avanza a scatti discreti invece che a ogni fotogramma.
 *
 * Le due animazioni della Home — increspature e oscillazione dell'otter —
 * durano 9 e 3,2 secondi: a 60fps ridisegnavano 60 volte al secondo per
 * spostare le cose di frazioni di pixel. Scandirle più lentamente costa
 * proporzionalmente meno, ma sotto una certa soglia lo scatto si vede: vedi
 * [RIPPLE_STEPS_PER_SECOND] per dove è finito il compromesso e perché.
 *
 * `withInfiniteAnimationFrameMillis` e non un timer proprio: segue
 * l'orologio delle animazioni di Compose, quindi si ferma da sé quando la
 * composizione esce di scena e rispetta l'impostazione di sistema
 * "rimuovi animazioni".
 */
@Composable
private fun steppedFraction(periodMillis: Int, stepsPerSecond: Int = 20, running: Boolean = true): State<Float> {
    // **Lo stato torna come `State`, non come `Float`.** Se il valore venisse
    // letto qui in fase di composizione, ogni scatto ricomporrebbe chi lo
    // legge; letto invece dentro la lambda di disegno, Compose salta
    // composizione e layout e ridisegna soltanto. Misurato su Galaxy S22 con
    // `dumpsys gfxinfo`: GPU 3ms per fotogramma ma fotogramma totale 16ms,
    // con "Slow UI thread" su tutti — il costo era la ricomposizione, non il
    // disegno.
    val fractionState = remember { mutableFloatStateOf(0f) }
    var fraction by fractionState
    LaunchedEffect(periodMillis, stepsPerSecond, running) {
        if (!running) return@LaunchedEffect
        val steps = (periodMillis / 1000f * stepsPerSecond).toInt().coerceAtLeast(1)
        val stepMillis = (1000f / stepsPerSecond).toLong()
        while (true) {
            // L'aggancio all'orologio delle animazioni di Compose, e non un
            // timer proprio, è ciò che fa fermare tutto quando la schermata
            // non è visibile: nessun fotogramma, nessun risveglio. Verificato:
            // con l'app in background il processo sta a 0%.
            withInfiniteAnimationFrameMillis { now ->
                val step = ((now % periodMillis) / periodMillis.toFloat() * steps).toInt()
                val next = step / steps.toFloat()
                if (next != fraction) fraction = next
            }
            // **L'attesa qui in mezzo è il punto.** Senza, il ciclo si
            // riaggancia subito al fotogramma successivo: su un telefono a
            // 120Hz sono 120 risvegli al secondo per cambiare lo stato 15
            // volte, e la pipeline grafica non torna mai a riposo. Misurato
            // su Galaxy S22: 38-48% di un core contro il 3-5% che resta
            // aspettando fra un passo e l'altro.
            delay(stepMillis)
        }
    }
    return fractionState
}

/**
 * Dopo quanto la scena si ferma — onde e oscillazione — se nessuno torna a
 * guardarla. Riparte al rientro nella schermata.
 */
internal const val SCENE_QUIET_AFTER_MILLIS = 30_000L

/** Scatti al secondo delle increspature, vedi [steppedFraction]. */
// A 10 scatti al secondo le onde si vedevano avanzare a strappi: il bordo
// dell'anello salta una decina di pixel per volta, e l'easing `t^0.8` rende
// i primi salti i più lunghi, proprio dove l'anello è più nitido.
//
// Il costo è lineare nel numero di ridisegni. Misurato su Galaxy S22 a
// processo caldo, build debug, scartando il primo campione di ogni serie
// (che è il picco di avvio, non il regime):
//
//     10 scatti/s → 10-20% di un core
//     20 scatti/s → 20-30%
//     30 scatti/s → 30-40%
//
// Si paga 30: lo scatto sparisce, e la spesa è comunque limitata ai primi
// [SCENE_QUIET_AFTER_MILLIS] — dopo, la scena si ferma e il processo torna
// a zero. Trenta secondi al 30% di un core non scaldano niente; era
// l'animazione infinita a farlo.
private const val RIPPLE_STEPS_PER_SECOND = 30

// L'oscillazione dell'otter sta anch'essa a 30: chi la legge lo fa dentro un
// `graphicsLayer` (vedi [PondOtter]), quindi ogni scatto sposta soltanto un
// livello già disegnato invece di ridisegnarlo. Misurato: alzarla da 6 a 30
// non ha spostato la CPU del processo.
private const val OTTER_STEPS_PER_SECOND = 30

/** Durata di un giro completo di un'increspatura, vedi [AmbientRipples]. */
private const val RIPPLE_PERIOD_MILLIS = 9000

/**
 * Impulso "a tocco": un anello che si espande e sfuma più un lampo pieno al
 * centro che sfuma ancora più in fretta — un solo passaggio (non in loop
 * come [AmbientRipples]), guidato da [progress] (0f→1f, animato dal
 * chiamante). Dà peso visivo al tap di avvio invece del salto secco che
 * c'era prima verso BlockScreen.
 */
@Composable
internal fun TapConfirmBurst(progress: Float, modifier: Modifier = Modifier) {
    val ringColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier) {
        val baseRadius = size.minDimension / 5f
        val maxExtra = size.minDimension / 2f

        drawCircle(
            color = ringColor,
            radius = baseRadius + progress * maxExtra,
            alpha = (1f - progress) * 0.5f,
            style = Stroke(width = 3.dp.toPx()),
        )
        drawCircle(
            color = ringColor,
            radius = baseRadius * (1f + progress * 0.3f),
            alpha = (1f - progress) * (1f - progress) * 0.25f,
        )
    }
}

/**
 * Offset verticale animato per l'effetto "otter che fluttua" — condiviso fra
 * [PondOtter] (Home) e `BlockScreen`, così la mascotte fluttua allo stesso
 * identico modo ovunque compaia con l'anello di avanzamento intorno, non solo
 * qui. Non `private`: unica ragione per cui è definita in questo file e non
 * altrove è che [PondOtter] è stata la prima a usarla.
 */
@Composable
fun rememberOtterFloatOffset(periodMillis: Int, restartKey: Int = 0): State<Float> {
    // Anche l'otter si acquieta, come lo stagno (vedi [AmbientRipples]): non
    // è un dettaglio di risparmio ma la voce grossa. Misurato sul Mac che
    // ospita l'emulatore: a schermata ferma, con le sole onde spente, il
    // processo emulatore stava ancora al ~300% di CPU contro il ~9% ad app
    // chiusa — cioè quasi tutto quel consumo era questa oscillazione di 10dp.
    //
    // L'ampiezza scende a zero in due secondi e mezzo invece di fermarsi di
    // scatto, così l'otter si posa invece di inchiodarsi a mezz'aria.
    var active by remember(restartKey) { mutableStateOf(true) }
    LaunchedEffect(restartKey) {
        active = true
        delay(SCENE_QUIET_AFTER_MILLIS)
        active = false
    }
    val amplitudeState = animateFloatAsState(
        targetValue = if (active) 5f else 0f,
        animationSpec = tween(2500, easing = LinearEasing),
        label = "otterBobAmplitude",
    )
    val running = active || amplitudeState.value > 0.05f
    // Una sinusoide sulla sorgente a scatti delle increspature: il periodo
    // completo è andata+ritorno, quindi il doppio di quello che chiedeva
    // `RepeatMode.Reverse`.
    val tState = steppedFraction(periodMillis * 2, stepsPerSecond = OTTER_STEPS_PER_SECOND, running = running)
    // `derivedStateOf`: il seno si ricalcola quando serve, ma chi legge il
    // risultato lo fa in fase di disegno (vedi `graphicsLayer` in PondOtter),
    // quindi nessuna ricomposizione per oscillare di 10dp.
    return remember { derivedStateOf { sin(tState.value * 2f * PI.toFloat()) * amplitudeState.value } }
}

/**
 * Chiusura dell'anello a fine pausa: l'anello completo si stacca dal suo
 * raggio, si allarga verso l'esterno e svanisce.
 *
 * È l'esatto contrario di [TapConfirmBurst], che all'avvio porta un'onda
 * verso l'interno stringendosi sull'otter — stesso vocabolario visivo letto
 * al rovescio, invece di introdurre un'animazione nuova per la fine.
 *
 * Usata solo alla **scadenza naturale**: allo sblocco con password l'anello
 * non è al 100%, e vederlo "compiersi" racconterebbe una cosa che non è
 * successa. Lì resta la sola dissolvenza — vedi BlockScreen.
 *
 * [progress] va da 0 (anello fermo al suo posto) a 1 (svanito).
 */
@Composable
fun RingReleaseBurst(progress: Float, modifier: Modifier = Modifier) {
    val ringColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier) {
        val strokeWidth = 4.dp.toPx()
        // Parte esattamente dov'era l'anello di avanzamento, così lo
        // sostituisce senza scarti di raggio.
        val startRadius = (size.minDimension - strokeWidth) / 2f
        val radius = startRadius * (1f + progress * 0.45f)

        drawCircle(
            color = ringColor,
            radius = radius,
            // Si assottiglia mentre si allarga: sembra che si allenti, non
            // che venga ingrandito.
            alpha = (1f - progress) * (1f - progress),
            style = Stroke(width = strokeWidth * (1f - progress * 0.6f)),
        )
    }
}

/**
 * Anello di avanzamento della sessione attiva: l'unico punto della Home
 * dove "primary" è usato a piena intensità (non a bassa opacità come nel
 * resto della scena), perché qui porta un'informazione reale — quanto è
 * passato — e non è decorazione. Non `private`: condiviso anche da
 * `BlockScreen`, che dopo il redesign mostra lo stesso identico
 * anello+otter fluttuante della Home invece di un badge statico.
 */
@Composable
fun ProgressRing(fraction: Float, modifier: Modifier = Modifier) {
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val progressColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier) {
        val strokeWidth = 4.dp.toPx()
        val diameter = size.minDimension - strokeWidth
        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
        val arcSize = Size(diameter, diameter)
        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
        drawArc(
            color = progressColor,
            startAngle = -90f,
            sweepAngle = 360f * fraction,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
    }
}
