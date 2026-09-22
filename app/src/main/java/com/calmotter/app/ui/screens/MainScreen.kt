package com.calmotter.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calmotter.app.BuildConfig
import com.calmotter.app.R
import com.calmotter.app.SessionHistoryManager
import com.calmotter.app.SessionManager
import com.calmotter.app.SessionStreak
import com.calmotter.app.ui.mascot.OtterZenMark
import com.calmotter.app.ui.mascot.TogetherMark

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
 *
 * TODO.md "7": questo file conteneva anche "lo stagno" (otter+anello+
 * increspature, ora in HomePond.kt) e il riepilogo settimanale (ora in
 * HomeSummary.kt) — 1171 righe, la più grande del progetto, senza un
 * difetto specifico legato alla dimensione ma segnalata comunque. Stesso
 * package, nessun comportamento cambiato: solo dove il codice vive.
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
