package com.calmotter.app.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calmotter.app.BuildConfig
import com.calmotter.app.R
import com.calmotter.app.SessionHistoryManager
import com.calmotter.app.SessionManager
import com.calmotter.app.SessionRecord
import com.calmotter.app.SessionStreak
import com.calmotter.app.ui.mascot.OtterFloatMark
import com.calmotter.app.ui.mascot.TogetherMark
import java.util.Calendar

// Indice 1 = 30 min, indice 2 = 60 min, ... fino a 4 ore, a passi di 30 minuti
// (stessa tabella usata da MainActivity prima della migrazione a Compose;
// ora mostrata come riga di chip invece che come NumberPicker a rotellina,
// vedi DurationChipRow).
private val DURATION_LABELS = arrayOf(
    "30 min", "1 h", "1 h 30", "2 h", "2 h 30", "3 h", "3 h 30", "4 h"
)

/**
 * Spazio sopra lo slot dell'otter, misurato dall'inizio dell'area sicura:
 * il padding superiore della Home (24dp) + l'intestazione (48dp, che è
 * l'altezza dell'IconButton delle impostazioni, non quella del testo) + il
 * padding sotto l'intestazione (24dp).
 *
 * [BlockScreen] riserva esattamente lo stesso spazio pur non avendo
 * un'intestazione: è ciò che fa cadere il suo otter nello stesso identico
 * punto di quello della Home.
 */
internal val OtterSlotTopInset = 96.dp

/**
 * Altezza fissa dello slot che contiene otter e anello, identica nelle due
 * schermate.
 *
 * Fissa e non `weight(1f)`: prima la Home centrava l'otter nello spazio che
 * avanzava, e quello spazio si restringe quando la card della cronologia in
 * fondo cresce. Misurato su un 1080×2220, centro dell'otter in Home: y=812 a
 * cronologia vuota, y=760 con qualche sessione — 52px di differenza fra due
 * stati della *stessa* schermata, mentre [BlockScreen] lo disegnava in un
 * punto suo, fisso. Nessuna posizione statica poteva quindi coincidere con
 * entrambe, e all'avvio della pausa l'elemento condiviso animava la
 * differenza: l'otter scivolava. Non era colpa della transizione, ma del
 * fatto che le due schermate lo mettevano in punti diversi.
 *
 * Con lo slot fisso non c'è più nulla da interpolare — verificato misurando
 * l'otter a schermo con l'oscillazione congelata: righe 706-790 identiche in
 * Home a cronologia vuota, in Home con sessioni e in [BlockScreen]. Resta
 * immobile mentre il resto sfuma (vedi MainActivity).
 *
 * Effetto collaterale voluto: anche la Home da sola smette di riassestarsi
 * quando registri la prima sessione.
 */
internal val OtterSlotHeight = 320.dp

/**
 * Schermata home ("Living Pond" — vedi specs/home-and-settings): lo stagno
 * con l'otter è l'unico pulsante di avvio (si tocca l'otter stesso), un
 * anello colorato attorno a lei mostra l'avanzamento mentre una sessione è
 * attiva, e la card sotto riassume streak/ultime sessioni con un CTA verso
 * la Cronologia. Tema, cambio password, gestione app consentite, frasi
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
    onSessionStarted: () -> Unit = {},
    onGroupPauseHost: () -> Unit = {},
    onGroupPauseJoin: () -> Unit = {},
    // Vedi il parametro omonimo di [BlockScreen]: è lo stesso otter, ed è
    // [MainActivity] a legarli come elemento condiviso.
    otterModifier: Modifier = Modifier,
    // Vedi [rememberOtterFloatOffset]: passato dall'esterno quando la stessa
    // oscillazione deve proseguire anche in [BlockScreen], null quando questa
    // schermata è sola e può gestirsela da sé.
    otterFloatOffset: Float? = null,
) {
    val context = LocalContext.current

    var accessibilityOk by remember { mutableStateOf(false) }
    var dndOk by remember { mutableStateOf(false) }
    var sessionActive by remember { mutableStateOf(false) }
    var remainingMillis by remember { mutableStateOf(0L) }
    var totalMillis by remember { mutableStateOf(0L) }
    var streakDays by remember { mutableIntStateOf(0) }
    var hasHistory by remember { mutableStateOf(false) }
    var weekSummary by remember { mutableStateOf(WeekSummary(List(7) { 0 }, List(7) { 0 }, 0, 0)) }
    var selectedDurationIndex by remember { mutableIntStateOf(1) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showGroupPauseChooser by remember { mutableStateOf(false) }

    fun refreshDerivedState() {
        accessibilityOk = isAccessibilityServiceEnabled()
        dndOk = isDndAccessGranted()
        sessionActive = sessionManager.isSessionActive()
        remainingMillis = sessionManager.remainingMillis()
        totalMillis = sessionManager.totalMillis()
        val history = sessionHistoryManager.getAll()
        streakDays = SessionStreak.currentStreakDays(history)
        hasHistory = history.isNotEmpty()
        weekSummary = weekSummaryOf(history)
    }

    // Rieseguito a ogni onResume() dell'Activity (resumeSignal incrementato
    // lì): equivalente del vecchio refreshUi()/bindMainScreen() chiamato da
    // onResume(), dato che setContent {} viene invocato una sola volta.
    LaunchedEffect(resumeSignal) {
        refreshDerivedState()
    }

    val sessionStartedText = stringResource(R.string.session_started)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .calmBackground()
            .safeDrawingPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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

        // Questa Column intermedia si prende lo spazio che avanza, ma al suo
        // interno PondScene ha ormai altezza fissa ([OtterSlotHeight]): lo
        // spazio in eccesso finisce sotto, tra il bottone "Tempo insieme" e
        // la card della cronologia, invece di essere distribuito attorno
        // all'otter. È ciò che tiene l'otter a un'altezza costante qualunque
        // sia il contenuto della card — vedi [OtterSlotHeight] per il perché.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PondScene(
                modifier = Modifier,
                sessionActive = sessionActive,
                remainingMillis = remainingMillis,
                totalMillis = totalMillis,
                selectedDurationIndex = selectedDurationIndex,
                onSelectDuration = { selectedDurationIndex = it },
                onStart = {
                    // In debug (incluso quello prodotto in CI) i permessi
                    // Accessibilità/DND non bloccano l'avvio di una sessione,
                    // per poter testare il resto del flusso (Settings,
                    // History, schermata di blocco...) senza doverli
                    // concedere davvero a ogni installazione pulita — vedi
                    // SessionManager.setOnlyCallsAllowed(), che già ignora
                    // silenziosamente il DND se non concesso, quindi questo
                    // bypass non nasconde un crash, solo il blocco vero e
                    // proprio non scatta. In release il controllo resta
                    // invariato.
                    if (BuildConfig.DEBUG || (accessibilityOk && dndOk)) {
                        val durationMinutes = selectedDurationIndex * 30
                        sessionManager.startSession(durationMinutes)
                        Toast.makeText(context, sessionStartedText, Toast.LENGTH_SHORT).show()
                        refreshDerivedState()
                        // Passa subito a BlockScreen invece di restare su
                        // MainScreen mostrando il progress ring: le due
                        // schermate ora sono unificate, vedi
                        // MainActivity.enterBlockScreen().
                        onSessionStarted()
                    } else {
                        showPermissionDialog = true
                    }
                },
                otterModifier = otterModifier,
                otterFloatOffset = otterFloatOffset,
            )

            if (!sessionActive) {
                Text(
                    text = weeklySummaryText(weekSummary),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    modifier = Modifier.padding(vertical = 20.dp)
                )
                // Non compete con il tap sull'otter (l'azione primaria):
                // testo piccolo e defilato, non un altro bottone pieno —
                // vedi specs/group-pause/design.md. TogetherMark (le due
                // zampe di PactPawsMark senza il badge circolare) come
                // icona leading, su richiesta esplicita ("un po' anonima"):
                // un'icona rende il bottone più riconoscibile a colpo
                // d'occhio invece di solo testo tra i due CTA della Home.
                TextButton(onClick = { showGroupPauseChooser = true }) {
                    TogetherMark(markSize = 18.dp)
                    Text(
                        text = stringResource(R.string.group_pause_entry_button),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }

        SessionsChartCard(
            streakDays = streakDays,
            hasHistory = hasHistory,
            weekSummary = weekSummary,
            dimmed = sessionActive,
            onHistory = onHistory,
        )
    }

    if (showPermissionDialog) {
        PermissionExplainerDialog(
            accessibilityOk = accessibilityOk,
            dndOk = dndOk,
            onGrantAccessibility = {
                showPermissionDialog = false
                onGrantAccessibility()
            },
            onGrantDnd = {
                showPermissionDialog = false
                onGrantDnd()
            },
            onDismiss = { showPermissionDialog = false },
        )
    }

    if (showGroupPauseChooser) {
        AlertDialog(
            onDismissRequest = { showGroupPauseChooser = false },
            title = { Text(stringResource(R.string.group_pause_entry_button)) },
            text = { Text(stringResource(R.string.group_pause_chooser_intro)) },
            confirmButton = {
                TextButton(onClick = {
                    showGroupPauseChooser = false
                    onGroupPauseHost()
                }) { Text(stringResource(R.string.group_pause_chooser_create)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showGroupPauseChooser = false
                    onGroupPauseJoin()
                }) { Text(stringResource(R.string.group_pause_chooser_join)) }
            },
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
private fun PondScene(
    modifier: Modifier = Modifier,
    sessionActive: Boolean,
    remainingMillis: Long,
    totalMillis: Long,
    selectedDurationIndex: Int,
    onSelectDuration: (Int) -> Unit,
    onStart: () -> Unit,
    otterModifier: Modifier = Modifier,
    otterFloatOffset: Float? = null,
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
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Lo stagno (increspature/anello + otter) ha altezza fissa e l'otter
        // è centrato al suo interno: la sua posizione dipende così solo da
        // [OtterSlotTopInset] + [OtterSlotHeight], mai dal contenuto sotto.
        // Vedi [OtterSlotHeight] per cosa faceva prima e perché è cambiato.
        Box(
            modifier = Modifier.height(OtterSlotHeight).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier.size(260.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (!sessionActive) {
                    AmbientRipples(modifier = Modifier.matchParentSize())
                    if (isStarting) {
                        TapConfirmBurst(progress = burstProgress.value, modifier = Modifier.matchParentSize())
                    }
                } else {
                    val fraction = if (totalMillis > 0) {
                        (1f - remainingMillis.toFloat() / totalMillis.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    ProgressRing(fraction = fraction, modifier = Modifier.size(176.dp))
                }

                val floatOffset = otterFloatOffset
                    ?: rememberOtterFloatOffset(periodMillis = if (sessionActive) 5200 else 3200)

                Box(
                    modifier = Modifier
                        .offset(y = floatOffset.dp)
                        .scale(otterScale)
                        .clip(CircleShape)
                        .clickable(enabled = !sessionActive && !isStarting, onClick = { isStarting = true }),
                    contentAlignment = Alignment.Center,
                ) {
                    OtterFloatMark(modifier = otterModifier, markSize = 124.dp)
                }
            }
        }

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
            Text(
                text = stringResource(R.string.home_start_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            )
            Spacer(modifier = Modifier.height(10.dp))
            DurationChipRow(
                selectedIndex = selectedDurationIndex,
                onSelect = onSelectDuration,
            )
        }
    }
}

/**
 * Increspature ambientali (3 anelli sfasati che si espandono e svaniscono in
 * loop): puramente decorative, segnalano "stagno in attesa". Tinte di
 * "primary" a opacità molto bassa (max ~0.18) — seguono la palette scelta
 * (Sage/Lavender/Terracotta) restando comunque tenui, non un colore acceso.
 */
@Composable
private fun AmbientRipples(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "ripples")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3600, easing = LinearEasing),
        ),
        label = "rippleT",
    )
    val ringColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier) {
        val baseRadius = size.minDimension / 5f
        val maxExtra = size.minDimension / 2.4f
        val strokeWidth = 2.dp.toPx()
        listOf(0f, 0.33f, 0.66f).forEach { phase ->
            val localT = (t + phase) % 1f
            drawCircle(
                color = ringColor,
                radius = baseRadius + localT * maxExtra,
                alpha = (1f - localT) * 0.18f,
                style = Stroke(width = strokeWidth),
            )
        }
    }
}

/**
 * Impulso "a tocco": un anello che si espande e sfuma più un lampo pieno al
 * centro che sfuma ancora più in fretta — un solo passaggio (non in loop
 * come [AmbientRipples]), guidato da [progress] (0f→1f, animato dal
 * chiamante). Dà peso visivo al tap di avvio invece del salto secco che
 * c'era prima verso BlockScreen.
 */
@Composable
private fun TapConfirmBurst(progress: Float, modifier: Modifier = Modifier) {
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
 * [PondScene] (Home) e `BlockScreen`, così la mascotte fluttua allo stesso
 * identico modo ovunque compaia con l'anello di avanzamento intorno, non solo
 * qui. Non `private`: unica ragione per cui è definita in questo file e non
 * altrove è che [PondScene] è stata la prima a usarla.
 */
@Composable
fun rememberOtterFloatOffset(periodMillis: Int): Float {
    val floatTransition = rememberInfiniteTransition(label = "otterFloat")
    val floatOffset by floatTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(periodMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "otterFloatY",
    )
    return floatOffset
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
            Surface(
                onClick = { onSelect(index + 1) },
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primary.copy(alpha = if (selected) 0.22f else 0.08f),
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (selected) 1f else 0.65f),
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

/**
 * Card con streak/ultime sessioni (barre degli ultimi 7 giorni, con le
 * iniziali del giorno sotto ciascuna) e CTA verso la Cronologia — l'intera
 * card è cliccabile. Sfondo e barre in "primary" a bassa opacità (segue la
 * palette scelta restando tenue); testo "onSurface" per la leggibilità.
 * Attenuata (non nascosta) durante una sessione attiva: resta consultabile
 * ma non è il focus.
 *
 * Quando [hasHistory] è falso (nessuna sessione mai registrata) le barre —
 * tutte appiattite sul minimo dello 0.04f — vengono sostituite da una riga
 * di invito: sette trattini identici somigliano a un errore di rendering
 * più che a "zero sessioni", specialmente al primissimo avvio.
 */
@Composable
private fun SessionsChartCard(
    streakDays: Int,
    hasHistory: Boolean,
    weekSummary: WeekSummary,
    dimmed: Boolean,
    onHistory: () -> Unit,
) {
    val maxMinutes = (weekSummary.dailyMinutes.maxOrNull() ?: 0).coerceAtLeast(1)
    val weekdayInitials = stringArrayResource(R.array.weekday_initials)

    Surface(
        onClick = onHistory,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (dimmed) 0.6f else 1f),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (streakDays >= 1) {
                        stringResource(R.string.streak_days, streakDays)
                    } else {
                        stringResource(R.string.home_chart_label)
                    },
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.home_history_cta),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }

            if (!hasHistory) {
                Text(
                    text = stringResource(R.string.home_chart_empty),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp, bottom = 4.dp),
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .height(48.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    weekSummary.dailyMinutes.forEachIndexed { index, minutes ->
                        val isToday = index == weekSummary.dailyMinutes.lastIndex
                        val fraction = (minutes.toFloat() / maxMinutes).coerceIn(0.04f, 1f)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(fraction)
                                .background(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = if (isToday) 0.55f else 0.18f),
                                    shape = RoundedCornerShape(4.dp),
                                )
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    weekSummary.weekdayIndices.forEachIndexed { index, weekdayIndex ->
                        val isToday = index == weekSummary.weekdayIndices.lastIndex
                        Text(
                            text = weekdayInitials.getOrElse(weekdayIndex) { "" },
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isToday) 0.7f else 0.38f),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/** Riassunto degli ultimi 7 giorni: barre giornaliere, giorno della
 * settimana di ciascuna (per le etichette) e totale sessioni/minuti (per
 * riusare le stringhe weekly_summary_* già scritte per la Cronologia,
 * vedi HistoryScreen.kt — stessa finestra "ultimi 7 giorni", non settimana
 * di calendario). */
private data class WeekSummary(
    val dailyMinutes: List<Int>,
    val weekdayIndices: List<Int>,
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

    val dailyMinutes = mutableListOf<Int>()
    val weekdayIndices = mutableListOf<Int>()
    var totalSessions = 0
    var totalMinutes = 0
    repeat(7) {
        val day = cursor.timeInMillis
        dailyMinutes += minutesByDay[day] ?: 0
        weekdayIndices += cursor.get(Calendar.DAY_OF_WEEK) - 1
        totalSessions += sessionsByDay[day] ?: 0
        totalMinutes += minutesByDay[day] ?: 0
        cursor.add(Calendar.DATE, 1)
    }
    return WeekSummary(dailyMinutes, weekdayIndices, totalSessions, totalMinutes)
}

@Composable
private fun weeklySummaryText(summary: WeekSummary): String = when {
    summary.totalSessions == 0 -> stringResource(R.string.weekly_summary_none)
    summary.totalSessions == 1 -> stringResource(R.string.weekly_summary_one, formatMinutes(summary.totalMinutes))
    else -> stringResource(R.string.weekly_summary_many, summary.totalSessions, formatMinutes(summary.totalMinutes))
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
