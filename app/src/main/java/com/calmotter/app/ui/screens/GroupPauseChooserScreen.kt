package com.calmotter.app.ui.screens

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
 * Le impronte dentro lo stagno, come in Home la lontra: tre cerchi
 * concentrici, il secondo chiaro. Non riusa `PondStill` della Home — là lo
 * stagno è a piena pagina e ancorato a un centro condiviso fra due
 * schermate, qui è un medaglione di 140dp che sta in colonna con il resto.
 */
@Composable
private fun TandemPawsMedallion() {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(140.dp)
            .clip(CircleShape)
            .background(primary.copy(alpha = 0.06f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceBright),
            contentAlignment = Alignment.Center,
            content = { TogetherMark(markSize = 44.dp) },
        )
    }
}

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
