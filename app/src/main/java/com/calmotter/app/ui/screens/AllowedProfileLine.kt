package com.calmotter.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.calmotter.app.AllowedAppsProfile
import com.calmotter.app.R

/**
 * "Consentite: Standard ›" sotto le durate, e il dialogo che sceglie il
 * profilo di app consentite (specs/allowed-app-profiles/). Una sola versione
 * per la Home e per la pagina di una pausa programmata, così si sceglie allo
 * stesso modo ovunque (nella pausa programmata il dialogo si apre da una
 * riga della card, vedi [AllowedProfileDialog]). Il chiamante la mostra solo
 * con più di un profilo.
 */
@Composable
internal fun AllowedProfileLine(
    profiles: List<AllowedAppsProfile>,
    selectedProfileId: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var picking by remember { mutableStateOf(false) }
    val selectedName = profiles.firstOrNull { it.id == selectedProfileId }?.name.orEmpty()
    TextButton(onClick = { picking = true }, modifier = modifier) {
        Text(
            text = stringResource(R.string.home_profile_line, selectedName) + " ›",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }

    if (picking) {
        AllowedProfileDialog(
            profiles = profiles,
            selectedProfileId = selectedProfileId,
            onSelect = { onSelect(it); picking = false },
            onDismiss = { picking = false },
        )
    }
}

/** Il dialogo che sceglie il profilo; da solo per la riga della pagina di una pausa programmata. */
@Composable
internal fun AllowedProfileDialog(
    profiles: List<AllowedAppsProfile>,
    selectedProfileId: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_profile_dialog_title)) },
        text = {
            Column {
                profiles.forEach { profile ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(profile.id) }
                            .padding(vertical = 4.dp),
                    ) {
                        RadioButton(selected = profile.id == selectedProfileId, onClick = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(profile.name)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}
