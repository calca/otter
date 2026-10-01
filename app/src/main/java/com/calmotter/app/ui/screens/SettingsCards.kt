package com.calmotter.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calmotter.app.AppTheme
import com.calmotter.app.R

// Le carte più grandi delle Impostazioni: permessi, app Home e tema.
// Spostate qui da SettingsScreen.kt (spacchettato come già MainScreen.kt):
// codice spostato, non riscritto.

/**
 * Card di stato per i due permessi di sistema (accessibilità, Non
 * disturbare) — spostata qui dalla Home perché nessuno dei due blocca
 * l'avvio di una sessione in sé: vengono comunque chiesti (con
 * spiegazione) al tap sull'otter se ancora mancanti, vedi
 * PondOtter/PermissionExplainerDialog in MainScreen.kt. Qui sono uno stato
 * sempre consultabile, non un promemoria a ogni apertura dell'app.
 * L'app Home ha una sua card separata sotto (vedi [HomeCard]): non è un
 * permesso di sistema, è solo "consigliata".
 */
@Composable
internal fun PermissionStatusCard(
    accessibilityOk: Boolean,
    dndOk: Boolean,
    onGrantAccessibility: () -> Unit,
    onGrantDnd: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            PermissionStatusRow(
                label = stringResource(R.string.permission_row_accessibility),
                done = accessibilityOk,
                actionLabel = stringResource(R.string.permission_action_grant),
                onAction = onGrantAccessibility,
                icon = {
                    EyeGlyph(
                        visible = true,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            PermissionStatusRow(
                label = stringResource(R.string.permission_row_dnd),
                done = dndOk,
                actionLabel = stringResource(R.string.permission_action_grant),
                onAction = onGrantDnd,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
        }
    }
}

@Composable
private fun PermissionStatusRow(
    label: String,
    done: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Nessun cerchio di stato accanto all'icona: due pastiglie tonde
        // sulla stessa riga si guardavano a vicenda senza che la prima
        // aggiungesse nulla — lo stato è già scritto a destra ("Concedi"
        // contro "Fatto"), a parole invece che per forma. Lo spazio che
        // occupava è sparito con lei: **senza [SettingsRowIcon] restava
        // uno Spacer(12dp) in più, che nessun'altra riga della schermata
        // ha** — le icone qui partivano 12dp più a destra di quelle di
        // Home, Password e Pausa. Segnalato ("le icone non sono ben
        // allineate") e verificato sullo schermo: il testo di questa
        // card iniziava a x=330 contro x=258 di tutte le altre.
        SettingsRowIcon { icon() }
        Text(
            text = label,
            color = if (done) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        )
        if (done) {
            Text(
                text = stringResource(R.string.permission_action_done),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        } else {
            Text(
                text = actionLabel,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable(onClick = onAction)
                    .padding(4.dp),
            )
        }
    }
}

/**
 * Sezione dedicata all'app Home, separata dai permessi di sistema sopra:
 * impostare Calm Otter come Home è solo "consigliato", non un requisito per
 * avviare una pausa (vedi app-blocking-and-home-lock/requirements.md), e
 * non è nemmeno un vero permesso — merita quindi una card propria invece di
 * essere una terza riga tra Accessibilità e Non disturbare.
 *
 * Lo Switch non riflette un vero stato on/off comandabile da qui: non
 * esiste un'API per "smettere di essere l'app Home" senza passare dalle
 * Impostazioni di sistema. Tapparlo in entrambe le direzioni richiama
 * [onSetHome] (riapre il selettore Home di Android, da cui si può anche
 * scegliere un'altra app) — checked riflette solo lo stato attuale.
 *
 * Quando [homeOk] è vero, mostra anche una nota: CalmOtter deve restare
 * titolare del ruolo Home di sistema anche quando non c'è una pausa attiva
 * (per poterlo intercettare di nuovo alla pausa successiva), quindi il
 * forward al vecchio launcher (vedi MainActivity.forwardToOriginalLauncher())
 * ne avvia solo l'Activity senza restituirgli il ruolo — quel launcher può
 * quindi mostrare un proprio avviso "impostami come predefinito", che non è
 * un problema di CalmOtter e non è evitabile senza un dialog di sistema ad
 * ogni pausa iniziata/finita (vedi README "Known Limits"). La nota non ha
 * senso finché CalmOtter non è ancora l'app Home: quel comportamento non si
 * verifica ancora.
 */
@Composable
internal fun HomeCard(homeOk: Boolean, onSetHome: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SettingsRowIcon {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(
                    text = stringResource(R.string.permission_row_home),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = homeOk,
                    onCheckedChange = { onSetHome() },
                    colors = settingsSwitchColors(),
                )
            }
        }
        if (homeOk) {
            Text(
                text = stringResource(R.string.settings_home_forward_hint),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, start = 4.dp, end = 4.dp),
            )
        }
    }
}

/**
 * Selettore tema in stile lista, come la card dei permessi: una riga per
 * palette invece dei 3 pallini fianco a fianco di prima (theme_dot_*.xml,
 * rimossi — vedi git history). Ogni riga mostra il colore VERO di quella
 * palette (non "primary" del tema attivo, che varierebbe riga per riga
 * senza senso) tramite colorResource() sulle stesse risorse
 * @color/{sage,lavender,terracotta}_primary di prima — colorResource segue
 * da sola chiaro/scuro grazie a values-night/colors.xml (vedi
 * specs/multi-theme-system/design.md), quindi ogni pallino resta corretto
 * anche in dark mode.
 */
@Composable
internal fun ThemeListCard(currentTheme: AppTheme, onPickTheme: (AppTheme) -> Unit) {
    // Griglia 2x2 invece dell'elenco verticale (redesign): con quattro
    // palette i colori si confrontano guardandoli insieme, non scorrendo
    // quattro righe. Due Row invece di LazyVerticalGrid: quattro celle fisse
    // dentro una pagina già scrollabile, e una griglia pigra annidata in uno
    // scroll verticale è la traduzione sbagliata dello stesso layout (stesso
    // motivo per cui l'elenco sessioni in Cronologia non è una LazyColumn).
    val entries = listOf(
        Triple(AppTheme.SAGE, R.string.theme_sage, R.color.sage_primary),
        Triple(AppTheme.DEEP_FOREST, R.string.theme_deep_forest, R.color.deep_forest_primary),
        Triple(AppTheme.DUSK_SAND, R.string.theme_dusk_sand, R.color.dusk_sand_primary),
        Triple(AppTheme.DAWN_CLAY, R.string.theme_dawn_clay, R.color.dawn_clay_primary),
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        entries.chunked(2).forEach { row ->
            // `IntrinsicSize.Max` sulla Row + `fillMaxHeight()` su ogni
            // cella: le due celle di una riga si stirano alla più alta
            // delle due invece di misurarsi ciascuna per conto proprio.
            // Segnalato in italiano ("il tema non è ben allineato") — "Sabbia
            // al tramonto" va a capo su due righe mentre "Argilla all'alba"
            // no, e senza questo le due celle risultavano di altezza
            // diversa nella stessa riga, col bordo della cella selezionata
            // che finiva più in alto o più in basso del vicino. La riga
            // sopra ("Salvia" / "Foresta profonda") capita a stare su una
            // riga sola in entrambe le lingue, motivo per cui il problema
            // si vedeva solo in fondo alla griglia — ma la correzione non
            // dipende da quale etichetta è più lunga in quale lingua, regge
            // qualunque lunghezza per costruzione.
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.height(IntrinsicSize.Max),
            ) {
                row.forEach { (theme, labelRes, colorRes) ->
                    ThemeGridCell(
                        label = stringResource(labelRes),
                        swatchColor = colorResource(colorRes),
                        selected = currentTheme == theme,
                        onClick = { onPickTheme(theme) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

/**
 * Una cella della griglia dei temi: pastiglia di colore, nome, e un bordo
 * pieno quando è quella attiva. Il contorno, non un segno di spunta: qui la
 * cosa da confrontare è il colore, e una casella evidenziata lo dice senza
 * aggiungere un glifo sopra la tinta.
 */
@Composable
private fun ThemeGridCell(
    label: String,
    swatchColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = if (selected) 0.12f else 0.06f),
        border = if (selected) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(swatchColor)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
            )
        }
    }
}
