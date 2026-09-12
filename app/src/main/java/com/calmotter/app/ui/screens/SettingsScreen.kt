package com.calmotter.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calmotter.app.AppTheme
import com.calmotter.app.PhraseManager
import com.calmotter.app.R

/**
 * Configurazione: stato di accessibilità/DND (spostati qui dalla Home, vedi
 * MainScreen.kt e home-and-settings/requirements.md — non bloccano l'avvio
 * di una pausa in sé, quindi controllarli a ogni apertura dell'app era
 * percepito come fastidioso), app Home (sezione propria, un solo toggle),
 * personalizzazione (palette, in stile card/lista come i permessi), lo
 * stato della password (chi l'ha impostata, cambio password, elenco app
 * consentite), le preferenze non critiche (frasi riflessive, un toggle), e
 * una sezione "Info" con link esterni (repo GitHub, licenza, sviluppatore).
 * Ogni sezione è una card a bassa opacità con righe divise da
 * [HorizontalDivider] — stesso linguaggio visivo in tutta la schermata,
 * niente più pulsanti pieni o un Checkbox isolato.
 *
 * Diversi valori (stato accessibilità/DND/Home) dipendono da stato esterno
 * che Compose non osserva automaticamente: vanno ricalcolati manualmente a
 * ogni onResume() dell'Activity tramite [resumeSignal] (vedi
 * SettingsActivity) — stesso pattern di MainScreen/OnboardingScreen.
 */
@Composable
fun SettingsScreen(
    currentTheme: AppTheme,
    partnerName: String?,
    phraseManager: PhraseManager,
    resumeSignal: Int,
    isAccessibilityServiceEnabled: () -> Boolean,
    isDndAccessGranted: () -> Boolean,
    isDefaultHome: () -> Boolean,
    onGrantAccessibility: () -> Unit,
    onGrantDnd: () -> Unit,
    onSetHome: () -> Unit,
    onPickTheme: (AppTheme) -> Unit,
    onManageApps: () -> Unit,
    onChangePassword: () -> Unit,
    onOpenGitHub: () -> Unit,
    onOpenLicense: () -> Unit,
    onOpenDeveloper: () -> Unit,
) {
    var accessibilityOk by remember { mutableStateOf(false) }
    var dndOk by remember { mutableStateOf(false) }
    var homeOk by remember { mutableStateOf(false) }

    LaunchedEffect(resumeSignal) {
        accessibilityOk = isAccessibilityServiceEnabled()
        dndOk = isDndAccessGranted()
        homeOk = isDefaultHome()
    }

    var phrasesEnabled by remember { mutableStateOf(phraseManager.isEnabled()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        SectionLabel(stringResource(R.string.settings_permissions_label), topPadding = 0.dp)
        PermissionStatusCard(
            accessibilityOk = accessibilityOk,
            dndOk = dndOk,
            onGrantAccessibility = onGrantAccessibility,
            onGrantDnd = onGrantDnd,
        )

        SectionLabel(stringResource(R.string.settings_home_label))
        HomeCard(homeOk = homeOk, onSetHome = onSetHome)

        SectionLabel(stringResource(R.string.settings_theme_label))
        ThemeListCard(currentTheme = currentTheme, onPickTheme = onPickTheme)

        SectionLabel(stringResource(R.string.settings_password_label))
        PasswordCard(
            partnerName = partnerName,
            onChangePassword = onChangePassword,
            onManageApps = onManageApps,
        )

        SectionLabel(stringResource(R.string.phrases_toggle_label))
        PhrasesCard(checked = phrasesEnabled, onCheckedChange = { checked ->
            phrasesEnabled = checked
            phraseManager.setEnabled(checked)
        })

        SectionLabel(stringResource(R.string.settings_info_label))
        InfoCard(
            onOpenGitHub = onOpenGitHub,
            onOpenLicense = onOpenLicense,
            onOpenDeveloper = onOpenDeveloper,
        )
    }
}

/** Etichetta di sezione uniforme — stesso stile per tutte le card sotto. */
@Composable
private fun SectionLabel(text: String, topPadding: Dp = 32.dp) {
    Text(
        text = text,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topPadding, bottom = 8.dp)
    )
}

/**
 * Colori del componente Switch di M3 ristretti agli 8 ruoli personalizzati
 * per palette in CalmOtterTheme.kt (primary/onPrimary/onSurface):
 * SwitchDefaults.colors() di default userebbe surfaceVariant per la track
 * non selezionata, un ruolo NON personalizzato che resta fisso sul
 * viola-grigio di base di Material3 a prescindere dalla palette scelta —
 * la stessa "trappola" già documentata per i mark dell'otter (vedi
 * CLAUDE.md e specs/mascot-marks/design.md).
 */
@Composable
private fun calmSwitchColors(): SwitchColors = SwitchDefaults.colors(
    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
    checkedTrackColor = MaterialTheme.colorScheme.primary,
    checkedBorderColor = MaterialTheme.colorScheme.primary,
    uncheckedThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
    uncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
    uncheckedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
)

/**
 * Card di stato per i due permessi di sistema (accessibilità, Non
 * disturbare) — spostata qui dalla Home perché nessuno dei due blocca
 * l'avvio di una sessione in sé: vengono comunque chiesti (con
 * spiegazione) al tap sull'otter se ancora mancanti, vedi
 * PondScene/PermissionExplainerDialog in MainScreen.kt. Qui sono uno stato
 * sempre consultabile, non un promemoria a ogni apertura dell'app.
 * L'app Home ha una sua card separata sotto (vedi [HomeCard]): non è un
 * permesso di sistema, è solo "consigliata".
 */
@Composable
private fun PermissionStatusCard(
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
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            PermissionStatusRow(
                label = stringResource(R.string.permission_row_dnd),
                done = dndOk,
                actionLabel = stringResource(R.string.permission_action_grant),
                onAction = onGrantDnd,
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
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (done) MaterialTheme.colorScheme.primary else Color.Transparent)
                .border(
                    width = 1.4.dp,
                    color = if (done) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    },
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                Text(
                    text = "✓",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
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
 */
@Composable
private fun HomeCard(homeOk: Boolean, onSetHome: () -> Unit) {
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
            Text(
                text = stringResource(R.string.permission_row_home),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = homeOk,
                onCheckedChange = { onSetHome() },
                colors = calmSwitchColors(),
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
private fun ThemeListCard(currentTheme: AppTheme, onPickTheme: (AppTheme) -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            ThemeListRow(
                label = stringResource(R.string.theme_sage),
                swatchColor = colorResource(R.color.sage_primary),
                selected = currentTheme == AppTheme.SAGE,
                onClick = { onPickTheme(AppTheme.SAGE) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            ThemeListRow(
                label = stringResource(R.string.theme_lavender),
                swatchColor = colorResource(R.color.lavender_primary),
                selected = currentTheme == AppTheme.LAVENDER,
                onClick = { onPickTheme(AppTheme.LAVENDER) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            ThemeListRow(
                label = stringResource(R.string.theme_terracotta),
                swatchColor = colorResource(R.color.terracotta_primary),
                selected = currentTheme == AppTheme.TERRACOTTA,
                onClick = { onPickTheme(AppTheme.TERRACOTTA) },
            )
        }
    }
}

@Composable
private fun ThemeListRow(
    label: String,
    swatchColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(swatchColor)
                .border(
                    width = if (selected) 2.dp else 0.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    shape = CircleShape,
                )
        )
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        )
        if (selected) {
            Text(
                text = stringResource(R.string.settings_theme_selected),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        }
    }
}

/**
 * Stato password: chi l'ha impostata (se fornito durante l'onboarding, vedi
 * PasswordManager.getPartnerName()/OnboardingScreen.kt — facoltativo, quindi
 * la riga non appare affatto se non c'è un nome salvato) più le due azioni
 * protette da password che vivevano prima come pulsanti pieni separati
 * ("Cambia password", "Gestisci app consentite") — raggruppate qui perché
 * concettualmente sono la stessa cosa: azioni sulla sicurezza della sessione,
 * non preferenze.
 */
@Composable
private fun PasswordCard(
    partnerName: String?,
    onChangePassword: () -> Unit,
    onManageApps: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            if (!partnerName.isNullOrEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_password_set_by, partnerName),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            }
            SettingsActionRow(label = stringResource(R.string.change_password), onClick = onChangePassword)
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            SettingsActionRow(label = stringResource(R.string.manage_allowed_apps), onClick = onManageApps)
        }
    }
}

/** Riga d'azione interna all'app (non un link esterno, vedi [InfoLinkRow]): stesso layout, freccia semplice invece della freccia diagonale. */
@Composable
private fun SettingsActionRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "→",
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Frasi riflessive durante la pausa: un solo toggle, stessa card delle altre sezioni invece del vecchio Checkbox isolato. */
@Composable
private fun PhrasesCard(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
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
            Text(
                text = stringResource(R.string.phrases_toggle_label),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = calmSwitchColors(),
            )
        }
    }
}

/**
 * Link esterni (repo GitHub, licenza, sviluppatore) — l'unica sezione di
 * Settings che lascia l'app. Stessa card neutra a bassa opacità delle altre
 * sezioni.
 */
@Composable
private fun InfoCard(
    onOpenGitHub: () -> Unit,
    onOpenLicense: () -> Unit,
    onOpenDeveloper: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            InfoLinkRow(label = stringResource(R.string.settings_info_github), onClick = onOpenGitHub)
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            InfoLinkRow(label = stringResource(R.string.settings_info_license), onClick = onOpenLicense)
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            InfoLinkRow(label = stringResource(R.string.settings_info_developer), onClick = onOpenDeveloper)
        }
    }
}

@Composable
private fun InfoLinkRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "↗",
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
