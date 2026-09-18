package com.calmotter.app.ui.screens

import android.widget.Toast
import com.calmotter.app.nfc.GroupPauseHceService
import com.calmotter.app.bluetooth.groupPauseUnlockToken
import androidx.compose.material3.TextButton
import com.calmotter.app.nfc.GroupPauseNfcReader
import com.calmotter.app.bluetooth.parseGroupPauseUnlockToken
import androidx.compose.runtime.DisposableEffect
import android.os.Looper
import android.os.Handler
import android.nfc.NfcAdapter
import android.app.Activity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calmotter.app.CalmCountdown
import com.calmotter.app.PasswordManager
import com.calmotter.app.R
import com.calmotter.app.SessionManager
import com.calmotter.app.ui.mascot.OtterZenMark
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
    // Stato della pausa condivisa, letto una volta: serve sia per mostrare i
    // nomi sia per decidere se il rilascio via NFC ha senso qui.
    val sessionGroupTag = remember { sessionManager.groupTag() }
    val activity = context as? Activity
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val nfcReleaseEnabled = remember {
        activity != null &&
            sessionManager.isGroupSession() &&
            !sessionManager.isGroupHost() &&
            sessionGroupTag != 0 &&
            NfcAdapter.getDefaultAdapter(context) != null
    }

    // Se questo dispositivo ha convocato la pausa condivisa, alla sua fine
    // può rilasciare chi c'era — vedi [ReleaseOthersStep]. Va letto adesso:
    // endSession() azzera tag e ruolo.
    val canReleaseOthers = remember {
        activity != null &&
            sessionManager.isGroupSession() &&
            sessionManager.isGroupHost() &&
            sessionGroupTag != 0 &&
            sessionManager.companions().isNotEmpty() &&
            NfcAdapter.getDefaultAdapter(context) != null
    }
    // Non null = mostra il passo di rilascio invece della schermata di blocco;
    // la lambda è l'uscita che era stata sospesa.
    var releaseThenExit by remember { mutableStateOf<(() -> Unit)?>(null) }

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
        if (canReleaseOthers) releaseThenExit = onExpiredNaturally else onExpiredNaturally()
    }

    val sessionEndedText = stringResource(R.string.session_ended)

    // La pausa di questo dispositivo è finita, ma era lui a convocarla: prima
    // di uscire offre il rilascio a chi c'era. Sostituisce la schermata invece
    // di aggiungersi altrove — è il momento esatto in cui serve, e non costa
    // spazio permanente da nessuna parte.
    releaseThenExit?.let { exit ->
        ReleaseOthersStep(groupTag = sessionGroupTag, onDone = exit)
        return
    }

    // Stesso contenitore della Home ([OtterAnchoredScreen]), che è ciò che
    // fa cadere questo otter nello stesso identico punto di quello lì: la
    // posizione non è più calcolata qui (prima: uno `Spacer` alto quanto
    // padding + intestazione della Home, tenuto allineato a mano) ma una
    // volta sola, per entrambe le schermate. `headerHeight` resta a zero:
    // un'intestazione questa schermata non ce l'ha.
    OtterAnchoredScreen(
        horizontalPadding = 40.dp,
        otter = {
            // Stesso anello di avanzamento + otter fluttuante della Home
            // durante una sessione attiva (vedi PondOtter/ProgressRing in
            // MainScreen.kt), al posto del vecchio badge statico
            // PausePawsMark — stesso linguaggio visivo ovunque una sessione
            // sia in corso, non solo qui.
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
                ProgressRing(fraction = fraction, modifier = Modifier.size(182.dp))
            }

            // Se il chiamante non ne fornisce una, questa schermata avvia la
            // propria oscillazione: è il caso di BlockOverlayActivity, che non
            // arriva da nessuna transizione.
            val floatOffset = otterFloatOffset ?: rememberOtterFloatOffset(periodMillis = 5200)
            Box(modifier = Modifier.offset(y = floatOffset.dp)) {
                OtterZenMark(modifier = otterModifier, markSize = 88.dp)
            }
        },
    ) {
        // Lo stato come chip, non come riga di testo (redesign). Il mockup
        // ne aveva due, un chip "session in progress" *e* l'etichetta
        // "PAUSED" sotto: dicono la stessa cosa due volte, quindi qui è
        // rimasta solo l'etichetta che c'era già, messa dentro il chip.
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
        ) {
            Text(
                text = stringResource(R.string.home_active_label).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 0.12.em,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }

        // Etichetta generica, non "con altre N persone": senza una lobby
        // live (vedi specs/group-pause/design.md, sezione "Deferred") questo
        // dispositivo non sa davvero quante altre persone si sono unite, solo
        // che questa è nata come pausa di gruppo — dire un numero che non
        // conosciamo davvero sarebbe disonesto, non solo impreciso.
        if (remember { sessionManager.isGroupSession() }) {
            // Con chi, quando lo si sa. I nomi arrivano dalla lobby dal vivo e
            // ora sopravvivono all'avvio della sessione: prima il lavoro fatto
            // per accoppiare due telefoni non lasciava alcuna traccia da qui
            // in poi, e la pausa era indistinguibile da una in solitaria a
            // parte una riga generica. Quella riga resta il ripiego per il
            // percorso QR/codice, che non ha modo di conoscere un nome.
            val companions = remember { sessionManager.companions() }
            Text(
                text = when (companions.size) {
                    0 -> stringResource(R.string.block_group_indicator)
                    1 -> stringResource(R.string.block_group_with_one, companions[0])
                    // plurals e non una stringa con %d: "e altre 1 persone"
                    // / "and 1 others" è sgrammaticato in entrambe le lingue,
                    // ed è esattamente il caso più frequente dopo quello a uno.
                    else -> pluralStringResource(
                        R.plurals.block_group_with_many,
                        companions.size - 1,
                        companions[0],
                        companions.size - 1,
                    )
                },
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
                BlockActionBadge(
                    label = app.label,
                    onClick = { onLaunchApp(app.packageName) },
                ) {
                    Text(
                        text = app.label.trim().take(2).uppercase(),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            BlockActionBadge(
                label = stringResource(R.string.unlock),
                onClick = { showUnlockDialog = true },
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }

    // Rilascio via NFC: mentre il dialogo di sblocco è aperto il telefono
    // ascolta anche l'NFC, così le due strade — digitare la password o
    // avvicinare il telefono di chi ha convocato la pausa e l'ha già chiusa —
    // soddisfano la stessa affordance, senza un selettore in mezzo. Vince
    // quella che accade prima.
    //
    // Solo in una pausa di gruppo e solo se questo dispositivo NON è l'host:
    // l'host non ha nessuno da cui farsi rilasciare. Il confronto sul
    // groupTag evita di liberare chi stava facendo un'altra pausa; è la
    // vicinanza fisica a fare da autorizzazione, non il tag — vedi
    // groupPauseUnlockToken.
    if (showUnlockDialog && nfcReleaseEnabled) {
        DisposableEffect(Unit) {
            val reader = GroupPauseNfcReader(activity!!)
            reader.start { payload ->
                if (parseGroupPauseUnlockToken(payload) == sessionGroupTag) {
                    mainHandler.post {
                        sessionManager.endSession()
                        Toast.makeText(context, sessionEndedText, Toast.LENGTH_SHORT).show()
                        onUnlocked()
                    }
                }
            }
            onDispose { reader.stop() }
        }
    }

    if (showUnlockDialog) {
        PasswordVerifyDialog(
            passwordManager = passwordManager,
            title = stringResource(R.string.unlock),
            confirmLabel = stringResource(R.string.unlock),
            onDismiss = { showUnlockDialog = false },
            // Il secondo modo va detto, altrimenti resta scopribile solo per
            // caso: il lettore NFC è già attivo mentre questo dialogo è aperto.
            message = if (nfcReleaseEnabled) stringResource(R.string.unlock_or_tap_hint) else null,
            onVerified = {
                sessionManager.endSession()
                Toast.makeText(context, sessionEndedText, Toast.LENGTH_SHORT).show()
                showUnlockDialog = false
                if (canReleaseOthers) releaseThenExit = onUnlocked else onUnlocked()
            },
        )
    }
}

/**
 * Passo finale per l'host di una pausa condivisa: il telefono espone via NFC
 * il token di rilascio (vedi [groupPauseUnlockToken]) finché questa schermata
 * è a video, così chi era nella stessa pausa può terminare avvicinando il
 * proprio telefono invece di digitare una password che non conosce.
 *
 * **Questo scavalca deliberatamente la password locale dell'altra persona**,
 * ed è una scelta dichiarata, non un effetto collaterale: una pausa convocata
 * insieme può essere chiusa insieme, di presenza. L'autorizzazione è il
 * contatto fisico fra i due telefoni più questo gesto esplicito dell'host —
 * vedi specs/group-pause/design.md.
 */
@Composable
private fun ReleaseOthersStep(groupTag: Int, onDone: () -> Unit) {
    DisposableEffect(groupTag) {
        GroupPauseHceService.pendingMarker = groupPauseUnlockToken(groupTag)
        onDispose { GroupPauseHceService.pendingMarker = null }
    }

    CalmScreenColumn(contentPadding = PaddingValues(40.dp)) {
        OtterZenMark(markSize = 96.dp)
        Text(
            text = stringResource(R.string.release_others_title),
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
        )
        Text(
            text = stringResource(R.string.release_others_body),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onDone, modifier = Modifier.padding(top = 24.dp)) {
            Text(stringResource(R.string.release_others_done))
        }
    }
}


/**
 * Una pastiglia della riga di azioni della pausa, con la propria didascalia
 * sotto (redesign).
 *
 * Le due lettere di un'app — "PH" per Telefono — non dicono niente finché
 * non le si è già capite una volta, e l'icona del lucchetto da sola non
 * distingue "sblocca la pausa" da "blocca qualcosa". Il nome sotto costa una
 * riga di testo piccolo e toglie l'indovinello.
 *
 * Non contraddice la didascalia *sopra* la riga, rimossa tempo fa: quella
 * era un titolo per il gruppo ("Puoi ancora usare…"), questa nomina il
 * singolo elemento.
 *
 * `contentDescription` sta sul contenitore, non sull'icona: per TalkBack la
 * pastiglia è un elemento solo, e ripetere l'etichetta due volte la farebbe
 * annunciare due volte.
 */
@Composable
private fun BlockActionBadge(
    label: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(64.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                .clickable(onClickLabel = label, onClick = onClick)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
            content = { content() },
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
