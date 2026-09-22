package com.calmotter.app.ui.screens

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.ComponentName
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calmotter.app.GroupPauseRecipe
import com.calmotter.app.R
import com.calmotter.app.bluetooth.GroupPauseBluetoothHost
import com.calmotter.app.bluetooth.bluetoothAdapterOrNull
import com.calmotter.app.bluetooth.groupPauseLobbyNameMarker
import com.calmotter.app.bluetooth.localBluetoothDisplayName
import com.calmotter.app.encode
import com.calmotter.app.groupPauseBluetoothRuntimePermissions
import com.calmotter.app.hasGroupPauseBluetoothPermissions
import com.calmotter.app.nfc.GroupPauseHceService
import com.calmotter.app.ui.mascot.OtterZenMark
import com.calmotter.app.ui.mascot.OtterSatelliteMark
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Lobby dal vivo lato host (Fase 2, vedi specs/group-pause/design.md):
 * un'unica schermata dall'inizio alla fine, niente pagine intermedie per
 * permessi/Bluetooth spento — un avviso gentile **in fondo, sotto ai
 * bottoni** (tocca per concedere/attivare) e il bottone "Iniziamo"
 * semplicemente disabilitato finché non c'è almeno un partecipante. L'NFC
 * (se il dispositivo lo supporta) si attiva da solo entrando qui, nessun
 * interruttore da accendere a parte. Chi preferisce QR/codice può tornare a
 * [GroupPauseQrDelayScreen] tramite [onWantCodeInstead].
 *
 * Redesign sul mockup "Time Together - Host Lobby" (progetto Stitch
 * 3158702940609906617): via la card tinta di fondo (la schermata sta ora
 * sullo sfondo velato piatto, come [GroupPauseChooserScreen] e la Home —
 * niente più contorno rettangolare a delimitare "qui c'è la lobby"), il
 * selettore di durata scorre come le pillole della Home invece di andare a
 * capo (vedi [ScrollableMinutePillRow] in GroupPauseHostScreen.kt), e
 * l'otter siede dentro lo stesso stagno a dischi concentrici del resto
 * dell'app (vedi [ParticipantRing]).
 *
 * Include anche il selettore di durata (prima un passo separato,
 * [GroupPauseHostScreen] ora entra qui direttamente) — [initialDurationMinutes]
 * è solo il valore di partenza (quello scelto in Home), la selezione vera è
 * stato locale e resta modificabile per tutta la permanenza in lobby: non
 * ha senso bloccarla dopo un certo punto, dato che l'host non ha ancora
 * comunicato nulla a nessuno finché non tocca "Iniziamo". Il cambio viene
 * comunque propagato a [GroupPauseBluetoothHost.updateDuration] così i
 * prossimi partecipanti che si collegano leggono il valore aggiornato — chi
 * si è già collegato prima del cambio ha visto il valore precedente
 * nell'anteprima, ma la durata *effettiva* resta comunque quella
 * dell'ultima ricetta trasmessa a "Iniziamo".
 */
@Composable
fun GroupPauseBluetoothLobbyHostScreen(
    initialDurationMinutes: Int,
    onRecipeReady: (GroupPauseRecipe, companions: List<String>) -> Unit,
    onWantCodeInstead: (durationMinutes: Int) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    var durationMinutes by remember { mutableIntStateOf(initialDurationMinutes) }
    var hasPermissions by remember { mutableStateOf(hasGroupPauseBluetoothPermissions(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results -> hasPermissions = results.values.all { it } }

    val adapter = remember { bluetoothAdapterOrNull(context) }
    var bluetoothEnabled by remember { mutableStateOf(adapter?.isEnabled == true) }
    val enableBtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { bluetoothEnabled = adapter?.isEnabled == true }

    var discoverableRequested by remember { mutableStateOf(false) }
    val discoverableLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* risultato ignorato: se rifiutata, host.start() gira comunque — solo meno visibile */ }

    val allReady = hasPermissions && bluetoothEnabled
    val nfcAdapter = remember { NfcAdapter.getDefaultAdapter(context) }
    val nfcAvailable = remember { nfcAdapter != null }
    val groupTag = remember { Random.nextInt(0, 65536) }
    val markerName = remember(groupTag) { groupPauseLobbyNameMarker(groupTag) }
    val host = remember { GroupPauseBluetoothHost(context) }
    // Non `adapter.name`: durante la lobby vale "CalmOtter-<tag>", riscritto
    // apposta per il discovery. Serve il nome vero del telefono.
    val hostName = remember { localBluetoothDisplayName(context) }

    // L'NFC è pensato per l'incontro di due persone: dopo il tap non ha
    // senso chiedere ancora di toccare "Iniziamo" a mano. `onTapRead` (vedi
    // GroupPauseHceService.kt) segnala solo che il marker è stato letto,
    // prima che la connessione Bluetooth che segue sia completa — non basta
    // da solo per avviare, va combinato con la comparsa del partecipante in
    // participantNames (vedi il LaunchedEffect più sotto e
    // broadcastRecipeAndClose in GroupPauseBluetoothHost.kt, che consegna la
    // ricetta solo a chi è già connesso).
    var nfcTapDetected by remember { mutableStateOf(false) }

    // Propaga ogni cambio del selettore all'host già in ascolto (se lo è
    // già — altrimenti è un no-op ininfluente, perché start() qui sotto
    // legge comunque durationMinutes al momento in cui gira davvero).
    LaunchedEffect(durationMinutes) { host.updateDuration(durationMinutes) }

    // Non appena permessi+Bluetooth sono pronti: chiede la visibilità una
    // sola volta (discoverableRequested) e avvia davvero la lobby — nessuna
    // schermata dedicata per questi due passi, solo l'avviso gentile sopra
    // al bottone finché non sono soddisfatti.
    DisposableEffect(allReady) {
        if (allReady) {
            if (!discoverableRequested) {
                discoverableRequested = true
                val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                    .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
                discoverableLauncher.launch(intent)
            }
            host.start(markerName, hostName, durationMinutes)
            if (nfcAvailable) {
                GroupPauseHceService.pendingMarker = markerName
                // Senza questo, un tap NFC apriva il selettore di sistema
                // "Completa azione con" invece di rispondere subito —
                // segnalato, riprodotto su un Galaxy S22. L'AID dichiarato in
                // apduservice.xml è categoria "other" (non "payment"): per
                // quella categoria Android instrada un tap verso il servizio
                // preferito solo se qualcuno lo dichiara esplicitamente
                // tale mentre è in primo piano — altrimenti, con più di un
                // gestore possibile per lo stesso AID (anche solo il nostro
                // servizio più un gestore di sistema/OEM), chiede all'utente
                // ogni volta. `setPreferredService`/`unsetPreferredService`
                // vogliono l'Activity, non un Context qualunque: questa
                // composable vive sempre dentro una, come già assunto altrove
                // in questo file (vedi `activity` in BlockScreen.kt per lo
                // stesso pattern).
                nfcAdapter?.let {
                    runCatching {
                        CardEmulation.getInstance(it).setPreferredService(
                            context as Activity,
                            ComponentName(context, GroupPauseHceService::class.java),
                        )
                    }
                }
                // processCommandApdu() gira sul thread NFC del sistema, non
                // su quello principale: mutableStateOf va toccato dal main
                // thread, da cui il post esplicito.
                val mainHandler = Handler(Looper.getMainLooper())
                GroupPauseHceService.onTapRead = { mainHandler.post { nfcTapDetected = true } }
            }
        }
        onDispose {
            host.stop()
            GroupPauseHceService.pendingMarker = null
            GroupPauseHceService.onTapRead = null
            if (nfcAvailable) {
                nfcAdapter?.let {
                    runCatching { CardEmulation.getInstance(it).unsetPreferredService(context as Activity) }
                }
            }
        }
    }

    val participantNames = host.participantNames

    // Estratta dal Button "Iniziamo" così sia l'avvio manuale che quello
    // automatico via NFC (vedi il LaunchedEffect subito sotto) condividano
    // esattamente la stessa logica.
    fun startSession() {
        val recipe = GroupPauseRecipe(
            durationMinutes = durationMinutes,
            startAtEpochMillis = System.currentTimeMillis() + 5_000L,
            groupTag = groupTag,
        )
        // Copiati prima di broadcastRecipeAndClose(), che chiude
        // le connessioni: participantNames è la lista viva della
        // lobby, e dopo la chiusura non è più ciò che si vuole
        // registrare.
        val companions = participantNames.toList()
        host.broadcastRecipeAndClose(recipe.encode())
        onRecipeReady(recipe, companions)
    }

    // Un tap letto da solo non basta: prova solo che il marker NFC è stato
    // scambiato, non che l'altro telefono si sia già connesso via Bluetooth
    // (che arriva qualche istante dopo, vedi il commento su nfcTapDetected
    // più sopra). Si aspetta quindi la comparsa in participantNames prima di
    // avviare davvero, e si azzera il flag subito dopo per non riavviare a
    // ogni variazione successiva (es. un secondo tap o un altro
    // partecipante che si aggiunge più tardi).
    LaunchedEffect(nfcTapDetected, participantNames.size) {
        if (nfcTapDetected && participantNames.isNotEmpty()) {
            nfcTapDetected = false
            startSession()
        }
    }

    // Bottoni ancorati al fondo pagina, su richiesta esplicita: il resto del
    // contenuto (anello, titolo, durata, "preferisci un codice") vive in una
    // Column interna con weight(1f) e la stessa `Arrangement.Center` che
    // CalmScreenColumn usava di default per tutto — così resta centrato
    // *nello spazio sopra i bottoni*, invece che nella pagina intera, mentre
    // i bottoni (e il banner sotto di loro, invariato) restano l'ultima cosa
    // in basso indipendentemente da quanto contenuto c'è sopra.
    CalmScreenColumn(contentPadding = PaddingValues(32.dp), verticalArrangement = Arrangement.Top) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            ParticipantRing(participantNames = participantNames, ready = allReady)

            // **Titolo e sottotitolo non raccontano più lo stato dei permessi.**
            // Prima swappavano su "Quasi pronti" / "Ancora un passaggio..." a
            // permessi mancanti — testo scritto per quando questi due passi
            // erano ancora una schermata a sé (vedi "No more dedicated
            // permission screens" in specs/group-pause/design.md). Da quando
            // sono diventati un avviso inline, quello swap ripeteva la stessa
            // notizia due volte: il titolo diceva "manca qualcosa" e il banner,
            // subito sotto ai bottoni ora, diceva *cosa* e *come* rimediare.
            // Il titolo resta quindi sempre quello vero — chi c'è, o chi si
            // aspetta — esattamente come nel mockup, che non ha uno stato
            // "permessi mancanti" testuale a parte.
            Text(
                text = lobbyTitleFor(participantNames),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(
                    if (participantNames.isEmpty()) R.string.group_pause_lobby_waiting_subtitle
                    else R.string.group_pause_lobby_ready_subtitle
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp)
            )

            // Il selettore di durata prima viveva su un passo separato prima
            // della lobby (GroupPauseSetupScreen, rimosso): incorporarlo qui
            // toglie uno schermo intero dal percorso di creazione, e non c'è
            // motivo per bloccarlo mentre si aspetta — l'host non comunica
            // nulla a nessuno finché non tocca "Iniziamo" (vedi il commento di
            // classe di questo file). Scorre invece di andare a capo — vedi
            // [ScrollableMinutePillRow] in GroupPauseHostScreen.kt.
            SetupLabel(stringResource(R.string.group_pause_duration_label), topPadding = 20.dp)
            ScrollableMinutePillRow(
                options = DURATION_OPTIONS,
                selected = durationMinutes,
                onSelect = { durationMinutes = it },
                labelFor = { minutesLabel(it) },
            )

            // Riprovato in stile link nudo (come SessionsSummaryLink in
            // HomeSummary.kt) su segnalazione — il contenitore tinto
            // distraeva troppo per essere solo l'uscita di riserva della
            // lobby. Se dovesse tornare a perdersi fra gli altri testi della
            // schermata, [CalmSecondaryButton] è il trattamento già provato
            // in precedenza per il motivo opposto.
            Row(
                modifier = Modifier
                    .padding(top = 20.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = { onWantCodeInstead(durationMinutes) })
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.group_pause_prefer_code_link),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "›",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }

        // A tutta larghezza, uno sotto l'altro invece che affiancati, ancorati
        // al fondo pagina — su richiesta esplicita, applicata qui e
        // all'onboarding (vedi OnboardingScreen.kt). L'ordine è stato
        // invertito su richiesta successiva: **l'azione primaria (Iniziamo)
        // è l'ultima**, non la prima — Cancel sopra, Iniziamo sotto. Altezza
        // 48.dp esplicita su entrambi: il default M3 (`ButtonDefaults.MinHeight`)
        // è 40.dp, sotto il target minimo di tocco raccomandato (48dp,
        // Material Design/WCAG 2.5.5) — misurato sull'emulatore prima della
        // modifica (120px / density 3.0 = 40dp, non un'approssimazione).
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // `Arrangement.spacedBy` e non `.padding(top = 8.dp)` sul
            // secondo bottone: applicato *dopo* `.height(48.dp)` nella catena
            // di modifier, il padding veniva "mangiato" dentro l'altezza già
            // fissata invece di aggiungersi sopra — misurato 40dp invece di
            // 48dp sull'emulatore. Lo spazio fra i due va sulla Column.
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text(stringResource(android.R.string.cancel))
            }
            Button(
                onClick = { startSession() },
                enabled = participantNames.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text(stringResource(R.string.group_pause_start_button))
            }
        }

        // **Sotto ai bottoni, non sopra.** Prima stava fra il selettore di
        // durata e "Preferisci un codice o un QR?", in mezzo al percorso che
        // chiunque segue per creare la pausa — segnalato: si mischiava al
        // "flow classico". Qui è chiaramente un avviso a parte, non un passo
        // del percorso: chi ha già tutto pronto non lo vede nemmeno.
        if (!allReady) {
            GentleBanner(
                text = stringResource(
                    if (!hasPermissions) R.string.group_pause_bt_permission_notice
                    else R.string.group_pause_bt_gentle_notice
                ),
                actionLabel = stringResource(
                    if (!hasPermissions) R.string.group_pause_bt_permission_action
                    else R.string.group_pause_bt_gentle_action
                ),
                onAction = {
                    if (!hasPermissions) {
                        permissionLauncher.launch(groupPauseBluetoothRuntimePermissions())
                    } else {
                        enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                    }
                },
            )
        }
    }
}

@Composable
private fun lobbyTitleFor(participantNames: List<String>): String = when (participantNames.size) {
    0 -> stringResource(R.string.group_pause_lobby_waiting_title)
    1 -> stringResource(R.string.group_pause_lobby_with_one, participantNames[0])
    2 -> stringResource(R.string.group_pause_lobby_with_two, participantNames[0], participantNames[1])
    else -> pluralStringResource(
        R.plurals.group_pause_lobby_with_many,
        participantNames.size - 1,
        participantNames[0],
        participantNames.size - 1,
    )
}

/**
 * L'otter centrale che aspetta, dentro lo stesso stagno a dischi concentrici
 * del resto dell'app — non era così: fino a ieri c'era un solo anello
 * sottile, e segnalato che l'otter non risultava "circondato dai cerchi"
 * come nel mockup ("Time Together - Host Lobby", progetto Stitch
 * 3158702940609906617, che stratifica un anello di contorno, uno tratteggiato
 * rotante, un alone sfocato e un disco chiaro attorno alla mascotte).
 *
 * Qui la rotazione e il "ping" del mockup non ci sono: **niente animazione
 * perpetua**, la stessa scelta già presa per [TandemPawsMedallion] in
 * GroupPauseChooserScreen.kt e per lo stagno fermo della Home
 * (`PondStill`/`AmbientRipples` in MainScreen.kt) — un'animazione senza fine
 * accanto a un bottone "Iniziamo" tirerebbe l'occhio via da quello, e qui non
 * ci sarebbe nemmeno un momento in cui fermarla, dato che questa schermata
 * può restare aperta indefinitamente in attesa di qualcuno. I dischi restano
 * quindi fermi; sono i colori dello stagno della Home — `tertiary` per i
 * dischi, `surfaceBright` per quello chiaro — non `ringColor` (`primary`),
 * che resta riservato all'anello esterno perché continui a dire lo stato.
 *
 * **L'anello esterno mantiene la sua logica invariata**: tratteggiato
 * mentre non c'è nessuno, pieno e più marcato appena si aggiunge il primo
 * partecipante, appena accennato finché manca un permesso o il Bluetooth.
 * Quella è l'unica cosa che questo disegno deve ancora dire — il resto
 * (permessi, Bluetooth) lo dice ormai il banner sotto ai bottoni, non più il
 * titolo sopra (vedi il commento di classe di questo file).
 *
 * Un otter satellite per ciascun partecipante collegato, agganciato appena
 * dentro l'anello esterno. Fino a 6 satelliti mostrati esplicitamente: oltre,
 * il conteggio resta comunque leggibile dal testo sopra ([lobbyTitleFor]).
 */
@Composable
private fun ParticipantRing(participantNames: List<String>, ready: Boolean) {
    val ringColor = MaterialTheme.colorScheme.primary
    val pondColor = MaterialTheme.colorScheme.tertiary
    val pondBright = MaterialTheme.colorScheme.surfaceBright
    val hasParticipants = participantNames.isNotEmpty()

    Box(modifier = Modifier.size(200.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(180.dp)) {
            val strokeWidth = 2.dp.toPx()

            // Lo stagno, fermo, disegnato per primo così l'anello esterno
            // (sotto) resti il bordo nitido sopra di esso. Raggi scelti
            // perché il disco chiaro combaci con l'OtterZenMark di 110dp
            // qui sotto — stessa proporzione con cui [TandemPawsMedallion]
            // fa combaciare il proprio disco chiaro con TogetherMark.
            drawCircle(color = pondColor, radius = 82.dp.toPx(), alpha = 0.14f)
            drawCircle(color = pondColor, radius = 68.dp.toPx(), alpha = 0.26f)
            drawCircle(color = pondBright, radius = 55.dp.toPx())

            when {
                // Il tratteggio dice "sto aspettando qualcuno": va mostrato
                // solo quando la lobby sta davvero ascoltando. Finché manca un
                // permesso o il Bluetooth non c'è nessuna attesa in corso, e
                // l'anello resta una traccia neutra e più tenue.
                !ready -> drawCircle(
                    color = ringColor.copy(alpha = 0.12f),
                    style = Stroke(width = strokeWidth),
                )
                hasParticipants -> drawCircle(
                    color = ringColor.copy(alpha = 0.22f),
                    style = Stroke(width = strokeWidth),
                )
                else -> drawCircle(
                    color = ringColor.copy(alpha = 0.22f),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round, pathEffect = dashedEffect()),
                )
            }
        }

        OtterZenMark(markSize = 110.dp)

        val shown = participantNames.take(6)
        val radius = 84.dp
        shown.forEachIndexed { index, _ ->
            val angleDeg = -90.0 + index * (360.0 / shown.size)
            val angleRad = Math.toRadians(angleDeg)
            val x = radius * cos(angleRad).toFloat()
            val y = radius * sin(angleRad).toFloat()
            Box(modifier = Modifier.offset(x = x, y = y)) {
                OtterSatelliteMark(markSize = 34.dp)
            }
        }
    }
}

private fun dashedEffect() = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))

/** Avviso non bloccante — sostituisce la vecchia schermata dedicata "Bluetooth spento"/permessi. */
@Composable
private fun GentleBanner(text: String, actionLabel: String, onAction: () -> Unit) {
    Surface(
        onClick = onAction,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = actionLabel,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}
