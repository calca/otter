package com.calmotter.app.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calmotter.app.CalmCountdown
import com.calmotter.app.PasswordManager
import com.calmotter.app.R
import com.calmotter.app.SessionManager
import com.calmotter.app.ui.mascot.PausePawsMark
import kotlinx.coroutines.delay

/**
 * Un'app in whitelist ([com.calmotter.app.AllowedAppsManager]), o il
 * telefono (sempre presente, vedi [BlockScreen.allowedApps]), risolta al
 * minimo che serve per mostrarla in [BlockScreen]: icona (desaturata al
 * disegno, per restare defilata) e nome per l'accessibilità/tap-target,
 * niente altro.
 */
data class AllowedAppLaunchItem(val label: String, val packageName: String, val icon: Bitmap)

/**
 * Schermata condivisa "sessione bloccata, inserisci la password per sbloccare",
 * usata sia da BlockOverlayActivity che da HomeActivity (stesso layout XML
 * precedente, stessa logica). Le due differenze di comportamento fra le due
 * activity (toast sì/no alla scadenza naturale del countdown, e formattazione
 * della frase opzionale) restano fuori da qui: sono decise dal chiamante
 * tramite [onExpiredNaturally] e tramite il parametro già formattato
 * [phraseText].
 */
@Composable
fun BlockScreen(
    sessionManager: SessionManager,
    passwordManager: PasswordManager,
    phraseText: String?,
    onExpiredImmediately: () -> Unit,
    onExpiredNaturally: () -> Unit,
    onUnlocked: () -> Unit,
    allowedApps: List<AllowedAppLaunchItem> = emptyList(),
    onLaunchApp: (String) -> Unit = {},
) {
    val context = LocalContext.current

    var showUnlockDialog by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("") }
    // Secondi di lockout rimanenti (null = nessun lockout in corso). Tenuto
    // separato dal testo formattato: stringResource() è @Composable e non
    // può essere chiamata dentro il LaunchedEffect che aggiorna il conto
    // alla rovescia — solo il numero viene aggiornato lì, la formattazione
    // avviene più sotto nel corpo del Composable.
    var lockoutSecondsRemaining by remember { mutableStateOf<Int?>(null) }
    var remainingText by remember { mutableStateOf("") }
    var isLockedOut by remember { mutableStateOf(passwordManager.isLockedOut()) }

    // Equivalente Compose del CountDownTimer(remainingMillis, 60_000) usato
    // dalla versione XML: aggiorna il tempo rimanente circa una volta al
    // minuto di tempo reale (non sincronizzato con l'inizio della sessione).
    // Se la sessione è già scaduta all'apertura dello schermo, nessun tick:
    // si chiude subito senza toast (path 1). Se scade durante il conto alla
    // rovescia, si chiude alla fine del loop (path 2) — il toast, se c'è, lo
    // decide il chiamante tramite onExpiredNaturally.
    LaunchedEffect(Unit) {
        var remaining = sessionManager.remainingMillis()
        if (remaining <= 0) {
            sessionManager.endSession(completedNaturally = true)
            onExpiredImmediately()
            return@LaunchedEffect
        }
        while (remaining > 0) {
            remainingText = CalmCountdown.format(remaining)
            delay(60_000)
            remaining = sessionManager.remainingMillis()
        }
        sessionManager.endSession(completedNaturally = true)
        onExpiredNaturally()
    }

    // Stesso pattern di ChangePasswordScreen: finché il lockout è attivo,
    // aggiorna il messaggio con il conto alla rovescia una volta al secondo,
    // poi si riabilita da sola.
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

    val sessionEndedText = stringResource(R.string.session_ended)
    val wrongPasswordText = stringResource(R.string.wrong_password)
    val displayStatusText = lockoutSecondsRemaining?.let {
        stringResource(R.string.password_locked_out, it)
    } ?: statusText

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        PausePawsMark(modifier = Modifier.padding(bottom = 16.dp))

        Text(
            text = stringResource(R.string.block_title),
            color = MaterialTheme.colorScheme.primary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = stringResource(R.string.block_message),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        )

        if (phraseText != null) {
            Text(
                text = phraseText,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 17.sp,
                fontStyle = FontStyle.Italic,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            )
        }

        Text(
            text = remainingText,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 20.sp,
            fontStyle = FontStyle.Italic,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 40.dp)
        )

        // Un'unica row di azioni: lo sblocco (badge pieno, per restare
        // l'azione principale e distinguersi dalle icone delle app) apre un
        // dialog con il campo password invece di tenerlo sempre visibile in
        // pagina — la row resta comunque compatta anche senza app consentite
        // configurate (l'icona di sblocco è l'unico elemento sempre
        // presente). Le icone delle app sono desaturate tingendole con
        // primary (BlendMode.Color: prende tonalità/saturazione dal tint,
        // luminosità dall'icona originale — trucco duotone) invece di un
        // grigio neutro, così restano riconoscibili ma defilate e seguono
        // comunque la palette (Sage/Lavender/Terracotta) — vedi
        // specs/app-blocking-and-home-lock/design.md per perché questa row
        // esiste solo quando Calm Otter è l'app Home e per il telefono
        // sempre incluso/il tetto di 5 app configurabili.
        Text(
            // "Sblocca" da sola quando non ci sono app consentite da
            // mostrare (es. BlockOverlayActivity, che non passa mai
            // allowedApps) — la frase combinata parlerebbe di un'app da
            // aprire che qui non esiste.
            text = if (allowedApps.isNotEmpty()) {
                stringResource(R.string.block_actions_label)
            } else {
                stringResource(R.string.unlock)
            },
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            fontSize = 13.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, bottom = 12.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable { showUnlockDialog = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = stringResource(R.string.unlock),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            val allowedAppTint = MaterialTheme.colorScheme.primary
            allowedApps.forEach { app ->
                Image(
                    bitmap = app.icon.asImageBitmap(),
                    contentDescription = app.label,
                    colorFilter = ColorFilter.tint(allowedAppTint, BlendMode.Color),
                    modifier = Modifier
                        .size(40.dp)
                        .clickable { onLaunchApp(app.packageName) }
                )
            }
        }
    }

    if (showUnlockDialog) {
        AlertDialog(
            onDismissRequest = {
                showUnlockDialog = false
                password = ""
                statusText = ""
            },
            title = { Text(stringResource(R.string.unlock)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.hint_unlock_password)) },
                        enabled = !isLockedOut,
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
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
                    enabled = !isLockedOut,
                    onClick = {
                        if (passwordManager.isLockedOut()) {
                            isLockedOut = true
                            return@TextButton
                        }
                        if (passwordManager.verify(password)) {
                            sessionManager.endSession()
                            Toast.makeText(context, sessionEndedText, Toast.LENGTH_SHORT).show()
                            showUnlockDialog = false
                            onUnlocked()
                        } else {
                            password = ""
                            if (passwordManager.isLockedOut()) {
                                isLockedOut = true
                            } else {
                                statusText = wrongPasswordText
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.unlock))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showUnlockDialog = false
                        password = ""
                        statusText = ""
                    }
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}
