package com.calmotter.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
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
    // La misura si prende **qui**, sulla pagina intera, prima che
    // `safeDrawingPadding` tolga le barre di sistema: "metà pagina" è metà
    // dello schermo, non metà di ciò che resta sotto la barra in alto.
    // Misurata dopo, la metà cadeva quattro punti più in basso (54% invece
    // di 50%, letto dalla gerarchia di accessibilità) — di preciso l'altezza
    // di barra di stato più barra, che qui viene infatti sottratta.
    BoxWithConstraints(modifier = Modifier.fillMaxSize().calmBackground()) {
        val halfPage = maxHeight / 2
        val topInset = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            GroupPauseChooserTopBar(onBack = onBack)

            // **Il titolo sta a metà pagina, le impronte a metà di quello
            // che c'è sopra.**
            //
            // Le due cose vanno decise insieme, ed è la ragione per cui qui
            // si misura la pagina invece di lasciare che il contenuto cada
            // dove capita: la metà alta è un riquadro suo, e il medaglione
            // ci sta dentro centrato. Ne viene che il titolo comincia esatto
            // a metà e che le impronte cadono a metà fra la barra e il
            // titolo — due richieste, una sola misura, nessuna delle due che
            // scivola quando cambia l'altra.
            //
            // Le due passate precedenti provavano a ottenerlo dalla sola
            // distribuzione dello spazio: centrare tutto stringeva il
            // contenuto a metà lasciando due fasce vuote; distribuire i
            // quattro blocchi allontanava la testata dalle strade che
            // introduce; distribuirne tre con il primo alto zero centrava sì
            // le impronte, ma lasciava scendere il titolo dove capitava.
            Box(modifier = Modifier.weight(1f)) {
                // Il `coerceAtLeast`: su uno schermo molto corto la metà
                // può cadere più in alto del medaglione stesso. Lì il titolo
                // scende sotto la metà — preferibile a delle impronte
                // tagliate — e il resto scorre.
                val topHalf = (halfPage - topInset - TopBarHeight)
                    .coerceAtLeast(MedallionSize)
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier.height(topHalf),
                        contentAlignment = Alignment.Center,
                        content = { TandemPawsMedallion() },
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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

                        // Subito sotto il sottotitolo: è la frase che le
                        // introduce, e allontanarle da lei le lasciava senza
                        // premessa.
                        GroupPauseChoiceCard(
                            title = stringResource(R.string.group_pause_chooser_create),
                            role = stringResource(R.string.group_pause_chooser_create_role),
                            description = stringResource(R.string.group_pause_chooser_create_desc),
                            icon = { TogetherMark(markSize = 22.dp) },
                            onClick = onCreate,
                            modifier = Modifier.padding(top = 20.dp),
                        )
                        GroupPauseChoiceCard(
                            title = stringResource(R.string.group_pause_chooser_join),
                            role = stringResource(R.string.group_pause_chooser_join_role),
                            description = stringResource(R.string.group_pause_chooser_join_desc),
                            icon = { OtterSatelliteMark(markSize = 22.dp) },
                            onClick = onJoin,
                            modifier = Modifier.padding(top = 12.dp),
                        )

                        // Poco sotto, ma staccata: non è una terza scelta, e
                        // attaccata alle card lo sarebbe sembrata.
                        GroupPauseChooserFooter(modifier = Modifier.padding(top = 20.dp))
                    }
                }
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(TopBarHeight),
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

/** Altezza della barra in cima. Serve anche a chi calcola dove cade metà pagina. */
private val TopBarHeight = 56.dp

/**
 * Le impronte dentro lo stagno fermo, come in Home la lontra.
 *
 * Ora un guscio sottile attorno a [OtterRingIllustration]
 * (`CalmBackground.kt`): questo disegno — cinque livelli, misure prese dal
 * mockup uno per uno (contenitore 224, `inset-0/3/7/11` più il disco
 * centrale da 96) — è quello da cui `OtterRingIllustration` è nata,
 * quando "unificare la grafica" fra questa schermata e la lobby join ha
 * smesso di voler dire "farle sembrare simili" e ha iniziato a voler dire
 * "farle disegnare dalla stessa funzione". Vedi la doc di
 * `OtterRingIllustration` per il dettaglio dei cinque livelli.
 *
 * **Ferme.** Il mockup fa pulsare l'anello esterno su quattro secondi; qui
 * no — `pulsing` resta `false`. Le onde animate (`OtterRingIllustration`
 * ne ha, riusate dalla lobby join per "sto cercando") vogliono dire "lo
 * stagno aspetta, tocca l'otter/il nome giusto": questa è una schermata
 * dove si sceglie fra due card, e un'animazione perpetua accanto a loro
 * tira l'occhio via da quelle. Vale anche il resto del discorso sul costo
 * — vedi specs/home-and-settings/design.md — con l'aggravante che qui non
 * ci sarebbe nemmeno un momento in cui fermarla.
 */
@Composable
private fun TandemPawsMedallion() {
    // dashed = true, sempre: qui non è uno stato "pronto/non pronto" (la
    // Chooser non controlla permessi/Bluetooth), è la scelta decorativa
    // fissa del mockup — vedi la nota su `dashed` in OtterRingIllustration.
    OtterRingIllustration(dashed = true, size = MedallionSize) {
        // Stessa misura del disco, non meno: il marchio ha il suo margine
        // dentro — il disegno occupa il 57% del proprio riquadro — quindi a
        // 96 l'inchiostro ne copre poco più della metà, che è la
        // proporzione del mockup. A 44 e poi a 56 le impronte ne coprivano
        // un terzo e ballavano in mezzo al bianco (misurato sullo
        // screenshot, non stimato).
        TogetherMark(markSize = 96.dp)
    }
}

/** Diametro del medaglione — lo stesso 224dp a cui [OtterRingIllustration] proporziona i suoi cinque livelli. */
private val MedallionSize = 224.dp

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
private fun GroupPauseChooserFooter(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
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
