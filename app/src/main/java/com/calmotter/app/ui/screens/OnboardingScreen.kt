package com.calmotter.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calmotter.app.PasswordManager
import com.calmotter.app.R
import com.calmotter.app.ui.mascot.OtterZenMark
import com.calmotter.app.ui.mascot.PactPawsMark
import com.calmotter.app.ui.mascot.SprigMark
import com.calmotter.app.ui.mascot.TogetherMark

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

    // .imePadding() sull'intera colonna (non solo sull'area scrollabile
    // interna) è ciò che tiene la riga Back/Next sopra la tastiera: senza,
    // sull'unico step con campi di testo (password) la tastiera copriva
    // Back/Next senza ridimensionare il layout, costringendo a chiuderla a
    // mano per proseguire — il "si perde il focus" segnalato.
    Column(modifier = Modifier.fillMaxSize().calmBackground().safeDrawingPadding().imePadding()) {
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
                0 -> {
                    StepBody(
                        // Stessa mascotte della Home ("Living Pond", vedi
                        // MainScreen.kt) invece di un'illustrazione dedicata:
                        // la prima cosa che l'utente vede in assoluto è così
                        // già l'otter che ritroverà a ogni apertura dell'app.
                        illustration = { OtterZenMark(markSize = 120.dp) },
                        titleRes = R.string.onb1_title,
                        bodyRes = R.string.onb1_body,
                        bodyBottomPadding = 16.dp
                    )
                    OnboardingNoteCard(
                        noteRes = R.string.onb1_note,
                        icon = { TogetherMark(markSize = 20.dp) },
                    )
                }
                STEP_PASSWORD -> {
                    StepBody(
                        // L'otter anche qui, non più le zampe del patto:
                        // quelle sono scese nella nota sotto, e la mascotte
                        // dà ai tre passi la stessa faccia invece di tre
                        // illustrazioni diverse.
                        illustration = { OtterZenMark(markSize = 120.dp) },
                        titleRes = R.string.onb4_title,
                        bodyRes = R.string.onb4_body,
                        bodyBottomPadding = 16.dp
                    )
                    OnboardingNoteCard(
                        noteRes = R.string.onb4_note,
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        modifier = Modifier.padding(bottom = 16.dp),
                    )

                    CalmTextField(
                        value = partnerName,
                        onValueChange = { partnerName = it },
                        label = stringResource(R.string.hint_partner_name),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    )

                    PasswordOutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = stringResource(R.string.hint_new_password),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    )

                    PasswordOutlinedTextField(
                        value = passwordConfirm,
                        onValueChange = { passwordConfirm = it },
                        label = stringResource(R.string.hint_confirm_password),
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
                else -> {
                    StepBody(
                        illustration = { OtterZenMark(markSize = 120.dp) },
                        titleRes = R.string.onb5_title,
                        bodyRes = R.string.onb5_body,
                        bodyBottomPadding = 16.dp
                    )
                    OnboardingNoteCard(
                        noteRes = R.string.onb5_note,
                        icon = { SprigMark(markSize = 20.dp) },
                    )
                }
            }
        }

        // A tutta larghezza, una sotto l'altra invece che affiancate, ancorate
        // al fondo pagina (già così: la Column scrollabile sopra ha
        // weight(1f), vedi sopra) — su richiesta esplicita, applicata qui e
        // alla lobby Bluetooth host (vedi GroupPauseBluetoothLobbyHostScreen.kt),
        // non ai dialoghi Material3 con confirmButton/dismissButton, lasciati
        // affiancati. L'ordine è stato invertito su richiesta successiva:
        // **l'azione primaria (Next/Finish) è l'ultima**, non la prima — Back
        // sopra, Next/Finish sotto. Altezza 48.dp esplicita su entrambi: il
        // default M3 (`ButtonDefaults.MinHeight`) è 40.dp, sotto il target
        // minimo di tocco raccomandato (48dp, Material Design/WCAG 2.5.5).
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            // `spacedBy` e non `.padding(top = 8.dp)` sul secondo bottone:
            // applicato *dopo* `.height(48.dp)` nella catena di modifier, il
            // padding veniva "mangiato" dentro l'altezza già fissata invece
            // di aggiungersi sopra — misurato 40dp invece di 48dp
            // sull'emulatore. Lo spazio fra i due va sulla Column, e con un
            // solo bottone (step 0, niente Back) non lascia comunque spazio
            // fantasma perché non ha nulla da distanziare.
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val passwordTooShortText = stringResource(R.string.password_too_short)
            val passwordsDontMatchText = stringResource(R.string.passwords_dont_match)

            if (currentStep > 0) {
                OutlinedButton(
                    onClick = { currentStep -= 1 },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(stringResource(R.string.onb_back))
                }
            }

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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
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
        text = boldAnnotatedString(stringResource(bodyRes)),
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
        fontSize = 16.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = bodyBottomPadding)
    )
}

/**
 * Evidenzia in grassetto il testo racchiuso tra <b> e </b> in una string
 * resource (scritti come &lt;b&gt;/&lt;/b&gt; in strings.xml così che
 * getString() li restituisca come testo letterale invece di scartarli, come
 * farebbe con i tag di styling nativi di Android). Basta questo parsing
 * minimale — non serve un parser HTML completo — per evidenziare una singola
 * frase chiave per step di onboarding.
 */
internal fun boldAnnotatedString(text: String): AnnotatedString {
    val openTag = "<b>"
    val closeTag = "</b>"
    return buildAnnotatedString {
        var index = 0
        while (index < text.length) {
            val openIndex = text.indexOf(openTag, index)
            if (openIndex == -1) {
                append(text.substring(index))
                break
            }
            append(text.substring(index, openIndex))
            val closeIndex = text.indexOf(closeTag, openIndex)
            if (closeIndex == -1) {
                append(text.substring(openIndex + openTag.length))
                break
            }
            pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
            append(text.substring(openIndex + openTag.length, closeIndex))
            pop()
            index = closeIndex + closeTag.length
        }
    }
}


/**
 * La riga che conta di ciascun passo, dentro una card tinta (redesign).
 *
 * Non è testo nuovo: è l'ultima frase del corpo di quel passo, spostata qui.
 * Erano già le frasi in grassetto — il patto, il gesto di fiducia, il
 * prenditi cura di te — cioè quelle che dicono *perché* l'app funziona così;
 * annegate in fondo a un paragrafo si leggevano come una chiusa, staccate si
 * leggono come la premessa che sono.
 */
@Composable
private fun OnboardingNoteCard(
    noteRes: Int,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
                content = { icon() },
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = boldAnnotatedString(stringResource(noteRes)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            )
        }
    }
}
