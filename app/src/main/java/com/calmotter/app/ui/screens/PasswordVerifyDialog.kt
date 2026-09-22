package com.calmotter.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.calmotter.app.PasswordManager
import com.calmotter.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dialog Material3 riusato ovunque un'azione protetta richieda di verificare
 * la password: sblocco di [BlockScreen], apertura di "Gestisci app
 * consentite" da Settings — prima ognuno con la propria implementazione
 * (`BlockScreen` già in Compose, Settings tramite un `AlertDialog.Builder`
 * di sistema con un `EditText`, non Material e visivamente fuori posto nel
 * resto di un'app 100% Compose). Estratto qui perché entrambi facevano
 * esattamente la stessa cosa: campo password, errore inline se sbagliata,
 * countdown di lockout live al secondo — solo Settings lo faceva peggio (un
 * Toast statico con i secondi residui *al momento del tap*, non aggiornato).
 *
 * Tutto lo stato (`password`, l'errore, il lockout) vive qui dentro, non
 * hoisted dal chiamante: dato che questo Composable viene invocato solo
 * dentro un `if (show) { PasswordVerifyDialog(...) }`, Compose distrugge il
 * suo `remember` quando il dialog si chiude — la volta successiva riparte
 * pulito da solo, senza bisogno di resettare i campi a mano come nella
 * versione precedente.
 *
 * Alla verifica riuscita chiama prima [onDismiss] poi [onVerified]: il
 * dialog si chiude sempre da solo, il chiamante decide solo cosa succede
 * dopo (terminare la sessione e fare il forward per BlockScreen, aprire
 * AllowedAppsActivity per Settings) senza doversene preoccupare.
 */
@Composable
fun PasswordVerifyDialog(
    passwordManager: PasswordManager,
    title: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onVerified: () -> Unit,
    message: String? = null,
) {
    var password by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("") }
    var lockoutSecondsRemaining by remember { mutableStateOf<Int?>(null) }
    var isLockedOut by remember { mutableStateOf(passwordManager.isLockedOut()) }
    // TODO.md "3.1": verify() fa girare PBKDF2 a 120k iterazioni — misurato
    // ~167ms sull'emulatore (probabilmente ottimistico rispetto a un
    // device reale/più vecchio, ma comunque nella fascia "centinaia di
    // millisecondi" che il TODO indicava come soglia). Chiamarla diretta
    // dal thread principale, nel momento più delicato del prodotto (chi
    // tiene in mano il telefono di un altro), rischiava di leggersi come
    // "l'app si è bloccata" invece che "sta controllando".
    var isVerifying by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val busy = isLockedOut || isVerifying

    // Finché il lockout è attivo, aggiorna il messaggio con il conto alla
    // rovescia una volta al secondo, poi si riabilita da sola.
    LaunchedEffect(isLockedOut) {
        if (isLockedOut) {
            while (passwordManager.isLockedOut()) {
                lockoutSecondsRemaining = passwordManager.lockoutRemainingSeconds()
                delay(1000)
            }
            isLockedOut = false
            lockoutSecondsRemaining = null
        }
    }

    val wrongPasswordText = stringResource(R.string.wrong_password)
    val displayStatusText = lockoutSecondsRemaining?.let {
        pluralStringResource(R.plurals.password_locked_out, it, it)
    } ?: statusText

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (message != null) {
                    Text(text = message, modifier = Modifier.padding(bottom = 12.dp))
                }
                PasswordOutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = stringResource(R.string.hint_unlock_password),
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )
                if (displayStatusText.isNotBlank()) {
                    Text(
                        text = displayStatusText,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    if (passwordManager.isLockedOut()) {
                        isLockedOut = true
                        return@TextButton
                    }
                    scope.launch {
                        isVerifying = true
                        // PBKDF2 fuori dal thread principale — vedi il
                        // commento su isVerifying qui sopra.
                        val verified = withContext(Dispatchers.Default) {
                            passwordManager.verify(password)
                        }
                        isVerifying = false
                        if (verified) {
                            onDismiss()
                            onVerified()
                        } else {
                            password = ""
                            if (passwordManager.isLockedOut()) {
                                isLockedOut = true
                            } else {
                                statusText = wrongPasswordText
                            }
                        }
                    }
                }
            ) {
                if (isVerifying) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                } else {
                    Text(confirmLabel)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
