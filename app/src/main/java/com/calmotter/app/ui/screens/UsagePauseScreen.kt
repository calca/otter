package com.calmotter.app.ui.screens

import com.calmotter.app.ui.theme.muted
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.calmotter.app.AllowedAppsProfile
import com.calmotter.app.R
import com.calmotter.app.USAGE_AFTER_OPTIONS
import com.calmotter.app.USAGE_DURATION_OPTIONS
import com.calmotter.app.UsagePauseRule

/**
 * Pausa dopo un uso lungo (specs/usage-pause/), sul modello della pagina di
 * una pausa programmata: in cima l'interruttore, poi "quando" (dopo quanto,
 * per quanto) e "come" (app che restano, protezione). Si salva a ogni
 * modifica, come gli interruttori di Impostazioni: non c'è un modulo da
 * confermare, e la password la chiede l'Activity alla modifica che allenta.
 */
@Composable
fun UsagePauseScreen(
    rule: UsagePauseRule,
    profiles: List<AllowedAppsProfile>,
    accessibilityOn: Boolean,
    onChange: (UsagePauseRule) -> Unit,
) {
    var pickingProfile by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = PageMargin, vertical = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.usage_pause_explain),
            color = MaterialTheme.colorScheme.muted(0.75f),
            modifier = Modifier.padding(bottom = 16.dp),
        )
        if (!accessibilityOn) {
            Text(
                text = stringResource(R.string.usage_pause_needs_permissions),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }

        EditorCard {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                SwitchRow(
                    label = stringResource(R.string.usage_pause_title),
                    checked = rule.enabled,
                    onCheckedChange = { onChange(rule.copy(enabled = it)) },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_phone_time),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
            }
        }

        EditorCard(modifier = Modifier.padding(top = 16.dp)) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                OptionStepper(
                    label = stringResource(R.string.usage_pause_after),
                    options = USAGE_AFTER_OPTIONS,
                    value = rule.afterMinutes,
                    lessDescription = stringResource(R.string.usage_pause_after_less),
                    moreDescription = stringResource(R.string.usage_pause_after_more),
                    onChange = { onChange(rule.copy(afterMinutes = it)) },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_phone_time),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
                CardDivider()
                OptionStepper(
                    label = stringResource(R.string.usage_pause_duration),
                    options = USAGE_DURATION_OPTIONS,
                    value = rule.durationMinutes,
                    lessDescription = stringResource(R.string.schedule_duration_shorter),
                    moreDescription = stringResource(R.string.schedule_duration_longer),
                    onChange = { onChange(rule.copy(durationMinutes = it)) },
                )
            }
        }

        EditorCard(modifier = Modifier.padding(top = 16.dp)) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                if (profiles.size > 1) {
                    SettingsActionRow(
                        label = stringResource(R.string.schedule_profile_label),
                        value = profiles.firstOrNull { it.id == rule.profileId }?.name,
                        onClick = { pickingProfile = true },
                        icon = { RowIcon(Icons.AutoMirrored.Filled.List) },
                    )
                    CardDivider()
                }
                SwitchRow(
                    label = stringResource(R.string.schedule_locked_label),
                    hint = stringResource(R.string.usage_pause_locked_hint),
                    checked = rule.locked,
                    onCheckedChange = { onChange(rule.copy(locked = it)) },
                    icon = { RowIcon(Icons.Filled.Lock) },
                )
            }
        }

        Text(
            text = stringResource(R.string.usage_pause_rules),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.muted(0.65f),
            modifier = Modifier.padding(top = 16.dp, start = 4.dp, end = 4.dp),
        )
    }

    if (pickingProfile) {
        AllowedProfileDialog(
            profiles = profiles,
            selectedProfileId = rule.profileId,
            onSelect = { onChange(rule.copy(profileId = it)); pickingProfile = false },
            onDismiss = { pickingProfile = false },
        )
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: @Composable () -> Unit,
    hint: String? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            // Tutta la riga è l'interruttore: TalkBack la legge una volta
            // sola, con nome e stato ("Mostra le frasi, attivato").
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 14.dp),
    ) {
        SettingsRowIcon { icon() }
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, color = MaterialTheme.colorScheme.onSurface)
            if (hint != null) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.muted(0.65f),
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = settingsSwitchColors(),
            // Senza azione propria perderebbe i 48dp e la riga si accorcerebbe.
            modifier = Modifier.minimumInteractiveComponentSize(),
        )
    }
}
