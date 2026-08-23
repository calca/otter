package com.calmotter.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
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

private const val STEP_COUNT = 5
private const val STEP_PERMISSIONS = 2
private const val STEP_PASSWORD = 3

/**
 * Wizard di onboarding a 5 step (pilota #4 della migrazione a Compose).
 * Riproduce esattamente il comportamento della precedente OnboardingActivity
 * XML/View: stessa navigazione, stessa validazione password solo all'uscita
 * dallo step 4, stesso refresh dei permessi (vedi step 3) sia all'ingresso
 * nello step sia al ritorno dalle Impostazioni tramite [resumeSignal] —
 * stesso pattern usato da MainScreen/MainActivity, dato che setContent {}
 * viene invocato una sola volta e Compose non osserva da solo lo stato
 * permessi del sistema operativo.
 */
@Composable
fun OnboardingScreen(
    resumeSignal: Int,
    passwordManager: PasswordManager,
    isAccessibilityServiceEnabled: () -> Boolean,
    isDndAccessGranted: () -> Boolean,
    onGrantAccessibility: () -> Unit,
    onGrantDnd: () -> Unit,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current

    var currentStep by remember { mutableIntStateOf(0) }

    var accessibilityOk by remember { mutableStateOf(false) }
    var dndOk by remember { mutableStateOf(false) }

    // Rieseguito quando si entra nello step 3 (cambio di currentStep) e a
    // ogni onResume() dell'Activity (resumeSignal incrementato lì, vedi
    // OnboardingActivity) — ma solo mentre lo step 3 è quello visibile,
    // esattamente come il vecchio updatePermissionsStatus() chiamato da
    // showStep(2) e da onResume().
    LaunchedEffect(resumeSignal, currentStep) {
        if (currentStep == STEP_PERMISSIONS) {
            accessibilityOk = isAccessibilityServiceEnabled()
            dndOk = isDndAccessGranted()
        }
    }

    var passwordError by remember { mutableStateOf<String?>(null) }
    var password by remember { mutableStateOf("") }
    var passwordConfirm by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
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
                0 -> StepBody(emoji = "🦦", titleRes = R.string.onb1_title, bodyRes = R.string.onb1_body)
                1 -> StepBody(emoji = "🤝", titleRes = R.string.onb2_title, bodyRes = R.string.onb2_body)
                STEP_PERMISSIONS -> {
                    StepBody(
                        emoji = "🔑",
                        titleRes = R.string.onb3_title,
                        bodyRes = R.string.onb3_body,
                        bodyBottomPadding = 24.dp
                    )

                    Button(
                        onClick = onGrantAccessibility,
                        enabled = !accessibilityOk,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Text(stringResource(R.string.onb3_btn_accessibility))
                    }

                    Button(
                        onClick = onGrantDnd,
                        enabled = !dndOk,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.onb3_btn_dnd))
                    }

                    val statusRes = when {
                        accessibilityOk && dndOk -> R.string.onb3_permissions_ok
                        accessibilityOk -> R.string.onb3_missing_dnd
                        dndOk -> R.string.onb3_missing_accessibility
                        else -> R.string.onb3_missing_both
                    }
                    Text(
                        text = stringResource(statusRes),
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                    )
                }
                STEP_PASSWORD -> {
                    StepBody(
                        emoji = "🔒",
                        titleRes = R.string.onb4_title,
                        bodyRes = R.string.onb4_body,
                        bodyBottomPadding = 24.dp
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
                else -> StepBody(emoji = "🌿", titleRes = R.string.onb5_title, bodyRes = R.string.onb5_body)
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

            Button(
                onClick = {
                    // Step 4 (indice 3, password): validazione obbligatoria solo
                    // all'uscita dallo step, non a ogni carattere digitato.
                    if (currentStep == STEP_PASSWORD) {
                        val errorRes = when {
                            password.length < 4 -> R.string.password_too_short
                            password != passwordConfirm -> R.string.passwords_dont_match
                            else -> null
                        }
                        if (errorRes != null) {
                            passwordError = context.getString(errorRes)
                            return@Button
                        }
                        passwordManager.setPassword(password)
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
    emoji: String,
    titleRes: Int,
    bodyRes: Int,
    bodyBottomPadding: Dp = 0.dp,
) {
    Text(
        text = emoji,
        fontSize = 72.sp,
        modifier = Modifier.padding(bottom = 24.dp)
    )
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
