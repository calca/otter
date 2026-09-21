package com.calmotter.app.ui.screens

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.nfc.NfcAdapter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
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
    val nfcAvailable = remember { NfcAdapter.getDefaultAdapter(context) != null }
    val groupTag = remember { Random.nextInt(0, 65536) }
    val markerName = remember(groupTag) { groupPauseLobbyNameMarker(groupTag) }
    val host = remember { GroupPauseBluetoothHost(context) }
    // Non `adapter.name`: durante la lobby vale "CalmOtter-<tag>", riscritto
    // apposta per il discovery. Serve il nome vero del telefono.
    val hostName = remember { localBluetoothDisplayName(context) }

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
            if (nfcAvailable) GroupPauseHceService.pendingMarker = markerName
        }
        onDispose {
            host.stop()
            GroupPauseHceService.pendingMarker = null
        }
    }

    val participantNames = host.participantNames

    CalmScreenColumn(contentPadding = PaddingValues(32.dp)) {
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

        // L'unica via d'uscita dalla lobby se l'altro telefono non si
        // fa trovare via Bluetooth, quindi l'unica CTA secondaria di
        // questa schermata a meritare il contenitore tinto (vedi
        // [CalmSecondaryButton]): da link nudo si perdeva fra i testi.
        CalmSecondaryButton(
            text = stringResource(R.string.group_pause_prefer_code_link),
            onClick = { onWantCodeInstead(durationMinutes) },
            modifier = Modifier.padding(top = 20.dp),
        )

        Row(modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            ) {
                Text(stringResource(android.R.string.cancel))
            }
            Button(
                onClick = {
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
                },
                enabled = participantNames.isNotEmpty(),
                modifier = Modifier.weight(1f)
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
    else -> stringResource(R.string.group_pause_lobby_with_many, participantNames[0], participantNames.size - 1)
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
