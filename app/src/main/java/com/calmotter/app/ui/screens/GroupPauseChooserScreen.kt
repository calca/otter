package com.calmotter.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.calmotter.app.R
import com.calmotter.app.ui.mascot.OtterSatelliteMark
import com.calmotter.app.ui.mascot.OtterZenMark
import com.calmotter.app.ui.mascot.SprigMark
import com.calmotter.app.ui.mascot.TogetherMark

/**
 * Primo passo di Tempo Insieme: scegliere se ospitare la pausa o unirsi a
 * una già aperta.
 *
 * **Era un dialogo, ora è una pagina** (redesign, schermata "Time Together
 * Chooser Page" del progetto Stitch 3158702940609906617). Il dialogo
 * funzionava come domanda secca, ma questo non è un "sei sicuro?": è
 * l'ingresso di un percorso a più passi, e da qui in poi ogni passo
 * (lobby, QR, conto alla rovescia) è già una schermata intera. Un dialogo
 * in cima a quella catena faceva iniziare il flusso in un registro e
 * proseguirlo in un altro, e per giunta senza un posto dove spiegare cosa
 * comporta ciascuna delle due strade.
 *
 * Della pagina del mockup **non** viene presa la barra di navigazione in
 * fondo, per la stessa ragione di tutte le altre schermate del redesign:
 * non c'è nessun altro posto dove portare.
 */
@Composable
fun GroupPauseChooserScreen(
    onCreate: () -> Unit,
    onJoin: () -> Unit,
    onBack: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().calmBackground()) {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            GroupPauseChooserTopBar(onBack = onBack)

            // Il corpo si centra in quello che resta invece di impilarsi
            // sotto la barra. Il mockup riempiva la metà bassa con la barra
            // di navigazione, che qui non c'è: appoggiato in alto, il
            // contenuto lasciava mezzo schermo vuoto sotto di sé.
            //
            // `verticalScroll` insieme ad `Arrangement.Center`: finché ci
            // sta, sta al centro; quando non ci sta più (carattere di
            // sistema ingrandito) scorre invece di farsi tagliare.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                TandemPawsMedallion()

                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.group_pause_entry_button),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.group_pause_chooser_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )

                Spacer(modifier = Modifier.height(28.dp))
                GroupPauseChoiceCard(
                    title = stringResource(R.string.group_pause_chooser_create),
                    role = stringResource(R.string.group_pause_chooser_create_role),
                    description = stringResource(R.string.group_pause_chooser_create_desc),
                    icon = { TogetherMark(markSize = 22.dp) },
                    onClick = onCreate,
                )
                GroupPauseChoiceCard(
                    title = stringResource(R.string.group_pause_chooser_join),
                    role = stringResource(R.string.group_pause_chooser_join_role),
                    description = stringResource(R.string.group_pause_chooser_join_desc),
                    icon = { OtterSatelliteMark(markSize = 22.dp) },
                    onClick = onJoin,
                    modifier = Modifier.padding(top = 12.dp),
                )

                Spacer(modifier = Modifier.height(28.dp))
                GroupPauseChooserFooter()
            }
        }
    }
}

/**
 * Freccia indietro a sinistra, marchio a destra. **Senza titolo**: il
 * mockup lo mette sia qui sia sotto il medaglione, e sarebbe la stessa
 * parola due volte a mezzo dito di distanza. Vale qui la ragione per cui
 * la Home ha perso il suo "Home" — la barra dice dove si torna, non dove
 * si è.
 *
 * Il marchio non è toccabile: come in Home è un segno, non un bottone.
 */
@Composable
private fun GroupPauseChooserTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            OtterZenMark(markSize = 30.dp)
        }
    }
}

/**
 * Le impronte dentro lo stagno fermo, come in Home la lontra.
 *
 * Cinque livelli, presi dal mockup uno per uno invece che a occhio
 * (contenitore 224, `inset-0/3/7/11` più il disco centrale da 96):
 *
 * 1. anello di contorno, il bordo dello stagno;
 * 2. anello **tratteggiato**, appena dentro e leggermente più stretto;
 * 3. disco velato;
 * 4. disco pallido;
 * 5. disco chiaro al centro, su cui stanno le impronte.
 *
 * Non riusa `PondStill` della Home: là lo stagno è a piena pagina e il suo
 * centro è un'invariante condivisa con `BlockScreen` (vedi
 * OtterAnchoredScreen.kt). Ne riusa però **i colori** — `tertiary` per i
 * dischi e `surfaceBright` per quello chiaro — perché siano lo stesso
 * stagno anche se disegnati da due file diversi.
 *
 * I due anelli di contorno invece prendono `primary` a bassa opacità e non
 * `tertiary`: nel mockup sono un mezzo tono, mentre il nostro `tertiary` è
 * già pallido di suo (#d5e0d5 nelle palette verdi) e su questo sfondo un
 * contorno sottile in quella tinta semplicemente non si vedrebbe.
 *
 * **Ferme.** Il mockup fa pulsare l'anello esterno su quattro secondi; qui
 * no. Le onde che si muovono sono della Home e vogliono dire "lo stagno
 * aspetta, tocca l'otter": questa è una schermata dove si sceglie, e
 * un'animazione perpetua accanto a due opzioni tira l'occhio via da quelle.
 * Vale anche il resto del discorso sul costo — vedi
 * specs/home-and-settings/design.md — con l'aggravante che qui non ci
 * sarebbe nemmeno un momento in cui fermarla.
 */
@Composable
private fun TandemPawsMedallion() {
    val primary = MaterialTheme.colorScheme.primary
    val ringColor = MaterialTheme.colorScheme.tertiary
    val brightColor = MaterialTheme.colorScheme.surfaceBright

    Box(
        modifier = Modifier.size(MedallionSize),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val outer = size.minDimension / 2f
            fun r(diameterDp: Float) = outer * (diameterDp / MEDALLION_DIAMETER)

            drawCircle(
                color = primary,
                radius = outer - 0.5.dp.toPx(),
                center = center,
                alpha = 0.18f,
                style = Stroke(width = 1.dp.toPx()),
            )
            drawCircle(
                color = primary,
                radius = r(190f),
                center = center,
                alpha = 0.25f,
                style = Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(4.dp.toPx(), 6.dp.toPx()),
                    ),
                ),
            )
            drawCircle(color = ringColor, radius = r(168f), center = center, alpha = 0.28f)
            drawCircle(color = ringColor, radius = r(136f), center = center, alpha = 0.75f)
            drawCircle(color = brightColor, radius = r(96f), center = center)
        }
        // Stessa misura del disco, non meno: il marchio ha il suo margine
        // dentro — il disegno occupa il 57% del proprio riquadro — quindi a
        // 96 l'inchiostro ne copre poco più della metà, che è la
        // proporzione del mockup. A 44 e poi a 56 le impronte ne coprivano
        // un terzo e ballavano in mezzo al bianco (misurato sullo
        // screenshot, non stimato).
        TogetherMark(markSize = 96.dp)
    }
}

/**
 * Diametro del medaglione, in dp e come numero nudo: il numero serve per
 * ricavare gli anelli interni dalle misure del mockup, che sono espresse
 * sullo stesso diametro.
 */
private const val MEDALLION_DIAMETER = 224f
private val MedallionSize = MEDALLION_DIAMETER.dp

/**
 * Una delle due strade. Rispetto alla riga che stava nel dialogo guadagna
 * l'etichetta del ruolo ("chi ospita" / "chi si unisce"): "Crea" e
 * "Unisciti" dicono cosa fa il tap, il ruolo dice in cosa ci si trova
 * dopo, che è l'informazione che mancava quando qualcuno doveva decidere
 * chi dei due fa cosa.
 */
@Composable
private fun GroupPauseChoiceCard(
    title: String,
    role: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
                content = { icon() },
            )
            Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    RoleBadge(role)
                }
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Text(
                text = "›",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun RoleBadge(role: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
    ) {
        Text(
            text = role,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/**
 * La riga in fondo del mockup. Non è una CTA e non porta da nessuna parte:
 * dice perché esiste questa schermata, alla stessa voce con cui la Home
 * dice "Tocca Otter per iniziare".
 */
@Composable
private fun GroupPauseChooserFooter() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        SprigMark(markSize = 16.dp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.group_pause_chooser_footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            textAlign = TextAlign.Center,
        )
    }
}
