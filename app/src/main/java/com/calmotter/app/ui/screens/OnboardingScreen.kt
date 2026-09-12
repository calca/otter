package com.calmotter.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calmotter.app.PasswordManager
import com.calmotter.app.R
import com.calmotter.app.ui.mascot.OtterFloatMark
import com.calmotter.app.ui.mascot.PactPawsMark
import com.calmotter.app.ui.mascot.SprigMark

private const val STEP_COUNT = 3
private const val STEP_PASSWORD = 1

/**
 * Wizard di onboarding a 3 step: benvenuto+funzionamento (uniti in un solo
 * step), password, fatto. Ridotto da 5 step — lo step dei permessi è stato
 * rimosso perché ormai ridondante: i permessi si chiedono, con spiegazione,
 * al primo tap sull'otter in Home se ancora mancanti (vedi
 * PermissionExplainerDialog in MainScreen.kt e
 * specs/home-and-settings/requirements.md "Permissions off Home"), non c'è
 * più bisogno di chiederli anche qui. Di conseguenza questo screen non ha
 * più bisogno di un resumeSignal: nessuno stato di sistema osservabile
 * dall'esterno resta da ricalcolare al ritorno da un'altra schermata.
 */
@Composable
fun OnboardingScreen(
    passwordManager: PasswordManager,
    onFinished: () -> Unit,
) {
    var currentStep by remember { mutableIntStateOf(0) }

    var passwordError by remember { mutableStateOf<String?>(null) }
    var partnerName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordConfirm by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().calmBackground().safeDrawingPadding()) {
        StepIndicator(
            stepCount = STEP_COUNT,
            activeIndex = currentStep,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 8.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (currentStep) {
                0 -> StepBody(
                    // Stessa mascotte della Home ("Living Pond", vedi
                    // MainScreen.kt) invece di un'illustrazione dedicata:
                    // la prima cosa che l'utente vede in assoluto è così
                    // già l'otter che ritroverà a ogni apertura dell'app.
                    illustration = { OtterFloatMark(markSize = 120.dp) },
                    titleRes = R.string.onb1_title,
                    bodyRes = R.string.onb1_body
                )
                STEP_PASSWORD -> {
                    StepBody(
                        illustration = { PactPawsMark(markSize = 96.dp) },
                        titleRes = R.string.onb4_title,
                        bodyRes = R.string.onb4_body,
                        bodyBottomPadding = 24.dp
                    )

                    OutlinedTextField(
                        value = partnerName,
                        onValueChange = { partnerName = it },
                        label = { Text(stringResource(R.string.hint_partner_name)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.hint_new_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = passwordConfirm,
                        onValueChange = { passwordConfirm = it },
                        label = { Text(stringResource(R.string.hint_confirm_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )

                    val error = passwordError
                    if (error != null) {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        )
                    }
                }
                else -> StepBody(
                    illustration = { SprigMark(markSize = 96.dp) },
                    titleRes = R.string.onb5_title,
                    bodyRes = R.string.onb5_body
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (currentStep > 0) {
                OutlinedButton(
                    onClick = { currentStep -= 1 },
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Text(stringResource(R.string.onb_back))
                }
            } else {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {}
            }

            val passwordTooShortText = stringResource(R.string.password_too_short)
            val passwordsDontMatchText = stringResource(R.string.passwords_dont_match)

            Button(
                onClick = {
                    // Step password: validazione obbligatoria solo all'uscita
                    // dallo step, non a ogni carattere digitato.
                    if (currentStep == STEP_PASSWORD) {
                        val error = when {
                            password.length < 4 -> passwordTooShortText
                            password != passwordConfirm -> passwordsDontMatchText
                            else -> null
                        }
                        if (error != null) {
                            passwordError = error
                            return@Button
                        }
                        passwordManager.setPassword(password, partnerName)
                        passwordError = null
                    }

                    if (currentStep < STEP_COUNT - 1) {
                        currentStep += 1
                    } else {
                        onFinished()
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    stringResource(
                        if (currentStep == STEP_COUNT - 1) R.string.onb_finish else R.string.onb_next
                    )
                )
            }
        }
    }
}

/**
 * Indicatore a pallini dello step corrente: i drawable esistenti
 * (dot_active/dot_inactive) sono forme piatte statiche (uno <shape> ovale
 * con un solo colore fisso, non uno StateListDrawable con stato isSelected
 * come i theme_dot_* usati da MainScreen), quindi una riproduzione nativa
 * Compose è più semplice dell'interop AndroidView vista lì.
 */
@Composable
private fun StepIndicator(
    stepCount: Int,
    activeIndex: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center
    ) {
        repeat(stepCount) { index ->
            Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                // Stessa coppia di colori semantici usata dal resto delle schermate
                // migrate (titolo/accento = primary, testo secondario = onSurface),
                // al posto dei due colori statici hardcoded @color/pause_accent /
                // @color/pause_on_background_secondary usati dai drawable XML
                // originali — così l'indicatore segue il tema (Sage/Lavender/
                // Terracotta) scelto dall'utente invece di restare sempre verde.
                val color = if (index == activeIndex) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
                Column(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(color)
                ) {}
            }
        }
    }
}

@Composable
private fun StepBody(
    titleRes: Int,
    bodyRes: Int,
    illustration: @Composable () -> Unit,
    bodyBottomPadding: Dp = 0.dp,
) {
    illustration()
    Spacer(modifier = Modifier.height(24.dp))
    Text(
        text = stringResource(titleRes),
        fontSize = 26.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(bottom = 16.dp)
    )
    Text(
        text = stringResource(bodyRes),
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
        fontSize = 16.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = bodyBottomPadding)
    )
}
