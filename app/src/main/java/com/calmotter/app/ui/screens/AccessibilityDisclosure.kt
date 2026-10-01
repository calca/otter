package com.calmotter.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.calmotter.app.R

/**
 * L'informativa sull'accessibilità, prima di mandare l'utente alle
 * impostazioni di sistema. Google Play la chiede alle app che usano
 * l'AccessibilityService senza essere strumenti di accessibilità: deve stare
 * nell'app, dire quali dati si usano, come, che non vengono raccolti né
 * condivisi, e chiedere un consenso esplicito. Ed è giusto dirlo comunque:
 * è il permesso più delicato che l'app chiede.
 *
 * Restituisce l'azione da usare al posto di [onAccepted]: mostra
 * l'informativa, e solo con "Accetto e continua" esegue [onAccepted].
 */
@Composable
fun rememberAccessibilityDisclosure(onAccepted: () -> Unit): () -> Unit {
    var visible by remember { mutableStateOf(false) }
    if (visible) {
        AlertDialog(
            onDismissRequest = { visible = false },
            title = { Text(stringResource(R.string.a11y_disclosure_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.a11y_disclosure_what))
                    Text(stringResource(R.string.a11y_disclosure_not), modifier = Modifier.padding(top = 10.dp))
                    Text(stringResource(R.string.a11y_disclosure_off), modifier = Modifier.padding(top = 10.dp))
                }
            },
            confirmButton = {
                TextButton(onClick = { visible = false; onAccepted() }) {
                    Text(stringResource(R.string.a11y_disclosure_accept))
                }
            },
            dismissButton = {
                TextButton(onClick = { visible = false }) {
                    Text(stringResource(R.string.a11y_disclosure_decline))
                }
            },
        )
    }
    return remember { { visible = true } }
}
