package com.calmotter.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calmotter.app.R
import com.calmotter.app.ui.mascot.OtterZenMark
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.State

/**
 * Ultimo passo comune a chi crea e chi si unisce a una pausa di gruppo
 * (vedi specs/group-pause/design.md, "handshake poi autonomia"): una volta
 * che questo dispositivo conosce durata+orario di inizio concordati, non
 * serve più nessuna connessione — questo composable si limita a contare
 * alla rovescia in locale (`LaunchedEffect`/`delay`, stesso idioma del
 * conto alla rovescia di lockout in ChangePasswordScreen.kt) e, allo scadere,
 * chiama [onReady] una sola volta (avvia la sessione — SessionManager
 * resta del tutto invariato, vedi startSession(isGroupSession = true)).
 *
 * [header] ospita contenuto specifico del chiamante sopra al conto alla
 * rovescia — il QR/codice da condividere per GroupPauseHostScreen, nulla
 * per GroupPauseJoinScreen (che l'ha già usato per arrivare qui).
 *
 * L'otter (`OtterZenMark`, 88dp) sta qui e non dentro [header], apposta:
 * segnalato contro il mockup ("Time Together - QR & Code") che la pagina
 * QR era l'unica dell'intero flusso di Tempo Insieme senza una mascotte —
 * bivio, lobby, entrambe le attese hanno tutte il proprio otter, questa
 * schermata no. Messo qui invece che nel solo `header` della pagina QR
 * perché lo stesso vuoto c'era anche nel passo di conto alla rovescia
 * raggiunto dalla lobby dal vivo (`header = {}`, nessun contenuto): un
 * componente condiviso vale la stessa cura ovunque venga usato, non solo
 * dove qualcuno l'ha notato per primo.
 *
 * Limite noto e documentato (specs/group-pause/requirements.md): se il
 * processo viene ucciso mentre questa schermata è in background durante
 * l'attesa, il conto alla rovescia si ferma — nessun AlarmManager di
 * riserva, scelta deliberata per restare nel perimetro di questa fase
 * (finestra di pochi minuti, rischio accettato).
 */
/**
 * Alpha 1↔0.35 a onda triangolare, a scatti invece che a ogni fotogramma —
 * stesso idioma di `steppedFraction` in MainScreen.kt (duplicato qui e non
 * condiviso: è privato là, e un puntino che pulsa non ha bisogno della
 * generalità di quella funzione, solo del suo principio). 10 passi al
 * secondo bastano a un'onda di 1,8s per leggersi morbida; vedi il commento
 * al punto d'uso per la misura che ha portato a scriverla così.
 */
@Composable
private fun rememberPulseAlpha(periodMillis: Int = 1800, stepsPerSecond: Int = 10): State<Float> {
    val alphaState = remember { mutableFloatStateOf(1f) }
    var alpha by alphaState
    LaunchedEffect(periodMillis, stepsPerSecond) {
        val steps = (periodMillis / 1000f * stepsPerSecond).toInt().coerceAtLeast(1)
        val stepMillis = (1000f / stepsPerSecond).toLong()
        while (true) {
            withInfiniteAnimationFrameMillis { now ->
                val step = ((now % periodMillis) / periodMillis.toFloat() * steps).toInt()
                val fraction = step / steps.toFloat()
                // Triangolare: sale nella prima metà del periodo, scende
                // nella seconda — lo stesso su e giù di un
                // `RepeatMode.Reverse`, senza bisogno di quel parametro.
                val triangle = if (fraction < 0.5f) fraction * 2f else (1f - fraction) * 2f
                val next = 1f - triangle * 0.65f
                if (next != alpha) alpha = next
            }
            delay(stepMillis)
        }
    }
    return alphaState
}

@Composable
fun GroupPauseCountdownScreen(
    durationMinutes: Int,
    startAtEpochMillis: Long,
    onReady: () -> Unit,
    onCancel: () -> Unit,
    // `false` quando [header] mostra già "fra quanto/per quanto" fusi nella
    // sua stessa frase (il percorso QR, su richiesta — vedi
    // specs/group-pause/design.md) e la riga separata sotto sarebbe una
    // ripetizione. Il countdown "puro" post-lobby dal vivo (header vuoto)
    // non ha altro posto dove mostrarlo, quindi resta `true` di default.
    showCountdownLine: Boolean = true,
    header: @Composable (minutes: Int, seconds: Int) -> Unit = { _, _ -> },
) {
    var remainingMillis by remember {
        mutableLongStateOf((startAtEpochMillis - System.currentTimeMillis()).coerceAtLeast(0L))
    }

    LaunchedEffect(startAtEpochMillis) {
        while (true) {
            val remaining = startAtEpochMillis - System.currentTimeMillis()
            remainingMillis = remaining.coerceAtLeast(0L)
            if (remaining <= 0L) {
                onReady()
                break
            }
            delay(200L)
        }
    }

    val totalSeconds = (remainingMillis / 1000L).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60

    // Niente più CalmCard: sfondo piatto come il resto del flusso di Tempo
    // Insieme (vedi il commento di classe di
    // GroupPauseBluetoothLobbyHostScreen per il perché).
    //
    // Cancel ancorato al fondo pagina, su richiesta esplicita — stesso
    // schema già applicato alla lobby host (vedi il commento lì): il resto
    // del contenuto vive in una Column interna con weight(1f) e la stessa
    // `Arrangement.Center` che CalmScreenColumn usava di default per tutto,
    // così resta centrato *nello spazio sopra il bottone* invece che nella
    // pagina intera, mentre Cancel resta l'ultima cosa in basso a
    // prescindere da quanto contenuto porta [header] (vuoto per il
    // countdown "puro" dopo una lobby dal vivo, pieno per il percorso QR).
    CalmScreenColumn(contentPadding = PaddingValues(32.dp), verticalArrangement = Arrangement.Top) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            OtterZenMark(markSize = 88.dp, modifier = Modifier.padding(bottom = 14.dp))
            header(minutes, seconds)

            // **Un'unica riga invece di due**, "fra quanto" e "per quanto" nello
            // stesso respiro — chiesto direttamente sul design di Stitch ("come
            // da design, che dici?"). Prima erano impilate: il conto alla
            // rovescia grande e in risalto, la durata sotto come didascalia.
            // Buona gerarchia, ma la richiesta è esplicita e la pillola del
            // mockup regge bene entrambi i fatti insieme, quindi qui si va con
            // quella — un contenitore riconoscibile come "stato", non due frasi
            // separate.
            //
        // Il puntino pulsante: l'unica animazione perpetua di questo file,
            // e la ragione per cui è ammessa dove altrove in questa app non lo
            // è (vedi Home/PondStill, la lobby, il bivio, tutti fermi apposta):
            // questa schermata **non può restare aperta a tempo indeterminato**
            // — il conto alla rovescia la chiude da sé in pochi minuti al più —
            // quindi non c'è il problema di un'animazione senza fine, e non c'è
            // una scelta da distrarre: qui non si decide nulla, si aspetta.
            //
            // La sorgente però NON è `rememberInfiniteTransition`/`animateFloat`
            // dirette: la prima versione le usava, e segnalata la CPU che
            // "frullava" proprio su questa schermata — misurato con /proc/<pid>/
            // stat su un processo caldo, campioni da 8s: **40-60% di un core**,
            // sceso a 0% disattivando solo questo puntino, nient'altro. La causa
            // era la frequenza: `InfiniteTransition` campiona a ogni fotogramma
            // del display (60-120Hz), senza il freno a scatti che il resto
            // dell'app applica sempre alle proprie animazioni perpetue (vedi
            // `steppedFraction` in MainScreen.kt e la spiegazione in
            // specs/home-and-settings/design.md, "How fast the scene steps") —
            // qui mancava, per una semplice svista nello scrivere la prima
            // versione, non per una scelta deliberata di andare senza. Un
            // Box di 8dp che cambia alpha non aveva bisogno di 120 aggiornamenti
            // al secondo per leggersi come "vivo"; [rememberPulseAlpha] più sotto
            // ne fa 10, con lo stesso idioma (`withInfiniteAnimationFrameMillis`
            // + `delay`) già usato altrove.
            // Niente più il contenitore Surface/pillola intorno a questa
            // riga — su richiesta, "per alleggerire": con la card bianca del
            // QR, la pillola codice+copy e le pillole 1m/2m/5m, era il quarto
            // elemento "boxed" di fila. Il puntino pulsante resta: da solo
            // segnala già "questo è live" senza bisogno di uno sfondo tinto
            // a fargli da cornice.
            //
            // `showCountdownLine == false` per il percorso QR: lì "fra
            // quanto"/"per quanto" sono già fusi dentro la frase di
            // [header], sopra al QR (vedi GroupPauseShareHeader) — questa
            // riga separata sarebbe la stessa informazione due volte. Niente
            // puntino pulsante nella versione fusa: dentro una frase intesa
            // funziona peggio che su una riga di stato a sé, e il numero che
            // scende già basta a dire "questo è live".
            if (showCountdownLine) {
                val pulse by rememberPulseAlpha()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .graphicsLayer { alpha = pulse }
                            .background(MaterialTheme.colorScheme.secondary, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(
                            R.string.group_pause_countdown_with_duration,
                            minutes,
                            seconds,
                            durationMinutes,
                        ),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        // A tutta larghezza, ancorato al fondo pagina, 48.dp di altezza —
        // stesso standard applicato al resto dell'app (vedi
        // specs/onboarding-and-password/design.md, "CTA height made
        // explicit"): il default M3 misura 40dp, sotto il target minimo di
        // tocco raccomandato (48dp, Material Design/WCAG 2.5.5).
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp)
                .height(48.dp),
        ) {
            Text(stringResource(android.R.string.cancel))
        }
    }
}
