package com.calmotter.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calmotter.app.BuildConfig
import com.calmotter.app.R
import com.calmotter.app.SessionHistoryManager
import com.calmotter.app.SessionManager
import com.calmotter.app.SessionRecord
import com.calmotter.app.SessionStreak
import com.calmotter.app.ui.mascot.OtterFloatMark
import com.calmotter.app.ui.mascot.OtterZenMark
import com.calmotter.app.ui.mascot.SprigMark
import com.calmotter.app.ui.mascot.TogetherMark
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.pow

// Indice 1 = 30 min, indice 2 = 60 min, ... fino a 4 ore, a passi di 30 minuti
// (stessa tabella usata da MainActivity prima della migrazione a Compose;
// ora mostrata come riga di chip invece che come NumberPicker a rotellina,
// vedi DurationChipRow).
/**
 * Larghezza del bottone "Tempo insieme", come frazione della pagina.
 *
 * Nasce per tenerlo allineato al riepilogo sessioni, che era una pastiglia
 * come lui. Ora quello è un link di testo e la pastiglia è rimasta una
 * sola, ma la misura resta fissa e non legata al testo: "Tempo insieme" si
 * traduce, e un bottone che cambia larghezza con la lingua sposterebbe il
 * baricentro della colonna sotto lo stagno.
 */
private const val HOME_PILL_WIDTH_FRACTION = 0.72f

private val DURATION_LABELS = arrayOf(
    "30 min", "1 h", "1 h 30", "2 h", "2 h 30", "3 h", "3 h 30", "4 h"
)

/**
 * Durata proposta di default, in minuti — 1h e non la più breve (30 min):
 * segnalato esplicitamente. Unica fonte di verità: [MainActivity] la usa
 * per l'indice iniziale di [DURATION_LABELS] (`/ 30`, ricadendo sempre su
 * un'opzione esistente), e [GroupPauseHostActivity]/[GroupPauseChooserActivity]
 * la usano come ripiego se l'extra della durata manca del tutto — un caso
 * che non dovrebbe mai capitare (la passano sempre), ma se capitasse deve
 * ricadere sullo stesso default che vede chi apre l'app, non su un numero
 * indipendente da tenere allineato a mano.
 */
internal const val DEFAULT_SESSION_DURATION_MINUTES = 60

/**
 * Altezza della striscia che contiene l'intestazione della Home: padding
 * superiore (24dp) + intestazione (48dp, che è l'altezza dell'IconButton
 * delle impostazioni, non quella del testo) + padding sotto (24dp).
 *
 * È **fissa** di proposito. Passata a [OtterAnchoredScreen] come
 * `headerHeight`, viene sottratta dallo spazio sopra l'otter, così
 * l'intestazione — che [BlockScreen] non ha — non sposta di un pixel ciò che
 * viene dopo. Se potesse crescere (carattere di sistema ingrandito)
 * spingerebbe giù l'otter nella sola Home, cioè il bug che quel contenitore
 * esiste per rendere impossibile.
 *
 * Prima questo stesso valore era l'offset *dall'alto* a cui entrambe le
 * schermate mettevano l'otter, con [BlockScreen] che riservava lo stesso
 * spazio tramite uno `Spacer` messo lì a mano. Ora la posizione la decide il
 * contenitore condiviso e le due schermate non hanno più modo di divergere:
 * vedi [OtterAnchoredScreen].
 */
internal val HomeHeaderHeight = 96.dp

/**
 * Altezza fissa dello slot che contiene otter e anello, identica nelle due
 * schermate perché [OtterAnchoredScreen] lo disegna per entrambe.
 *
 * Fissa e non `weight(1f)`: la Home centrava l'otter nello spazio che
 * avanzava, e quello spazio si restringeva al crescere del contenuto sotto.
 * Misurato su un 1080×2220, centro dell'otter in Home: y=812 a cronologia
 * vuota, y=760 con qualche sessione — 52px di differenza fra due stati della
 * *stessa* schermata, mentre [BlockScreen] lo disegnava in un punto suo.
 * Nessuna posizione statica poteva coincidere con entrambe, e all'avvio della
 * pausa l'elemento condiviso animava la differenza: l'otter scivolava. Non
 * era colpa della transizione, ma del fatto che le due schermate lo mettevano
 * in punti diversi.
 *
 * Con uno slot di altezza fissa, centrato nel viewport dal contenitore
 * condiviso, non c'è più nulla da interpolare. Effetto collaterale voluto:
 * anche la Home da sola smette di riassestarsi quando registri la prima
 * sessione.
 *
 * Da 272dp a 330dp con l'arrivo dello stagno a tre dischi: il disco esterno
 * ha un raggio di 153dp e senza margine toccherebbe il testo sotto, quindi in 272dp non ci stava e finiva sotto il testo
 * "Tocca l'otter per iniziare" e sulle pillole. Resta comunque un valore
 * fisso e condiviso: è l'altezza *riservata*, non una misura del contenuto.
 */
internal val OtterSlotHeight = 400.dp

/**
 * Schermata home ("Living Pond" — vedi specs/home-and-settings): lo stagno
 * con l'otter è l'unico pulsante di avvio (si tocca l'otter stesso), un
 * anello colorato attorno a lei mostra l'avanzamento mentre una sessione è
 * attiva, e sotto una riga discreta ([SessionsSummaryLink]) riassume
 * streak/settimana ed è anche l'ingresso alla Cronologia. Tema, cambio password, gestione app consentite, frasi
 * riflessive e lo stato di accessibilità/DND/Home sono su SettingsScreen
 * (icona ingranaggio): sono azioni/controlli occasionali, non quelli
 * compiuti ogni volta che si apre l'app (vedi
 * specs/home-and-settings/requirements.md).
 *
 * L'otter è sempre toccabile quando non c'è una sessione attiva: se
 * accessibilità e Non disturbare sono già concessi avvia la pausa, altrimenti
 * apre [PermissionExplainerDialog] — il controllo dei permessi avviene solo
 * nel momento in cui servono davvero, non come un banner permanente.
 *
 * Diversi valori (stato accessibilità/DND, sessione attiva, streak)
 * dipendono da stato esterno che Compose non osserva automaticamente:
 * vanno ricalcolati manualmente a ogni onResume() dell'Activity tramite
 * [resumeSignal] (vedi MainActivity).
 */
@Composable
fun MainScreen(
    resumeSignal: Int,
    sessionManager: SessionManager,
    sessionHistoryManager: SessionHistoryManager,
    isAccessibilityServiceEnabled: () -> Boolean,
    isDndAccessGranted: () -> Boolean,
    onGrantAccessibility: () -> Unit,
    onGrantDnd: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    // Riceve la durata scelta qui in Home (vedi selectedDurationIndex sotto),
    // in minuti: il flow di creazione la usa come valore iniziale del proprio
    // selettore, invece di ripartire sempre da un default indipendente — vedi
    // GroupPauseBluetoothLobbyHostScreen.
    onGroupPause: (durationMinutes: Int) -> Unit = {},
    // **false quando l'otter lo disegna il chiamante**, sopra la dissolvenza
    // fra questa schermata e quella di blocco (vedi [PersistentOtter]): qui
    // lo slot resta riservato ma vuoto, così il contenuto attorno si dispone
    // come sempre. true solo per usi isolati di questa schermata.
    drawOtter: Boolean = true,
    // Vedi [rememberOtterFloatOffset]: passato dall'esterno quando la stessa
    // oscillazione deve proseguire anche in [BlockScreen], null quando questa
    // schermata è sola e può gestirsela da sé.
    otterFloatOffset: State<Float>? = null,
    // Issati in [MainActivity][com.calmotter.app.MainActivity] perché servono
    // anche all'otter persistente, che vive fuori di qui: la durata scelta è
    // ciò che il tocco avvia, il dialogo dei permessi è ciò che il tocco
    // mostra quando non può avviare nulla.
    selectedDurationIndex: Int,
    onSelectDuration: (Int) -> Unit,
    // Cosa fa il tocco sull'otter. Sta qui come parametro e non come corpo
    // perché l'otter ora vive fuori da questa schermata: quando è usata da
    // sola (`drawOtter = true`) il marchio dentro lo slot chiama comunque
    // questo.
    onOtterTap: () -> Unit = {},
    showPermissionDialog: Boolean,
    onDismissPermissionDialog: () -> Unit,
) {
    val context = LocalContext.current

    var accessibilityOk by remember { mutableStateOf(false) }
    var dndOk by remember { mutableStateOf(false) }
    var sessionActive by remember { mutableStateOf(false) }
    var remainingMillis by remember { mutableLongStateOf(0L) }
    var totalMillis by remember { mutableLongStateOf(0L) }
    var streakDays by remember { mutableIntStateOf(0) }
    var weekSummary by remember { mutableStateOf(WeekSummary(0, 0)) }

    fun refreshDerivedState() {
        accessibilityOk = isAccessibilityServiceEnabled()
        dndOk = isDndAccessGranted()
        sessionActive = sessionManager.isSessionActive()
        remainingMillis = sessionManager.remainingMillis()
        totalMillis = sessionManager.totalMillis()
        val history = sessionHistoryManager.getAll()
        streakDays = SessionStreak.currentStreakDays(history)
        weekSummary = weekSummaryOf(history)
    }

    // Rieseguito a ogni onResume() dell'Activity (resumeSignal incrementato
    // lì): equivalente del vecchio refreshUi()/bindMainScreen() chiamato da
    // onResume(), dato che setContent {} viene invocato una sola volta.
    LaunchedEffect(resumeSignal) {
        refreshDerivedState()
    }

    OtterAnchoredScreen(
        horizontalPadding = 24.dp,
        headerHeight = HomeHeaderHeight,
        background = { otterCenterY ->
            // Solo qui, non nella schermata di blocco (che non passa questo
            // slot): le increspature sono l'attesa di avviare una pausa, non
            // qualcosa da mostrare mentre è in corso — vedi anche
            // [AmbientRipples].
            if (!sessionActive) {
                PondStill(centerY = otterCenterY, modifier = Modifier.fillMaxSize())
                AmbientRipples(
                    centerY = otterCenterY,
                    restartKey = resumeSignal,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        },
        header = {
            // Altezza fissa (vedi [HomeHeaderHeight]): questa intestazione non
            // deve poter spingere giù l'otter, che nella schermata di blocco
            // non ce l'ha. Centrata nella striscia, che è quanto basta a
            // ritrovare i 24dp sopra e sotto di prima.
            Row(
                modifier = Modifier.fillMaxWidth().align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Il marchio del redesign accanto al nome: piccolo e non
                // toccabile, è un'insegna, non un bottone — l'unica cosa da
                // toccare qui resta l'otter grande al centro.
                OtterZenMark(markSize = 34.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = stringResource(R.string.settings_title),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        otter = {
            // Quando l'otter lo disegna il chiamante sopra la dissolvenza
            // (vedi [PersistentOtter]), qui resta soltanto l'anello di
            // avanzamento: fa parte di *questa* schermata, compare con lei e
            // sfuma con lei. Il marchio no — quello è uno solo e non si
            // scambia.
            PondOtter(
                restartKey = resumeSignal,
                sessionActive = sessionActive,
                remainingMillis = remainingMillis,
                totalMillis = totalMillis,
                drawMark = drawOtter,
                onStart = onOtterTap,
                otterFloatOffset = otterFloatOffset,
            )
        },
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        if (sessionActive) {
            val remainingMin = (remainingMillis / 60_000L).toInt() + 1
            Text(
                text = stringResource(R.string.home_active_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Text(
                text = stringResource(R.string.home_time_remaining, remainingMin),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 2.dp),
            )
        } else {
            // `bodyMedium` invece di `labelSmall`: è l'unica istruzione della
            // schermata, e a 11sp al 45% si leggeva come una didascalia di
            // servizio. Resta comunque più tenue del resto — non compete con
            // l'otter, la indica.
            Text(
                // Il nome della mascotte in grassetto: è un nome proprio, non
                // "l'otter" generico — stesso parser <b> dell'onboarding.
                text = boldAnnotatedString(stringResource(R.string.home_start_hint)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
            Spacer(modifier = Modifier.height(10.dp))
            DurationChipRow(
                selectedIndex = selectedDurationIndex,
                onSelect = onSelectDuration,
            )

            SessionsSummaryLink(
                streakDays = streakDays,
                weekSummary = weekSummary,
                onHistory = onHistory,
            )

            // Non compete con il tap sull'otter, che resta l'azione
            // primaria (vedi specs/group-pause/design.md) — ma come
            // `TextButton` nudo non si leggeva affatto come qualcosa da
            // toccare ("anche in home non sono chiari"): [CalmSecondaryButton]
            // gli dà un contenitore tinto senza farne un bottone pieno.
            // TogetherMark (le due zampe di PactPawsMark senza il badge
            // circolare) resta l'icona leading, su richiesta precedente
            // ("un po' anonima"): rende il bottone riconoscibile a colpo
            // d'occhio invece di solo testo.
            // 20dp sotto la riga di riepilogo: da quando entrambe hanno un
            // contenitore tinto, senza questo spazio le due pastiglie si
            // sfioravano e si leggevano come un blocco unico invece che
            // come due destinazioni diverse.
            CalmSecondaryButton(
                text = stringResource(R.string.group_pause_entry_button),
                // **Stesso controllo del tap sull'otter, bypass DEBUG
                // compreso.** Qui non c'era affatto: si poteva attraversare
                // tutto Tempo Insieme — accordarsi con qualcuno, la lobby, il
                // conto alla rovescia — e far partire una sessione che poi non
                // bloccava e non silenziava niente, perché nessuna delle due
                // Activity del flusso guarda accessibilità e DND prima di
                // chiamare `startSession`. Il danno peggiore lo prendeva
                // l'altra persona, che invece veniva bloccata davvero.
                //
                // Il controllo sta **qui e non all'avvio della sessione**: a
                // quel punto qualcuno si è già accordato con te e ha già la
                // schermata bloccata. Fermare prima significa fermare mentre
                // la cosa riguarda ancora te soltanto. E ora che entrambe le
                // strade passano da questo bottone (il bivio è una pagina,
                // vedi GroupPauseChooserScreen), un controllo solo le copre
                // tutte e due.
                onClick = {
                    if (BuildConfig.DEBUG || (accessibilityOk && dndOk)) {
                        onGroupPause(selectedDurationIndex * 30)
                    } else {
                        onOtterTap()
                    }
                },
                // 22dp e non 18: le due impronte hanno otto polpastrelli fra
                // loro, e sotto i 20dp si impastavano in una macchia sola.
                leadingIcon = { TogetherMark(markSize = 22.dp) },
                modifier = Modifier.padding(top = 20.dp).fillMaxWidth(HOME_PILL_WIDTH_FRACTION),
            )
        }
    }

    if (showPermissionDialog) {
        PermissionExplainerDialog(
            accessibilityOk = accessibilityOk,
            dndOk = dndOk,
            onGrantAccessibility = {
                onDismissPermissionDialog()
                onGrantAccessibility()
            },
            onGrantDnd = {
                onDismissPermissionDialog()
                onGrantDnd()
            },
            onDismiss = onDismissPermissionDialog,
        )
    }
}

/**
 * Spiega perché servono i permessi ancora mancanti e offre un'azione per
 * concederli — mostrato solo al tap sull'otter quando qualcosa manca
 * ancora, non come banner permanente su Home (vedi
 * specs/home-and-settings/requirements.md). Elenca solo le righe
 * effettivamente mancanti: se uno dei due è già stato concesso da quando il
 * dialog era stato chiuso l'ultima volta, non ricompare qui.
 */
@Composable
private fun PermissionExplainerDialog(
    accessibilityOk: Boolean,
    dndOk: Boolean,
    onGrantAccessibility: () -> Unit,
    onGrantDnd: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
        title = { Text(stringResource(R.string.home_permission_dialog_title)) },
        text = {
            Column {
                if (!accessibilityOk) {
                    PermissionReasonRow(
                        reason = stringResource(R.string.permission_reason_accessibility),
                        actionLabel = stringResource(R.string.permission_action_grant),
                        onGrant = onGrantAccessibility,
                    )
                }
                if (!dndOk) {
                    PermissionReasonRow(
                        reason = stringResource(R.string.permission_reason_dnd),
                        actionLabel = stringResource(R.string.permission_action_grant),
                        onGrant = onGrantDnd,
                    )
                }
            }
        },
    )
}

@Composable
private fun PermissionReasonRow(reason: String, actionLabel: String, onGrant: () -> Unit) {
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Text(
            text = reason,
            color = MaterialTheme.colorScheme.onSurface,
        )
        TextButton(onClick = onGrant, contentPadding = PaddingValues(vertical = 4.dp)) {
            Text(actionLabel)
        }
    }
}

/**
 * Lo "stagno": increspature ambientali a riposo (puramente decorative, si
 * calmano appena parte una sessione) o anello di avanzamento funzionale
 * durante la pausa, con l'otter — il pulsante di avvio — sempre al centro.
 * L'otter è sempre toccabile quando non c'è una sessione attiva: [onStart]
 * (in MainScreen) decide se questo significhi avviare la pausa o spiegare
 * quali permessi mancano ancora.
 *
 * Il tap che avvia la pausa ha un'animazione di conferma propria (richiesta
 * esplicita — prima [onStart] scattava all'istante, un salto secco verso
 * BlockScreen senza alcun feedback sul tap stesso): [isStarting] blocca
 * ulteriori tap, fa "saltare" l'otter con una molla rimbalzante
 * ([otterScale]) e fa partire un impulso che si espande e sfuma
 * ([TapConfirmBurst], più marcato delle [AmbientRipples] continue di
 * sfondo) — solo al termine di quell'animazione [onStart] viene invocata
 * davvero, quindi lo scambio con BlockScreen avviene a gesto già "visto",
 * non a scapito della reattività (la sessione parte comunque in meno di
 * mezzo secondo).
 */
@Composable
private fun PondOtter(
    sessionActive: Boolean,
    remainingMillis: Long,
    totalMillis: Long,
    onStart: () -> Unit,
    otterFloatOffset: State<Float>? = null,
    // false quando il marchio lo disegna [PersistentOtter] sopra la
    // dissolvenza: qui resta solo l'anello, che appartiene a questa
    // schermata e deve sfumare con lei.
    drawMark: Boolean = true,
    // Cambia a ogni onResume: fa ripartire l'oscillazione quando si torna
    // sulla schermata, vedi [rememberOtterFloatOffset].
    restartKey: Int = 0,
) {
    var isStarting by remember { mutableStateOf(false) }
    val otterScale by animateFloatAsState(
        targetValue = if (isStarting) 1.18f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "otterTapScale",
    )
    val burstProgress = remember { Animatable(0f) }
    LaunchedEffect(isStarting) {
        if (isStarting) {
            burstProgress.snapTo(0f)
            burstProgress.animateTo(1f, animationSpec = tween(380, easing = FastOutSlowInEasing))
            onStart()
            // Se onStart() ha davvero avviato la sessione, questo Composable
            // sta per uscire di scena (MainActivity passa a BlockScreen) e il
            // reset è ininfluente. Se invece mancano i permessi, onStart()
            // si limita a mostrare il dialogo esplicativo: questa schermata
            // resta a video, e senza il reset l'otter restava permanentemente
            // non toccabile (clickable è enabled solo quando !isStarting) —
            // bug reale: tap sull'otter senza permessi concessi, poi più
            // nessun tap ha effetto, nemmeno chiudendo il dialogo.
            isStarting = false
        }
    }

    Box(
        modifier = Modifier.size(260.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (!sessionActive) {
            // Le increspature vere e proprie sono ora uno sfondo a piena
            // pagina passato a [OtterAnchoredScreen] (vedi il parametro
            // `background` nella chiamata in [MainScreen]) invece che un
            // figlio di questo Box: qui restava solo [TapConfirmBurst].
            if (isStarting) {
                TapConfirmBurst(progress = burstProgress.value, modifier = Modifier.matchParentSize())
            }
        } else {
            val fraction = if (totalMillis > 0) {
                (1f - remainingMillis.toFloat() / totalMillis.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
            ProgressRing(fraction = fraction, modifier = Modifier.size(182.dp))
        }

        if (drawMark) {
            val floatOffset = otterFloatOffset
                ?: rememberOtterFloatOffset(
                    periodMillis = if (sessionActive) 5200 else 3200,
                    restartKey = restartKey,
                )

            // TODO.md "4.2": l'otter è il tasto di avvio principale
            // dell'app, ma OtterZenMark è disegnato con Canvas — senza
            // un'etichetta esplicita TalkBack lo annuncia come un
            // "Pulsante" muto, senza dire cosa fa. La stessa frase già
            // visibile sotto ("Tap Otter to start", home_start_hint) fa
            // anche da contentDescription — non serve inventarne una
            // seconda. Solo quando è davvero toccabile: durante una pausa
            // (sessionActive) l'otter non è un pulsante, non deve leggersi
            // come tale.
            val otterContentDescription = if (!sessionActive && !isStarting) {
                // .text, non la stringa grezza — vedi lo stesso commento in
                // PersistentOtter.kt.
                boldAnnotatedString(stringResource(R.string.home_start_hint)).text
            } else {
                null
            }
            Box(
                modifier = Modifier
                    .graphicsLayer { translationY = floatOffset.value * density }
                    .scale(otterScale)
                    .clip(CircleShape)
                    .clickable(enabled = !sessionActive && !isStarting, onClick = { isStarting = true })
                    .then(
                        if (otterContentDescription != null) {
                            Modifier.semantics { contentDescription = otterContentDescription }
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                OtterZenMark(markSize = OtterMarkSize)
            }
        }
    }
}

/**
 * Riga discreta con lo stato delle sessioni, che è anche l'unico ingresso
 * alla Cronologia dalla Home.
 *
 * Sostituisce la card con il grafico a barre degli ultimi 7 giorni, rimossa
 * su segnalazione ("il grafico e la cronologia in home creano ingombro").
 * Il vero argomento però non era lo spazio: la Home diceva **la stessa cosa
 * due volte**, una qui come riga di riepilogo e una nell'intestazione della
 * card, e la card aggiungeva solo la forma a barre. Una schermata che parla
 * di quanto *non* usi il telefono non ha motivo di somigliare a un cruscotto
 * di analytics.
 *
 * Lo streak non si perde: è l'unico dato che la riga non aveva già, e qui
 * assorbe la stessa logica che l'intestazione della card applicava — streak
 * se è di almeno un giorno, altrimenti il riepilogo della settimana. La
 * forma della settimana, quella sì, diventa un numero da leggere invece di
 * una sagoma da guardare: resta a un tap di distanza in Cronologia.
 */
@Composable
private fun SessionsSummaryLink(
    streakDays: Int,
    weekSummary: WeekSummary,
    onHistory: () -> Unit,
) {
    // Il colore del link fa il lavoro che faceva il contenitore. Una riga
    // grigia al 70% non si capiva che fosse toccabile — era la ragione della
    // pastiglia — ma in `primary`, con il chevron accanto, si legge come un
    // link e basta: nessuno sfondo da giustificare.
    val linkColor = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .padding(top = 20.dp)
            .clip(RoundedCornerShape(50))
            // onClickLabel invece di una stringa in più: TalkBack annuncia
            // "apri Cronologia" come azione della riga, senza che il "›"
            // debba significare qualcosa per chi non lo vede.
            .clickable(
                onClickLabel = stringResource(R.string.history_title),
                onClick = onHistory,
            )
            // Senza sfondo il bersaglio non si vede più, quindi va imposto:
            // il testo è alto una ventina di dp, sotto i 48 che servono a un
            // dito. Il padding sta dopo `clickable` apposta, così l'area
            // toccata e quella che si illumina sono la stessa.
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        // La fogliolina resta: è l'unico segno che lega questa riga allo
        // stagno qui sopra, e senza pastiglia non compete più con nulla.
        SprigMark(markSize = 14.dp)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = if (streakDays >= 1) {
                pluralStringResource(R.plurals.streak_days, streakDays, streakDays)
            } else {
                weeklySummaryText(weekSummary)
            },
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            color = linkColor,
        )
        Text(
            text = "›",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = linkColor,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/**
 * Increspature ambientali (3 anelli sfasati che si espandono e svaniscono in
 * loop): puramente decorative, segnalano "stagno in attesa". Tinte di
 * "primary" a opacità molto bassa (max ~0.18) — seguono la palette scelta
 * (Sage/Lavender/Terracotta) restando comunque tenui, non un colore acceso.
 *
 * Disegnate su un `Canvas` a piena pagina (passato come `background` a
 * [OtterAnchoredScreen], non più un figlio del piccolo `Box` dell'otter —
 * richiesto esplicitamente: "le onde dietro l'otter si estendono su tutta
 * la pagina, anche uscendo"), quindi il centro non è più quello del proprio
 * riquadro ma [centerY] passato dal chiamante, l'unico punto che
 * [OtterAnchoredScreen] garantisce identico in entrambe le schermate. Il
 * raggio massimo è ancorato a `size.height` (non più a `size.minDimension`
 * del vecchio riquadro 260dp) apposta perché ecceda le dimensioni della
 * pagina prima che l'ultimo anello sparisca: a quel punto l'alpha è già
 * vicina a zero, quindi "uscire dai bordi" si vede come una dissolvenza sul
 * limite dello schermo, non come un cerchio che si taglia di netto.
 */
/**
 * I quattro dischi fermi dello stagno, in una `Canvas` **separata da quella
 * animata**.
 *
 * Non è una divisione estetica ma di costo: stando dentro [AmbientRipples]
 * venivano riempiti da capo a ogni fotogramma su tutta la pagina, e su
 * emulatore la sola Home teneva la CPU al 70-85% (misurato con `top`:
 * Impostazioni, che non anima nulla, stava a 0%). Qui dentro non si legge
 * alcuno stato animato, quindi Compose la disegna una volta e la riusa.
 */
@Composable
private fun PondStill(centerY: Dp, modifier: Modifier = Modifier) {
    val ringColor = MaterialTheme.colorScheme.tertiary
    val brightColor = MaterialTheme.colorScheme.surfaceBright

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, centerY.toPx())

        // Lo stagno fermo: **tre dischi pieni** concentrici, di tonalità
        // alternate — pallido, chiaro, pallido — come nel design (prima erano
        // due anelli di contorno: segnalato con l'immagine alla mano, "sono
        // 3 cerchi concentrici"). Il disco di mezzo usa `surfaceBright`
        // perché nel mockup è più chiaro dello sfondo, non uguale.
        // Misurati sul mockup pixel per pixel, sulla riga che passa per il
        // **centro vero** dello stagno (y=145 del mockup, non y=127: la prima
        // lettura era presa più in alto e leggeva il disco sbagliato).
        // Confini a 27, 48, 64 e 80px dal centro su 226px di larghezza,
        // cioè 47, 83, 110 e 138dp su 390dp — qui riscalati.
        //
        // Sono **quattro** dischi e il bianco è il secondo: pallido attorno
        // alla mascotte, poi bianco, poi pallido, poi velo. Le due letture
        // precedenti sbagliavano proprio qui, una invertendo l'ordine e
        // l'altra scambiando il disco pallido interno per l'alone che
        // [OtterZenMark] si disegna da sé.
        val innerPaleRadius = 54.dp.toPx()
        val whiteRadius = 91.dp.toPx()
        val paleRadius = 121.dp.toPx()
        val veilRadius = 151.dp.toPx()
        drawCircle(color = ringColor, radius = veilRadius, center = center, alpha = 0.28f)
        drawCircle(color = ringColor, radius = paleRadius, center = center, alpha = 0.75f)
        drawCircle(color = brightColor, radius = whiteRadius, center = center)
        drawCircle(color = ringColor, radius = innerPaleRadius, center = center, alpha = 0.75f)

    }
}

@Composable
private fun AmbientRipples(centerY: Dp, restartKey: Int, modifier: Modifier = Modifier) {
    // **Lo stagno si acquieta.** Dopo [RIPPLE_QUIET_AFTER_MILLIS] le onde
    // svaniscono e l'animazione si ferma del tutto: a schermata ferma il
    // consumo va a zero invece di restare lì a ridipingere per sempre.
    //
    // Non è solo una misura di risparmio, è ciò che fa l'acqua: un sasso
    // produce onde che *finiscono*. Quelle infinite erano l'artificio.
    //
    // Riparte quando si rientra nella schermata ([restartKey] è il
    // `resumeSignal` della Home, cambia a ogni onResume) — cioè quando
    // qualcuno torna a guardare lo stagno.
    var active by remember(restartKey) { mutableStateOf(true) }
    LaunchedEffect(restartKey) {
        active = true
        delay(SCENE_QUIET_AFTER_MILLIS)
        active = false
    }
    // Le onde in corso non si congelano a mezz'aria: sfumano in due secondi
    // e mezzo, poi l'orologio si spegne.
    val fade by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(2500, easing = LinearEasing),
        label = "rippleFade",
    )
    val running = active || fade > 0.01f
    // 9 secondi per giro, non 3,6: è un sasso caduto nell'acqua, non una
    // scansione radar. L'avanzamento è a scatti, vedi [steppedFraction].
    val tState = steppedFraction(RIPPLE_PERIOD_MILLIS, stepsPerSecond = RIPPLE_STEPS_PER_SECOND, running = running)
    if (!running) return
    // Le onde usano `tertiary` (#d5e0d5 nelle palette verdi), che nel design
    // system è esattamente il colore dei "ripple borders" — non `primary` a
    // bassa opacità, che dava un grigio.
    val ringColor = MaterialTheme.colorScheme.tertiary

    // Nessun `graphicsLayer()` qui: misurato su Galaxy S22, con e senza, la
    // CPU del processo resta sul 30% — un livello proprio non aiuta, perché
    // il costo non è propagare l'invalidazione ai vicini ma ridisegnare
    // questa tela, che è grande quanto lo schermo. Non aggiungiamo un
    // livello che non paga.
    Canvas(modifier = modifier) {
        val t = tState.value
        val center = Offset(size.width / 2f, centerY.toPx())
        val veilRadius = 151.dp.toPx()
        // Quanto cresce un anello prima di spegnersi. **Non** piu'
        // `size.height / 1.1` (circa 865dp): a quella velocita' l'onda usciva
        // dallo schermo dopo un quinto della sua vita e le altre due
        // continuavano fuori campo — se ne vedeva una sola, mentre di anelli
        // ce ne sono sempre tre. Con 300dp l'ultimo arco e' ancora dentro i
        // bordi quando svanisce, quindi se ne vedono due o tre insieme, che e'
        // cio' che fa uno stagno.
        val maxExtra = 300.dp.toPx()

        // Tre anelli sfasati, ciascuno con la sua vita indipendente. Ogni
        // anello imita ciò che fa un'increspatura vera:
        //
        // - **rallenta**: il raggio segue 1-(1-t)^2 invece di t, quindi parte
        //   svelto dal bordo dello stagno e si allarga sempre più piano.
        // - **si assottiglia**: il tratto passa da 3dp a 0,8dp mentre si
        //   allarga, perché la stessa energia si distribuisce su una
        //   circonferenza più lunga.
        // - **svanisce prima della fine**: l'opacità cala con (1-t)^1.8, così
        //   l'onda è già sparita quando arriverebbe ai bordi, invece di
        //   spegnersi di colpo là dove il cerchio si taglierebbe.
        listOf(0f, 0.33f, 0.66f).forEach { phase ->
            val localT = (t + phase) % 1f
            // t^0.8: rallenta, ma appena — con una decelerazione forte
            // (1-(1-t)^2) l'anello schizzava via dal bordo e passava quasi
            // tutta la sua vita lontano e sbiadito, cioè invisibile.
            val eased = localT.pow(0.8f)
            drawCircle(
                color = ringColor,
                radius = veilRadius + eased * maxExtra,
                center = center,
                // Dissolvenza quasi lineare: con (1-t)^1.3 il terzo anello
                // era gia' a un quinto di opacita' su un colore che di suo ha
                // poco contrasto, cioe' invisibile.
                alpha = (1f - localT) * 0.95f * fade,
                style = Stroke(width = (3.5f - 2.3f * eased).dp.toPx()),
            )
        }
    }
}


/**
 * Frazione 0f→1f che avanza a scatti discreti invece che a ogni fotogramma.
 *
 * Le due animazioni della Home — increspature e oscillazione dell'otter —
 * durano 9 e 3,2 secondi: a 60fps ridisegnavano 60 volte al secondo per
 * spostare le cose di frazioni di pixel. Scandirle più lentamente costa
 * proporzionalmente meno, ma sotto una certa soglia lo scatto si vede: vedi
 * [RIPPLE_STEPS_PER_SECOND] per dove è finito il compromesso e perché.
 *
 * `withInfiniteAnimationFrameMillis` e non un timer proprio: segue
 * l'orologio delle animazioni di Compose, quindi si ferma da sé quando la
 * composizione esce di scena e rispetta l'impostazione di sistema
 * "rimuovi animazioni".
 */
@Composable
private fun steppedFraction(periodMillis: Int, stepsPerSecond: Int = 20, running: Boolean = true): State<Float> {
    // **Lo stato torna come `State`, non come `Float`.** Se il valore venisse
    // letto qui in fase di composizione, ogni scatto ricomporrebbe chi lo
    // legge; letto invece dentro la lambda di disegno, Compose salta
    // composizione e layout e ridisegna soltanto. Misurato su Galaxy S22 con
    // `dumpsys gfxinfo`: GPU 3ms per fotogramma ma fotogramma totale 16ms,
    // con "Slow UI thread" su tutti — il costo era la ricomposizione, non il
    // disegno.
    val fractionState = remember { mutableFloatStateOf(0f) }
    var fraction by fractionState
    LaunchedEffect(periodMillis, stepsPerSecond, running) {
        if (!running) return@LaunchedEffect
        val steps = (periodMillis / 1000f * stepsPerSecond).toInt().coerceAtLeast(1)
        val stepMillis = (1000f / stepsPerSecond).toLong()
        while (true) {
            // L'aggancio all'orologio delle animazioni di Compose, e non un
            // timer proprio, è ciò che fa fermare tutto quando la schermata
            // non è visibile: nessun fotogramma, nessun risveglio. Verificato:
            // con l'app in background il processo sta a 0%.
            withInfiniteAnimationFrameMillis { now ->
                val step = ((now % periodMillis) / periodMillis.toFloat() * steps).toInt()
                val next = step / steps.toFloat()
                if (next != fraction) fraction = next
            }
            // **L'attesa qui in mezzo è il punto.** Senza, il ciclo si
            // riaggancia subito al fotogramma successivo: su un telefono a
            // 120Hz sono 120 risvegli al secondo per cambiare lo stato 15
            // volte, e la pipeline grafica non torna mai a riposo. Misurato
            // su Galaxy S22: 38-48% di un core contro il 3-5% che resta
            // aspettando fra un passo e l'altro.
            delay(stepMillis)
        }
    }
    return fractionState
}

/**
 * Dopo quanto la scena si ferma — onde e oscillazione — se nessuno torna a
 * guardarla. Riparte al rientro nella schermata.
 */
internal const val SCENE_QUIET_AFTER_MILLIS = 30_000L

/** Scatti al secondo delle increspature, vedi [steppedFraction]. */
// A 10 scatti al secondo le onde si vedevano avanzare a strappi: il bordo
// dell'anello salta una decina di pixel per volta, e l'easing `t^0.8` rende
// i primi salti i più lunghi, proprio dove l'anello è più nitido.
//
// Il costo è lineare nel numero di ridisegni. Misurato su Galaxy S22 a
// processo caldo, build debug, scartando il primo campione di ogni serie
// (che è il picco di avvio, non il regime):
//
//     10 scatti/s → 10-20% di un core
//     20 scatti/s → 20-30%
//     30 scatti/s → 30-40%
//
// Si paga 30: lo scatto sparisce, e la spesa è comunque limitata ai primi
// [SCENE_QUIET_AFTER_MILLIS] — dopo, la scena si ferma e il processo torna
// a zero. Trenta secondi al 30% di un core non scaldano niente; era
// l'animazione infinita a farlo.
private const val RIPPLE_STEPS_PER_SECOND = 30

// L'oscillazione dell'otter sta anch'essa a 30: chi la legge lo fa dentro un
// `graphicsLayer` (vedi [PondOtter]), quindi ogni scatto sposta soltanto un
// livello già disegnato invece di ridisegnarlo. Misurato: alzarla da 6 a 30
// non ha spostato la CPU del processo.
private const val OTTER_STEPS_PER_SECOND = 30

/** Durata di un giro completo di un'increspatura, vedi [AmbientRipples]. */
private const val RIPPLE_PERIOD_MILLIS = 9000

/**
 * Impulso "a tocco": un anello che si espande e sfuma più un lampo pieno al
 * centro che sfuma ancora più in fretta — un solo passaggio (non in loop
 * come [AmbientRipples]), guidato da [progress] (0f→1f, animato dal
 * chiamante). Dà peso visivo al tap di avvio invece del salto secco che
 * c'era prima verso BlockScreen.
 */
@Composable
internal fun TapConfirmBurst(progress: Float, modifier: Modifier = Modifier) {
    val ringColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier) {
        val baseRadius = size.minDimension / 5f
        val maxExtra = size.minDimension / 2f

        drawCircle(
            color = ringColor,
            radius = baseRadius + progress * maxExtra,
            alpha = (1f - progress) * 0.5f,
            style = Stroke(width = 3.dp.toPx()),
        )
        drawCircle(
            color = ringColor,
            radius = baseRadius * (1f + progress * 0.3f),
            alpha = (1f - progress) * (1f - progress) * 0.25f,
        )
    }
}

/**
 * Offset verticale animato per l'effetto "otter che fluttua" — condiviso fra
 * [PondOtter] (Home) e `BlockScreen`, così la mascotte fluttua allo stesso
 * identico modo ovunque compaia con l'anello di avanzamento intorno, non solo
 * qui. Non `private`: unica ragione per cui è definita in questo file e non
 * altrove è che [PondOtter] è stata la prima a usarla.
 */
@Composable
fun rememberOtterFloatOffset(periodMillis: Int, restartKey: Int = 0): State<Float> {
    // Anche l'otter si acquieta, come lo stagno (vedi [AmbientRipples]): non
    // è un dettaglio di risparmio ma la voce grossa. Misurato sul Mac che
    // ospita l'emulatore: a schermata ferma, con le sole onde spente, il
    // processo emulatore stava ancora al ~300% di CPU contro il ~9% ad app
    // chiusa — cioè quasi tutto quel consumo era questa oscillazione di 10dp.
    //
    // L'ampiezza scende a zero in due secondi e mezzo invece di fermarsi di
    // scatto, così l'otter si posa invece di inchiodarsi a mezz'aria.
    var active by remember(restartKey) { mutableStateOf(true) }
    LaunchedEffect(restartKey) {
        active = true
        delay(SCENE_QUIET_AFTER_MILLIS)
        active = false
    }
    val amplitudeState = animateFloatAsState(
        targetValue = if (active) 5f else 0f,
        animationSpec = tween(2500, easing = LinearEasing),
        label = "otterBobAmplitude",
    )
    val running = active || amplitudeState.value > 0.05f
    // Una sinusoide sulla sorgente a scatti delle increspature: il periodo
    // completo è andata+ritorno, quindi il doppio di quello che chiedeva
    // `RepeatMode.Reverse`.
    val tState = steppedFraction(periodMillis * 2, stepsPerSecond = OTTER_STEPS_PER_SECOND, running = running)
    // `derivedStateOf`: il seno si ricalcola quando serve, ma chi legge il
    // risultato lo fa in fase di disegno (vedi `graphicsLayer` in PondOtter),
    // quindi nessuna ricomposizione per oscillare di 10dp.
    return remember { derivedStateOf { sin(tState.value * 2f * PI.toFloat()) * amplitudeState.value } }
}

/**
 * Chiusura dell'anello a fine pausa: l'anello completo si stacca dal suo
 * raggio, si allarga verso l'esterno e svanisce.
 *
 * È l'esatto contrario di [TapConfirmBurst], che all'avvio porta un'onda
 * verso l'interno stringendosi sull'otter — stesso vocabolario visivo letto
 * al rovescio, invece di introdurre un'animazione nuova per la fine.
 *
 * Usata solo alla **scadenza naturale**: allo sblocco con password l'anello
 * non è al 100%, e vederlo "compiersi" racconterebbe una cosa che non è
 * successa. Lì resta la sola dissolvenza — vedi BlockScreen.
 *
 * [progress] va da 0 (anello fermo al suo posto) a 1 (svanito).
 */
@Composable
fun RingReleaseBurst(progress: Float, modifier: Modifier = Modifier) {
    val ringColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier) {
        val strokeWidth = 4.dp.toPx()
        // Parte esattamente dov'era l'anello di avanzamento, così lo
        // sostituisce senza scarti di raggio.
        val startRadius = (size.minDimension - strokeWidth) / 2f
        val radius = startRadius * (1f + progress * 0.45f)

        drawCircle(
            color = ringColor,
            radius = radius,
            // Si assottiglia mentre si allarga: sembra che si allenti, non
            // che venga ingrandito.
            alpha = (1f - progress) * (1f - progress),
            style = Stroke(width = strokeWidth * (1f - progress * 0.6f)),
        )
    }
}

/**
 * Anello di avanzamento della sessione attiva: l'unico punto della Home
 * dove "primary" è usato a piena intensità (non a bassa opacità come nel
 * resto della scena), perché qui porta un'informazione reale — quanto è
 * passato — e non è decorazione. Non `private`: condiviso anche da
 * `BlockScreen`, che dopo il redesign mostra lo stesso identico
 * anello+otter fluttuante della Home invece di un badge statico.
 */
@Composable
fun ProgressRing(fraction: Float, modifier: Modifier = Modifier) {
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val progressColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier) {
        val strokeWidth = 4.dp.toPx()
        val diameter = size.minDimension - strokeWidth
        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
        val arcSize = Size(diameter, diameter)
        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
        drawArc(
            color = progressColor,
            startAngle = -90f,
            sweepAngle = 360f * fraction,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
    }
}

/**
 * Riga di chip per scegliere la durata della pausa, scorrevole in
 * orizzontale (8 opzioni, troppe per stare tutte a schermo su telefoni
 * stretti). Sfondo disegnato a mano con "primary" a bassa opacità invece del
 * FilterChip di M3: i colori di stato di FilterChip derivano da ruoli non
 * personalizzati per palette (secondaryContainer ecc., vedi la nota su
 * surfaceVariant in CLAUDE.md) e renderebbero comunque colori fissi non
 * coerenti col tema. Il testo resta "onSurface" (leggibilità), solo lo
 * sfondo segue la palette scelta.
 */
@Composable
private fun DurationChipRow(selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DURATION_LABELS.forEachIndexed { index, label ->
            val selected = (index + 1) == selectedIndex
            // La durata scelta è l'unico elemento pieno della schermata
            // (redesign): prima si distingueva solo per una tinta più
            // scura, differenza debole su schermo piccolo. Non compete con
            // l'otter, che è l'azione: questa è una scelta già fatta.
            Surface(
                onClick = { onSelect(index + 1) },
                shape = RoundedCornerShape(50),
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f)
                },
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    },
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

/** Riassunto degli ultimi 7 giorni: totale sessioni e minuti, per riusare
 * le stringhe weekly_summary_* già scritte per la Cronologia (vedi
 * HistoryScreen.kt — stessa finestra "ultimi 7 giorni", non settimana di
 * calendario). I dati giorno per giorno servivano al grafico a barre della
 * Home, rimosso: le barre degli ultimi 7 giorni restano in Cronologia, che
 * se li ricalcola per conto suo (vedi WeeklyChart.kt). */
private data class WeekSummary(
    val totalSessions: Int,
    val totalMinutes: Int,
)

private fun weekSummaryOf(records: List<SessionRecord>): WeekSummary {
    fun dayStart(timeMs: Long): Long = Calendar.getInstance().apply {
        timeInMillis = timeMs
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val minutesByDay = HashMap<Long, Int>()
    val sessionsByDay = HashMap<Long, Int>()
    for (record in records) {
        val day = dayStart(record.startTimeMs)
        minutesByDay[day] = (minutesByDay[day] ?: 0) + record.effectiveMinutes
        sessionsByDay[day] = (sessionsByDay[day] ?: 0) + 1
    }

    val cursor = Calendar.getInstance().apply { timeInMillis = dayStart(System.currentTimeMillis()) }
    cursor.add(Calendar.DATE, -6)

    var totalSessions = 0
    var totalMinutes = 0
    repeat(7) {
        val day = cursor.timeInMillis
        totalSessions += sessionsByDay[day] ?: 0
        totalMinutes += minutesByDay[day] ?: 0
        cursor.add(Calendar.DATE, 1)
    }
    return WeekSummary(totalSessions, totalMinutes)
}

@Composable
private fun weeklySummaryText(summary: WeekSummary): String = if (summary.totalSessions == 0) {
    stringResource(R.string.weekly_summary_none)
} else {
    pluralStringResource(
        R.plurals.weekly_summary_sessions,
        summary.totalSessions,
        summary.totalSessions,
        formatMinutes(summary.totalMinutes),
    )
}

/** Duplica HistoryScreen.kt's formatMinutes(): stessa resa "1h 30m", non
 * condivisa perché entrambe le funzioni sono private ai rispettivi file
 * (stesso pattern di last7DayMinutes/dayStart già documentato altrove). */
private fun formatMinutes(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "${m}m"
        m == 0 -> "${h}h"
        else -> "${h}h ${m}m"
    }
}
