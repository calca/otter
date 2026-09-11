package com.calmotter.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.calmotter.app.PasswordManager
import com.calmotter.app.R
import kotlinx.coroutines.delay

/**
 * Schermata di cambio password (pilota della migrazione a Compose).
 * Riproduce esattamente il comportamento della precedente versione XML/View:
 * stessa validazione, stesso trattamento del rate-limiting (vedi
 * [PasswordManager]/LockoutPolicy), stesse stringhe.
 */
@Composable
fun ChangePasswordScreen(
    passwordManager: PasswordManager,
    onDone: () -> Unit
) {
    val context = LocalContext.current

    var current by remember { mutableStateOf("") }
    var new1 by remember { mutableStateOf("") }
    var new2 by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    // Secondi di lockout rimanenti (null = nessun lockout in corso). Separato
    // dal testo formattato: stringResource() è @Composable e non può essere
    // chiamata dentro il LaunchedEffect che aggiorna il conto alla rovescia.
    var lockoutSecondsRemaining by remember { mutableStateOf<Int?>(null) }
    var isLockedOut by remember { mutableStateOf(passwordManager.isLockedOut()) }

    // Equivalente Compose del CountDownTimer usato dalla versione XML: finché
    // il lockout è attivo, aggiorna il messaggio con il conto alla rovescia
    // una volta al secondo, poi si riabilita da sola.
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

    val displayErrorMessage = lockoutSecondsRemaining?.let {
        stringResource(R.string.password_locked_out, it)
    } ?: errorMessage

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = stringResource(R.string.change_password_info),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        OutlinedTextField(
            value = current,
            onValueChange = { current = it },
            label = { Text(stringResource(R.string.hint_current_password)) },
            enabled = !isLockedOut,
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )

        OutlinedTextField(
            value = new1,
            onValueChange = { new1 = it },
            label = { Text(stringResource(R.string.hint_new_password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )

        OutlinedTextField(
            value = new2,
            onValueChange = { new2 = it },
            label = { Text(stringResource(R.string.hint_confirm_password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        )

        if (displayErrorMessage.isNotBlank()) {
            Text(
                text = displayErrorMessage,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        val wrongCurrentPasswordText = stringResource(R.string.wrong_current_password)
        val passwordTooShortText = stringResource(R.string.password_too_short)
        val passwordsDontMatchText = stringResource(R.string.passwords_dont_match)
        val passwordChangedText = stringResource(R.string.password_changed)

        Button(
            onClick = {
                if (passwordManager.isLockedOut()) {
                    isLockedOut = true
                    return@Button
                }

                // verify() ha effetti collaterali sul rate-limiting: va chiamata
                // una sola volta e il risultato riutilizzato, non richiamata due volte.
                val currentValid = passwordManager.verify(current)

                val error = when {
                    !currentValid -> wrongCurrentPasswordText
                    new1.length < 4 -> passwordTooShortText
                    new1 != new2 -> passwordsDontMatchText
                    else -> null
                }

                if (error != null) {
                    errorMessage = error
                    // Pulisce solo il campo errato per non costringere a riscrivere tutto
                    if (!currentValid) {
                        current = ""
                    } else {
                        new1 = ""
                        new2 = ""
                    }

                    if (passwordManager.isLockedOut()) {
                        isLockedOut = true
                    }
                } else {
                    passwordManager.setPassword(new1)
                    Toast.makeText(context, passwordChangedText, Toast.LENGTH_SHORT).show()
                    onDone()
                }
            },
            enabled = !isLockedOut,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.change_password_confirm))
        }
    }
}
