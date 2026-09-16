package com.calmotter.app.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calmotter.app.CalmCountdown
import com.calmotter.app.PasswordManager
import com.calmotter.app.R
import com.calmotter.app.SessionManager
import com.calmotter.app.ui.mascot.OtterFloatMark
import kotlinx.coroutines.delay

/**
 * Un'app in whitelist ([com.calmotter.app.AllowedAppsManager]), o il
 * telefono (sempre presente, vedi [BlockScreen.allowedApps]), risolta al
 * minimo che serve per mostrarla in [BlockScreen]: solo il nome, da cui si
 * derivano le iniziali per il badge (stesso stile del badge di Unlock, vedi
 * più sotto) — nessuna icona reale caricata, niente da desaturare.
 */
data class AllowedAppLaunchItem(val label: String, val packageName: String)

/**
 * Schermata condivisa "sessione bloccata, inserisci la password per sbloccare",
 * usata identicamente da tutti e tre i punti in cui una sessione attiva può
 * essere incontrata: `BlockOverlayActivity` (AccessibilityService, app non
 * consentita aperta), e `MainActivity` sia dall'icona del launcher sia dal
 * tasto Home — deliberatamente unificate (vedi il commento di classe di
 * `MainActivity`) dopo che la versione precedente, aperta dall'icona durante
 * una sessione, non offriva alcun modo di sbloccare da lì. I tre chiamanti
 * restano liberi di differire solo su cosa succede *dopo* l'uscita da questa
 * schermata (sblocco/scadenza) — `onExpiredImmediately`/`onExpiredNaturally`/
 * `onUnlocked` — non su come appare o si comporta mentre è a schermo.
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
    // Applicato all'otter perché [MainActivity] possa dichiararlo elemento
    // condiviso con quello della Home e farlo scivolare da lì invece di
    // sostituirlo di colpo. Default vuoto: gli altri due chiamanti
    // (BlockOverlayActivity via AccessibilityService, e MainActivity quando
    // apre già bloccata senza passare dalla Home) non hanno una schermata di
    // partenza da cui animare, e mostrano questa così com'è.
    otterModifier: Modifier = Modifier,
    // Vedi [rememberOtterFloatOffset]: quando si arriva qui dalla Home,
    // l'oscillazione dev'essere la *stessa* istanza, non una nuova con la
    // propria fase — vedi il commento al punto d'uso.
    otterFloatOffset: Float? = null,
) {
    val context = LocalContext.current

    var showUnlockDialog by remember { mutableStateOf(false) }
    var remainingText by remember { mutableStateOf("") }
    // Millisecondi grezzi (non solo il testo già formattato) servono per
    // l'anello di avanzamento condiviso con la Home — vedi [ProgressRing] in
    // MainScreen.kt. totalMillis non cambia durante la sessione, letto una
    // volta sola: questo Composable viene sempre ricomposto da capo quando si
    // entra in questo stato (vedi MainActivity.enterBlockScreen()).
    val totalMillis = remember { sessionManager.totalMillis() }
    var remainingMillisState by remember { mutableStateOf(sessionManager.remainingMillis()) }

    // Chiusura dell'anello a scadenza naturale. Deliberatamente NON usata
    // allo sblocco con password né in `onExpiredImmediately` (sessione già
    // finita prima ancora che la schermata comparisse): nel primo caso
    // l'anello non è al 100% e "compierlo" racconterebbe una cosa non
    // avvenuta, nel secondo non c'è nulla che l'utente stesse guardando.
    var releasingRing by remember { mutableStateOf(false) }
    val ringRelease = remember { Animatable(0f) }

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
            remainingText = CalmCountdown.format(remaining, context)
            remainingMillisState = remaining
            delay(60_000)
            remaining = sessionManager.remainingMillis()
        }
        sessionManager.endSession(completedNaturally = true)
        // La pausa è arrivata in fondo da sé: l'anello si compie e si
        // scioglie prima di lasciare la schermata (vedi [RingReleaseBurst]).
        // La sessione è già chiusa a questo punto — l'animazione ritarda solo
        // l'uscita dalla schermata, non la fine del blocco.
        releasingRing = true
        ringRelease.animateTo(1f, animationSpec = tween(700, easing = FastOutSlowInEasing))
        onExpiredNaturally()
    }

    val sessionEndedText = stringResource(R.string.session_ended)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .calmBackground()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 40.dp, vertical = 0.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Ancorata in alto, non più centrata verticalmente: l'otter deve
        // cadere nello stesso identico punto di quello della Home, e la
        // centratura lo legava all'altezza del testo sotto (che cambia con
        // la frase riflessiva e con la presenza dell'indicatore di gruppo).
        // Questo Spacer riserva lo spazio che in Home occupano padding e
        // intestazione — vedi [OtterSlotTopInset] in MainScreen.kt.
        Spacer(modifier = Modifier.height(OtterSlotTopInset))

        // Stesso anello di avanzamento + otter fluttuante della Home durante
        // una sessione attiva (vedi PondScene/ProgressRing in MainScreen.kt),
        // al posto del vecchio badge statico PausePawsMark — stesso
        // linguaggio visivo ovunque una sessione sia in corso, non solo qui.
        // Stesso slot ad altezza fissa della Home, per la stessa ragione.
        Box(
            modifier = Modifier.height(OtterSlotHeight).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val fraction = if (totalMillis > 0) {
                (1f - remainingMillisState.toFloat() / totalMillis.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
            if (releasingRing) {
                // L'anello scompare e al suo posto parte la dissolvenza, dallo
                // stesso raggio: i due non convivono, altrimenti si vedrebbero
                // due cerchi concentrici invece di uno che si allenta.
                RingReleaseBurst(progress = ringRelease.value, modifier = Modifier.size(176.dp))
            } else {
                ProgressRing(fraction = fraction, modifier = Modifier.size(176.dp))
            }

            // Se il chiamante non ne fornisce una, questa schermata avvia la
            // propria oscillazione: è il caso di BlockOverlayActivity, che non
            // arriva da nessuna transizione.
            val floatOffset = otterFloatOffset ?: rememberOtterFloatOffset(periodMillis = 5200)
            Box(modifier = Modifier.offset(y = floatOffset.dp)) {
                OtterFloatMark(modifier = otterModifier, markSize = 124.dp)
            }
        }

        Text(
            text = stringResource(R.string.home_active_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )

        // Etichetta generica, non "con altre N persone": senza una lobby
        // live (vedi specs/group-pause/design.md, sezione "Deferred") questo
        // dispositivo non sa davvero quante altre persone si sono unite, solo
        // che questa è nata come pausa di gruppo — dire un numero che non
        // conosciamo davvero sarebbe disonesto, non solo impreciso.
        if (remember { sessionManager.isGroupSession() }) {
            Text(
                text = stringResource(R.string.block_group_indicator),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        Text(
            text = remainingText,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            fontStyle = FontStyle.Italic,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 28.dp)
        )

        if (phraseText != null) {
            Text(
                text = phraseText,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                fontSize = 16.sp,
                fontStyle = FontStyle.Italic,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 28.dp)
            )
        }

        // Un'unica row di azioni, tutte con lo stesso badge circolare tinto
        // (primary a bassa opacità + contenuto primary — non più un riempimento
        // primary pieno con onPrimary: quel look, ereditato da prima che il
        // resto dell'app passasse al linguaggio "card tinta" di Settings/
        // AllowedApps/History, stonava rispetto a tutto il resto, segnalato
        // direttamente). Le app consentite mostrano le prime 2 lettere del
        // nome al posto di un'icona reale (nessuna icona da caricare/
        // desaturare), e lo sblocco — ultimo a destra, non il primo elemento —
        // apre un dialog con il campo password invece di tenerlo sempre
        // visibile in pagina. Nessuna didascalia sopra la row (rimossa: il
        // lucchetto e le iniziali già dicono cosa sono) — resta comunque
        // compatta anche senza app consentite configurate (il badge di
        // sblocco è l'unico elemento sempre presente) — vedi
        // AllowedAppLaunchItems.kt per il telefono sempre incluso/il tetto di
        // 3 app configurabili, condiviso da tutti e tre i chiamanti. Niente
        // scroll orizzontale di proposito: telefono + 3 app + sblocco (5
        // badge) restano sempre entro la riga a questa dimensione, quindi uno
        // scroll nascosto farebbe solo perdere lo sblocco di vista come
        // capitava prima con il tetto a 5 app (7 badge, l'ultimo — lo sblocco
        // stesso — finiva fuori schermo).
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            allowedApps.forEach { app ->
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                        .clickable { onLaunchApp(app.packageName) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = app.label.trim().take(2).uppercase(),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                    .clickable { showUnlockDialog = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = stringResource(R.string.unlock),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }

    if (showUnlockDialog) {
        PasswordVerifyDialog(
            passwordManager = passwordManager,
            title = stringResource(R.string.unlock),
            confirmLabel = stringResource(R.string.unlock),
            onDismiss = { showUnlockDialog = false },
            onVerified = {
                sessionManager.endSession()
                Toast.makeText(context, sessionEndedText, Toast.LENGTH_SHORT).show()
                onUnlocked()
            },
        )
    }
}
