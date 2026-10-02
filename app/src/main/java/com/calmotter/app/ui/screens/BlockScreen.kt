package com.calmotter.app.ui.screens

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.mutableFloatStateOf
import com.calmotter.app.BlockSessionController
import com.calmotter.app.SlowExitManager
import com.calmotter.app.TogetherActivities
import com.calmotter.app.nfc.GroupPauseHceService
import com.calmotter.app.bluetooth.groupPauseUnlockToken
import androidx.compose.material3.TextButton
import com.calmotter.app.nfc.GroupPauseNfcReader
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Button
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import com.calmotter.app.nfc.NfcTapGuard
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
    // **false quando l'otter lo disegna il chiamante**, sopra la dissolvenza
    // fra Home e questa schermata (vedi [PersistentOtter]): qui lo slot
    // resta riservato ma senza il marchio, e l'anello di avanzamento —
    // che appartiene a questa schermata — continua a starci dentro.
    // true per gli altri due chiamanti (BlockOverlayActivity via
    // AccessibilityService, e MainActivity quando apre già bloccata senza
    // passare dalla Home): non hanno nessuna transizione da cui arrivare e
    // mostrano questa schermata così com'è, otter compreso.
    drawOtter: Boolean = true,
    // Vedi [rememberOtterFloatOffset]: quando si arriva qui dalla Home,
    // l'oscillazione dev'essere la *stessa* istanza, non una nuova con la
    // propria fase — vedi il commento al punto d'uso.
    otterFloatOffset: State<Float>? = null,
) {
    val context = LocalContext.current

    var showUnlockDialog by remember { mutableStateOf(false) }
    // Le decisioni (chi rilascia chi, come finisce la pausa, avanzamento,
    // respiro) stanno in BlockSessionController, provato a parte; qui solo
    // lo stato di ciò che si vede. Creato una volta: legge lo stato di gruppo
    // finché la pausa è in corso, perché endSession() lo azzera.
    val activity = context as? Activity
    val controller = remember {
        BlockSessionController(
            session = sessionManager,
            slowExit = SlowExitManager.getInstance(context),
            nfcAvailable = activity != null && NfcAdapter.getDefaultAdapter(context) != null,
        )
    }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    // Uscita lenta (specs/slow-exit/): conferma, e scadenza dell'attesa in
    // corso (0 = nessuna). La scadenza vive in SessionManager, non qui: deve
    // sopravvivere all'uscita dalla schermata e al riavvio.
    var confirmingSlowExit by remember { mutableStateOf(false) }
    var slowExitDeadline by remember { mutableLongStateOf(controller.slowExitDeadline()) }
    var slowExitProgress by remember { mutableFloatStateOf(0f) }
    var remainingText by remember { mutableStateOf("") }
    // Millisecondi grezzi (non solo il testo già formattato) servono per
    // l'anello di avanzamento condiviso con la Home — vedi [ProgressRing].
    var remainingMillisState by remember { mutableLongStateOf(controller.remainingMillis()) }

    // Pausa respiro (≤ 10 minuti, specs/breathing-pause/): l'anello respira
    // al posto di quello di avanzamento e le parole del respiro prendono il
    // posto della riga del tempo; la frase non c'è. Con le animazioni di
    // sistema disattivate l'anello resta fermo e il testo invita solo a
    // respirare lentamente.
    val breathing = controller.isBreathing
    val reduceMotion = remember { animationsDisabled(context) }
    val breathClock = if (breathing && !reduceMotion) rememberBreathClock() else null
    // derivedStateOf: il testo cambia due volte per ciclo, non a ogni scatto
    // dell'orologio.
    val inhaling by remember(breathClock) {
        derivedStateOf { breathClock?.let { isInhaling(it.value) } }
    }

    // Non null = mostra il passo di rilascio degli altri (vedi
    // [ReleaseOthersStep]) invece della schermata di blocco; la lambda è
    // l'uscita che era stata sospesa.
    var releaseThenExit by remember { mutableStateOf<(() -> Unit)?>(null) }
    fun exitAfterEarlyEnd(offerRelease: Boolean) {
        if (offerRelease) releaseThenExit = onUnlocked else onUnlocked()
    }

    // Chiusura dell'anello a scadenza naturale. Deliberatamente NON usata
    // allo sblocco con password né in `onExpiredImmediately` (sessione già
    // finita prima ancora che la schermata comparisse): nel primo caso
    // l'anello non è al 100% e "compierlo" racconterebbe una cosa non
    // avvenuta, nel secondo non c'è nulla che l'utente stesse guardando.
    var releasingRing by remember { mutableStateOf(false) }
    val ringRelease = remember { Animatable(0f) }

    // Equivalente Compose del CountDownTimer(remainingMillis, 60_000) usato
    // dalla versione XML: aggiorna il tempo rimanente circa una volta al
    // minuto di tempo reale (non sincronizzato con l'inizio della sessione),
    // ma l'ultimo giro attende solo fino alla scadenza.
    // Se la sessione è già scaduta all'apertura dello schermo, nessun tick:
    // si chiude subito senza toast (path 1). Se scade durante il conto alla
    // rovescia, si chiude alla fine del loop (path 2) — il toast, se c'è, lo
    // decide il chiamante tramite onExpiredNaturally.
    LaunchedEffect(Unit) {
        var remaining = controller.remainingMillis()
        if (remaining <= 0) {
            controller.finishNaturally()
            onExpiredImmediately()
            return@LaunchedEffect
        }
        while (remaining > 0) {
            remainingText = CalmCountdown.format(remaining, context)
            remainingMillisState = remaining
            delay(CalmCountdown.nextTickDelayMillis(remaining))
            remaining = controller.remainingMillis()
        }
        controller.finishNaturally()
        // La pausa è arrivata in fondo da sé: l'anello si compie e si
        // scioglie prima di lasciare la schermata (vedi [RingReleaseBurst]).
        // La sessione è già chiusa a questo punto — l'animazione ritarda solo
        // l'uscita dalla schermata, non la fine del blocco.
        releasingRing = true
        ringRelease.animateTo(1f, animationSpec = tween(700, easing = FastOutSlowInEasing))
        // Scadenza naturale: gli altri telefoni hanno lo stesso conto alla
        // rovescia e finiscono da soli, non c'è nessuno da rilasciare.
        onExpiredNaturally()
    }

    // L'host ha sbloccato prima della fine: prima di uscire offre il rilascio
    // a chi è ancora in pausa. Sostituisce la schermata invece
    // di aggiungersi altrove — è il momento esatto in cui serve, e non costa
    // spazio permanente da nessuna parte.
    releaseThenExit?.let { exit ->
        ReleaseOthersStep(
            groupTag = controller.groupTag,
            onDone = exit,
            drawOtter = drawOtter,
            otterFloatOffset = otterFloatOffset,
        )
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
        // 48dp di bottone + 12dp di distacco dal contenuto sopra + 24dp di
        // margine dal fondo vero dello schermo — stessi tre numeri usati
        // dentro [footer] qui sotto, ripetuti qui perché la colonna
        // scorrevole sappia quanto spazio lasciare libero in fondo e non
        // farsi mai coprire dall'overlay.
        footerHeight = 84.dp,
        otter = {
            // Stesso anello di avanzamento + otter fluttuante della Home
            // durante una sessione attiva (vedi PondOtter/ProgressRing in
            // MainScreen.kt), al posto del vecchio badge statico
            // PausePawsMark — stesso linguaggio visivo ovunque una sessione
            // sia in corso, non solo qui.
            val fraction = controller.progress(remainingMillisState)
            if (releasingRing) {
                // L'anello scompare e al suo posto parte la dissolvenza, dallo
                // stesso raggio: i due non convivono, altrimenti si vedrebbero
                // due cerchi concentrici invece di uno che si allenta.
                RingReleaseBurst(progress = ringRelease.value, modifier = Modifier.size(176.dp))
            } else if (slowExitDeadline > 0L) {
                ProgressRing(fraction = slowExitProgress, modifier = Modifier.size(182.dp))
            } else if (breathing) {
                BreathingRing(
                    fullness = { breathClock?.let { breathFullness(it.value) } ?: 0.5f },
                    modifier = Modifier.size(182.dp),
                )
            } else {
                ProgressRing(fraction = fraction, modifier = Modifier.size(182.dp))
            }

            // Se il chiamante non ne fornisce una, questa schermata avvia la
            // propria oscillazione: è il caso di BlockOverlayActivity, che non
            // arriva da nessuna transizione.
            val floatOffset = otterFloatOffset ?: rememberOtterFloatOffset(periodMillis = 5200)
            // Lettura in fase di disegno (vedi steppedFraction in
            // MainScreen.kt): con `Modifier.offset(y = valore.dp)` il valore
            // si legge in composizione e ogni scatto ricompone la schermata.
            if (drawOtter) {
                Box(modifier = Modifier.graphicsLayer { translationY = floatOffset.value * density }) {
                    OtterZenMark(markSize = OtterMarkSize)
                }
            }
        },
        // Sblocco non è un'app da lanciare, è l'unica azione che chiude la
        // pausa: overlay ancorato al vero fondo del viewport (vedi
        // [OtterAnchoredScreen.footer]), stesso trattamento CTA usato
        // altrove nell'app (Next dell'onboarding, Salva di
        // ChangePasswordScreen) — segnalato: prima stava semplicemente
        // sotto la row di badge nel flusso scorrevole, non ancorato come le
        // altre CTA a fondo pagina.
        //
        // **Nascosto mentre il dialog di sblocco è già aperto** — segnalato
        // ("sembra cliccabile perché sale sopra al dialog"): non è un
        // problema di ordine — il dialog (un `AlertDialog` reale, finestra
        // propria) intercetta comunque ogni tocco sotto di sé — ma di
        // percezione. Il velo di oscuramento del dialog si vede appena su
        // un verde già scuro e saturo come `primary`, mentre sui badge
        // chiari sopra è evidente: il bottone resta l'unico elemento a
        // schermo che non sembra spegnersi, e per questo si legge come
        // ancora toccabile. Il bottone serve solo ad aprire questo stesso
        // dialog, quindi mostrarlo mentre è già aperto era comunque
        // ridondante, non solo fuorviante — `footerHeight` resta invariata
        // (riserva sempre lo stesso spazio, vedi sopra), quindi
        // sparire/ricomparire non sposta nient'altro in pagina.
        footer = {
            if (slowExitDeadline > 0L) {
                OutlinedButton(
                    onClick = {
                        controller.cancelSlowExit()
                        slowExitDeadline = 0L
                        slowExitProgress = 0f
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 24.dp)
                        .height(48.dp),
                ) {
                    Text(stringResource(R.string.slow_exit_stay))
                }
            } else if (!showUnlockDialog) {
                Button(
                    onClick = { showUnlockDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 24.dp)
                        .height(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.unlock),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
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

        val breathingText = if (breathing) {
            when (inhaling) {
                true -> stringResource(R.string.breathing_in)
                false -> stringResource(R.string.breathing_out)
                null -> stringResource(R.string.breathing_slowly)
            }
        } else {
            null
        }
        // Nella pausa respiro la riga del tempo non c'è: in 10 minuti contare
        // il tempo è ciò che il respiro vuole evitare, e il conto tranquillo
        // (arrotondato per difetto ai 5 minuti) diceva "5 minuti" appena
        // iniziata. Al suo posto, con lo stesso stile, le parole del respiro.
        Text(
            text = if (slowExitDeadline > 0L) stringResource(R.string.slow_exit_waiting) else breathingText ?: remainingText,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            fontStyle = FontStyle.Italic,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 28.dp)
        )

        // In una pausa di gruppo con un'attività proposta, al posto della
        // frase c'è l'attività (specs/together-activity/), anche con le frasi
        // spente e anche nella pausa respiro: è una proposta del gruppo, non
        // una frase motivazionale.
        val groupActivity = remember { TogetherActivities.byId(controller.groupActivityId) }
        val shownPhrase = groupActivity?.let { stringResource(it.text) } ?: phraseText.takeIf { !breathing }
        if (shownPhrase != null) {
            Text(
                text = shownPhrase,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                fontSize = 16.sp,
                fontStyle = FontStyle.Italic,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 28.dp)
            )
        }

        // Row di sole app consentite, badge circolare tinto (primary a bassa
        // opacità + contenuto primary — non più un riempimento primary pieno
        // con onPrimary: quel look, ereditato da prima che il resto dell'app
        // passasse al linguaggio "card tinta" di Settings/AllowedApps/
        // History, stonava rispetto a tutto il resto, segnalato
        // direttamente). Le app consentite mostrano le prime 2 lettere del
        // nome al posto di un'icona reale (nessuna icona da caricare/
        // desaturare). Nessuna didascalia sopra la row (rimossa: le iniziali
        // già dicono cosa sono) — vedi AllowedAppLaunchItems.kt per il
        // telefono sempre incluso/il tetto di 3 app configurabili, condiviso
        // da tutti e tre i chiamanti.
        //
        // **Sblocco non è più un badge in questa row** — segnalato: con 3
        // app consentite configurate, telefono + 3 app + sblocco erano 5
        // badge da 64dp l'uno più le spaziature, ~384dp di contenuto — più
        // largo dei ~280-300dp disponibili su un telefono reale stretto
        // (`horizontalPadding = 40.dp` per lato tolti dalla larghezza dello
        // schermo), quindi l'ultimo badge — lo sblocco stesso — finiva
        // tagliato fuori senza alcun modo di raggiungerlo (niente scroll
        // orizzontale, di proposito: vedi la nota storica più sotto).
        // Sbagliava il presupposto, non il codice: "restano sempre entro la
        // riga" era vero solo fino a 2 app, non fino al tetto reale di 3.
        //
        // Tolto dalla row invece di stringere ulteriormente i badge (che
        // avrebbe solo spostato la stessa soglia più in là): sblocco non è
        // un'app da lanciare, è l'unica azione che chiude la pausa — merita
        // il trattamento CTA a piena larghezza già usato altrove nell'app
        // (Next dell'onboarding, Salva di ChangePasswordScreen), non la
        // stessa forma di un tasto rapido per Calendar o Fotocamera.
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
        }
    }

    // Rilascio via NFC: mentre il dialogo di sblocco è aperto il telefono
    // ascolta anche l'NFC, così le due strade — digitare la password o
    // avvicinare il telefono di chi ha convocato la pausa e l'ha già chiusa —
    // soddisfano la stessa affordance, senza un selettore in mezzo. Vince
    // quella che accade prima.

    // Rilascio da parte dell'host (specs/group-pause/): il lettore NFC resta
    // acceso finché il dialogo di sblocco è aperto, così avvicinare il
    // telefono dell'host è un secondo modo di uscire. Le regole (solo chi si
    // è unito, solo per questa pausa) sono in BlockSessionController.
    if (showUnlockDialog && controller.canBeReleasedByNfc) {
        DisposableEffect(Unit) {
            val reader = GroupPauseNfcReader(activity!!)
            reader.start { payload ->
                mainHandler.post {
                    if (controller.releaseByNfc(payload)) onUnlocked()
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
            onNoPassword = if (controller.slowExitAvailable) {
                { confirmingSlowExit = true }
            } else {
                null
            },
            // Il secondo modo va detto, altrimenti resta scopribile solo per
            // caso: il lettore NFC è già attivo mentre questo dialogo è aperto.
            message = if (controller.canBeReleasedByNfc) stringResource(R.string.unlock_or_tap_hint) else null,
            onVerified = {
                showUnlockDialog = false
                exitAfterEarlyEnd(controller.unlockWithPassword())
            },
        )
    }

    if (confirmingSlowExit) {
        val wait = controller.slowExitWaitMinutes
        AlertDialog(
            onDismissRequest = { confirmingSlowExit = false },
            title = { Text(stringResource(R.string.slow_exit_confirm_title)) },
            text = { Text(pluralStringResource(R.plurals.slow_exit_confirm_body, wait, wait)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingSlowExit = false
                    slowExitDeadline = controller.startSlowExit()
                }) { Text(stringResource(R.string.slow_exit_confirm_start)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingSlowExit = false }) {
                    Text(stringResource(R.string.slow_exit_stay))
                }
            },
        )
    }

    // L'attesa dell'uscita lenta: l'anello si riempie sull'attesa, e alla
    // scadenza la pausa finisce come "senza password". L'allarme fa la stessa
    // cosa se questa schermata non c'è; endSession() è idempotente.
    LaunchedEffect(slowExitDeadline) {
        val deadline = slowExitDeadline
        if (deadline <= 0L) return@LaunchedEffect
        while (System.currentTimeMillis() < deadline) {
            slowExitProgress = controller.slowExitProgress(deadline)
            delay(minOf(1_000L, (deadline - System.currentTimeMillis()).coerceAtLeast(1L)))
        }
        exitAfterEarlyEnd(controller.finishSlowExit())
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
private fun ReleaseOthersStep(
    groupTag: Int,
    onDone: () -> Unit,
    // Come per il resto della schermata di blocco: false quando l'otter lo
    // disegna già MainActivity sopra la schermata (l'otter persistente, vedi
    // [PersistentOtter]). Prima questo passo ne disegnava comunque uno suo,
    // in un'altra posizione, e in Home se ne vedevano due — segnalato.
    drawOtter: Boolean,
    otterFloatOffset: State<Float>?,
) {
    DisposableEffect(groupTag) {
        GroupPauseHceService.pendingMarker = groupPauseUnlockToken(groupTag)
        onDispose { GroupPauseHceService.pendingMarker = null }
    }
    // Il tocco dell'altro telefono deve arrivare a Calm Otter e non al
    // selettore di sistema (Samsung) — vedi NfcTapGuard. Senza intercettare
    // i tag: qui l'Activity è MainActivity o BlockOverlayActivity, a cui un
    // Intent in più non va consegnato.
    NfcTapGuard(enabled = true)

    // Stesso contenitore della schermata di blocco: l'otter cade nello stesso
    // punto, quindi quello persistente (o quello disegnato qui) non salta
    // quando questo passo prende il posto del blocco.
    OtterAnchoredScreen(
        horizontalPadding = 40.dp,
        otter = {
            if (drawOtter) {
                val floatOffset = otterFloatOffset ?: rememberOtterFloatOffset(periodMillis = 5200)
                Box(modifier = Modifier.graphicsLayer { translationY = floatOffset.value * density }) {
                    OtterZenMark(markSize = OtterMarkSize)
                }
            }
        },
    ) {
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
