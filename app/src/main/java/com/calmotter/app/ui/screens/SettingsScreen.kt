package com.calmotter.app.ui.screens

import android.view.View
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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.calmotter.app.AppTheme
import com.calmotter.app.PhraseManager
import com.calmotter.app.R

/**
 * Configurazione: stato di accessibilità/DND/Home predefinita (spostati qui
 * dalla Home, vedi MainScreen.kt e home-and-settings/requirements.md — non
 * bloccano l'avvio di una pausa in sé, quindi controllarli a ogni apertura
 * dell'app era percepito come fastidioso), personalizzazione (palette) e le
 * azioni protette da password (cambio password, elenco app consentite), più
 * le preferenze non critiche (frasi riflessive).
 *
 * Diversi valori (stato accessibilità/DND/Home) dipendono da stato esterno
 * che Compose non osserva automaticamente: vanno ricalcolati manualmente a
 * ogni onResume() dell'Activity tramite [resumeSignal] (vedi
 * SettingsActivity) — stesso pattern di MainScreen/OnboardingScreen.
 */
@Composable
fun SettingsScreen(
    currentTheme: AppTheme,
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
        Text(
            text = stringResource(R.string.settings_permissions_label),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )
        PermissionStatusCard(
            accessibilityOk = accessibilityOk,
            dndOk = dndOk,
            homeOk = homeOk,
            onGrantAccessibility = onGrantAccessibility,
            onGrantDnd = onGrantDnd,
            onSetHome = onSetHome,
        )

        Text(
            text = stringResource(R.string.settings_theme_label),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp, bottom = 8.dp)
        )
        ThemePicker(currentTheme = currentTheme, onPickTheme = onPickTheme)

        Button(
            onClick = onChangePassword,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp)
        ) {
            Text(stringResource(R.string.change_password))
        }

        Button(
            onClick = onManageApps,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Text(stringResource(R.string.manage_allowed_apps))
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 24.dp)
        ) {
            Checkbox(
                checked = phrasesEnabled,
                onCheckedChange = { checked ->
                    phrasesEnabled = checked
                    phraseManager.setEnabled(checked)
                }
            )
            Text(
                text = stringResource(R.string.phrases_toggle_label),
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

/**
 * Card di stato per i tre prerequisiti di una pausa "completa" (accessibilità,
 * Non disturbare, app Home) — spostata qui dalla Home perché nessuno dei tre
 * blocca l'avvio di una sessione in sé: i primi due vengono comunque chiesti
 * (con spiegazione) al tap sull'otter se ancora mancanti, vedi
 * PondScene/PermissionExplainerDialog in MainScreen.kt; il terzo è solo
 * "consigliato". Qui sono uno stato sempre consultabile, non un promemoria
 * a ogni apertura dell'app.
 */
@Composable
private fun PermissionStatusCard(
    accessibilityOk: Boolean,
    dndOk: Boolean,
    homeOk: Boolean,
    onGrantAccessibility: () -> Unit,
    onGrantDnd: () -> Unit,
    onSetHome: () -> Unit,
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
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            PermissionStatusRow(
                label = stringResource(R.string.permission_row_home),
                done = homeOk,
                actionLabel = stringResource(R.string.permission_action_set),
                onAction = onSetHome,
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
 * Selettore tema a 3 pallini (Salvia/Lavanda/Terracotta): riusa i drawable
 * StateListDrawable esistenti (theme_dot_*.xml, con anello di selezione)
 * tramite AndroidView, pixel-identici alla versione XML — non c'è un
 * componente Material3 equivalente e riprodurre da zero l'anello di
 * selezione rischierebbe di introdurre differenze visive sottili.
 */
@Composable
private fun ThemePicker(
    currentTheme: AppTheme,
    onPickTheme: (AppTheme) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        ThemeDot(
            drawableRes = R.drawable.theme_dot_sage,
            labelRes = R.string.theme_sage,
            selected = currentTheme == AppTheme.SAGE,
            onClick = { onPickTheme(AppTheme.SAGE) },
            modifier = Modifier.weight(1f)
        )
        ThemeDot(
            drawableRes = R.drawable.theme_dot_lavender,
            labelRes = R.string.theme_lavender,
            selected = currentTheme == AppTheme.LAVENDER,
            onClick = { onPickTheme(AppTheme.LAVENDER) },
            modifier = Modifier.weight(1f)
        )
        ThemeDot(
            drawableRes = R.drawable.theme_dot_terracotta,
            labelRes = R.string.theme_terracotta,
            selected = currentTheme == AppTheme.TERRACOTTA,
            onClick = { onPickTheme(AppTheme.TERRACOTTA) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ThemeDot(
    drawableRes: Int,
    labelRes: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AndroidView(
            factory = { ctx -> View(ctx).apply { setBackgroundResource(drawableRes) } },
            update = { view -> view.isSelected = selected },
            modifier = Modifier.size(36.dp)
        )
        Text(
            text = stringResource(labelRes),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
