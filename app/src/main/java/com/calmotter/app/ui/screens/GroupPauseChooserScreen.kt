package com.calmotter.app.ui.screens

import com.calmotter.app.ui.theme.muted
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.LocalContext
import com.calmotter.app.Share
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
    // Stessa impalcatura di "Avvicina i telefoni": freccia in alto, il resto
    // centrato nello spazio che rimane. Fino al 2026-10-16 il titolo era
    // fissato a metà pagina sotto un medaglione da 224dp, con la lontra in
    // alto a destra, le etichette "Chi ospita / Chi si unisce" e un motto in
    // fondo: le scelte finivano schiacciate in basso (segnalato).
    CalmScreenColumn(
        contentPadding = PaddingValues(horizontal = PageMargin, vertical = 16.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        CenteredInRemainingSpace {
            TandemPawsMedallion()
            Text(
                text = stringResource(R.string.group_pause_entry_button),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 20.dp),
            )
            Text(
                text = stringResource(R.string.group_pause_chooser_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.muted(0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, start = CenteredTextInset, end = CenteredTextInset),
            )
            GroupPauseChoiceCard(
                title = stringResource(R.string.group_pause_chooser_create),
                description = stringResource(R.string.group_pause_chooser_create_desc),
                // 40 e non 28 come la lontra: le impronte occupano circa metà
                // del loro riquadro, e alla stessa misura sembravano piccole.
                icon = { TogetherMark(markSize = 40.dp) },
                onClick = onCreate,
                modifier = Modifier.padding(top = 24.dp),
            )
            GroupPauseChoiceCard(
                title = stringResource(R.string.group_pause_chooser_join),
                description = stringResource(R.string.group_pause_chooser_join_desc),
                icon = { OtterSatelliteMark(markSize = 28.dp) },
                onClick = onJoin,
                modifier = Modifier.padding(top = 12.dp),
            )
            // Per una pausa insieme l'app serve su entrambi i telefoni:
            // l'invito sta qui, dove manca. Dal menu di condivisione di
            // Android, nessuna rete (vedi Share).
            val context = LocalContext.current
            CalmLinkRow(
                text = stringResource(R.string.group_pause_invite_link),
                onClick = { Share.invite(context) },
                modifier = Modifier.padding(top = 20.dp),
            )
        }
    }
}

/**
 * Le impronte dentro lo stagno fermo, disegnate da [OtterRingIllustration]
 * (`CalmBackground.kt`). **Ferme**: qui si sceglie fra due card, e
 * un'animazione perpetua accanto a loro tirerebbe l'occhio via da quelle.
 * 160dp come in "Avvicina i telefoni" in proporzione (era 224dp: mezza
 * pagina); il marchio resta al 43% del diametro, la proporzione del mockup.
 */
@Composable
private fun TandemPawsMedallion() {
    OtterRingIllustration(dashed = true, size = MedallionSize) {
        TogetherMark(markSize = MedallionSize * 0.43f)
    }
}

private val MedallionSize = 160.dp

/**
 * Una delle due strade: titolo e una riga. Le etichette del ruolo ("Chi
 * ospita" / "Chi si unisce") sono state tolte il 2026-10-16: ripetevano
 * "Crea" e "Unisciti" in una pillola tinta dentro una card tinta.
 */
@Composable
private fun GroupPauseChoiceCard(
    title: String,
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
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.muted(0.7f),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Text(
                text = "›",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp).clearAndSetSemantics {},
            )
        }
    }
}
