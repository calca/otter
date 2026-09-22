package com.calmotter.app.ui.screens

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calmotter.app.GroupPauseRecipe
import com.calmotter.app.R
import com.calmotter.app.bluetooth.GroupPauseBluetoothJoin
import com.calmotter.app.bluetooth.bluetoothAdapterOrNull
import com.calmotter.app.bluetooth.localBluetoothDisplayName
import com.calmotter.app.decodeGroupPauseRecipe
import com.calmotter.app.groupPauseBluetoothRuntimePermissions
import com.calmotter.app.hasGroupPauseBluetoothPermissions
import com.calmotter.app.nfc.GroupPauseNfcReader
import com.calmotter.app.ui.mascot.OtterZenMark
import com.calmotter.app.ui.mascot.OtterTapMark
import androidx.compose.runtime.LaunchedEffect

private sealed class JoinLobbyState {
    data object Listening : JoinLobbyState()
    data object Connecting : JoinLobbyState()
    data object WaitingForHost : JoinLobbyState()
    data class Error(val message: String) : JoinLobbyState()
}

/**
 * Lobby dal vivo lato joiner (Fase 2, vedi specs/group-pause/design.md):
 * l'NFC (quando disponibile) e la ricerca Bluetooth girano **insieme**,
 * sulla stessa schermata, non più come due stati alternativi
 * (`NfcHero`/`SearchHero`) fra cui passare con un tap su un link — segnalato
 * come "doppio step" da evitare. Il codice/QR resta il ripiego secondario,
 * ora un link nudo ([CalmLinkRow]) invece di una pastiglia tinta, per
 * alleggerire una schermata che con la fusione ha comunque più contenuto
 * (l'elenco Bluetooth può comparire in qualunque momento, non solo dopo
 * uno switch esplicito).
 *
 * `join.startDiscovery()` parte sempre appena pronta (`allReady`), non più
 * solo nello stato "ricerca": popola `join.discovered` indipendentemente
 * da NFC. Il marker NFC letto da [GroupPauseNfcReader] non riavvia il
 * discovery con `autoConnectToNameMarker` (che richiederebbe fermare e
 * ripartire, con un secondo `BroadcastReceiver` da gestire): resta in
 * `pendingNfcMarker` finché lo stesso nome non compare fra i dispositivi
 * già trovati dalla ricerca già in corso, poi si connette da sé — vedi il
 * `LaunchedEffect` più sotto.
 *
 * Stessi due ritocchi già fatti sulla lobby host, per la stessa ragione —
 * vedi il commento di classe di [GroupPauseBluetoothLobbyHostScreen]: via
 * la card tinta di fondo (sfondo piatto come il resto del flusso), e
 * titolo/sottotitolo che non swappano più su un testo "permessi mancanti"
 * — quello lo dice ormai solo [GentleReadinessBanner], e dirlo due volte
 * era il residuo di quando questi due passi erano ancora una schermata a
 * sé.
 */
@Composable
fun GroupPauseBluetoothLobbyJoinScreen(
    onRecipeReady: (GroupPauseRecipe, hostName: String?) -> Unit,
    onWantCodeInstead: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as Activity
    var hasPermissions by remember { mutableStateOf(hasGroupPauseBluetoothPermissions(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results -> hasPermissions = results.values.all { it } }

    val adapter = remember { bluetoothAdapterOrNull(context) }
    var bluetoothEnabled by remember { mutableStateOf(adapter?.isEnabled == true) }
    val enableBtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { bluetoothEnabled = adapter?.isEnabled == true }

    val allReady = hasPermissions && bluetoothEnabled
    val nfcAvailable = remember { NfcAdapter.getDefaultAdapter(context) != null }
    val localName = remember { localBluetoothDisplayName(context) }
    val join = remember { GroupPauseBluetoothJoin(context) }
    val nfcReader = remember { GroupPauseNfcReader(activity) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    var state by remember { mutableStateOf<JoinLobbyState>(JoinLobbyState.Listening) }
    // Nome annunciato letto via NFC, in attesa che la ricerca Bluetooth già
    // in corso lo trovi — vedi il commento di classe per perché non si
    // riavvia startDiscovery() con autoConnectToNameMarker.
    var pendingNfcMarker by remember { mutableStateOf<String?>(null) }
    // Popolati dal LOBBY: dell'host appena connessi. Restano null se all'altro
    // capo gira una versione che non lo invia — in quel caso la schermata
    // ricade sul vecchio "in attesa dell'host", senza rompersi.
    var hostName by remember { mutableStateOf<String?>(null) }
    var hostDurationMinutes by remember { mutableStateOf<Int?>(null) }
    val invalidCodeText = stringResource(R.string.group_pause_invalid_code)
    val connectionLostText = stringResource(R.string.group_pause_join_error)

    fun onRecipeCode(code: String) {
        val recipe = decodeGroupPauseRecipe(code)
        if (recipe != null) onRecipeReady(recipe, hostName) else state = JoinLobbyState.Error(invalidCodeText)
    }

    val onReadinessAction: () -> Unit = {
        if (!hasPermissions) {
            permissionLauncher.launch(groupPauseBluetoothRuntimePermissions())
        } else {
            enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
    }

    // Estratta dal `DisposableEffect` qui sotto perché anche il retry dopo
    // un errore deve poterla richiamare: con l'effetto ora keyato solo su
    // `allReady` (vedi il commento lì), un errore che riporta a `Listening`
    // non lo fa ripartire da sé — serve chiamarla a mano dal bottone di
    // retry, dopo aver ripulito quanto lasciato dal tentativo precedente
    // (vedi lì).
    fun startListening() {
        join.startDiscovery()
        if (nfcAvailable) {
            nfcReader.start { marker -> mainHandler.post { pendingNfcMarker = marker } }
        }
    }

    fun connect(device: BluetoothDevice) {
        state = JoinLobbyState.Connecting
        join.connectTo(
            device = device,
            localDisplayName = localName,
            onConnected = { mainHandler.post { state = JoinLobbyState.WaitingForHost } },
            onLobbyInfo = { name, minutes ->
                mainHandler.post {
                    hostName = name
                    hostDurationMinutes = minutes
                }
            },
            onRecipe = { code -> mainHandler.post { onRecipeCode(code) } },
            onError = { mainHandler.post { state = JoinLobbyState.Error(connectionLostText) } },
        )
    }

    // NFC e ricerca Bluetooth partono insieme quando permessi+Bluetooth sono
    // pronti (allReady) — prima di allora la schermata mostra comunque
    // l'illustrazione e l'avviso gentile, ma nessuna chiamata reale.
    //
    // **Bug corretto: era `DisposableEffect(state, allReady)`.** Segnalato
    // ("il Bluetooth non è stabile, si disconnette da solo"): tenere
    // `state` come chiave fa girare `onDispose` — quindi `join.stop()` —
    // a *ogni* cambio di stato, non solo quando si lascia davvero la
    // schermata. `connect()` (poco sotto) passa a `Connecting` e poi
    // chiama `join.connectTo(...)`, che lancia una coroutine *asincrona*
    // sullo scope di `join`; quel cambio di stato fa scattare l'effetto
    // con la chiave vecchia in disposizione nello stesso istante — una
    // corsa vera: se la connessione si stabilisce (o si è già stabilita,
    // caso NFC) prima che Compose finisca di ricomporre,
    // `join.stop()` chiude `socket`, cioè la connessione appena
    // riuscita, un attimo dopo che è nata. Da fuori si vede esattamente
    // come "provo a collegarmi e risulto subito disconnesso" — non un
    // problema del Bluetooth del dispositivo, un `stop()` chiamato sulla
    // connessione sbagliata.
    //
    // La lobby host non aveva questo bug: il suo `DisposableEffect` è già
    // keyato solo su `allReady` (vedi GroupPauseBluetoothLobbyHostScreen.kt),
    // mai su un proprio stato interno — stesso schema riapplicato qui.
    // `connectTo()` cancella già il discovery per conto proprio appena
    // parte (vedi GroupPauseBluetoothJoin.kt), quindi non c'è nulla da
    // fermare esplicitamente al cambio di stato: NFC e ricerca restano
    // vivi in sottofondo durante `Connecting`/`WaitingForHost` (innocuo:
    // il discovery a livello di adapter è già cancellato, il reader NFC
    // semplicemente non ha più nulla da fare) e vengono chiusi una volta
    // sola, quando si lascia davvero la lobby o `allReady` torna falso.
    DisposableEffect(allReady) {
        if (allReady) startListening()
        onDispose {
            nfcReader.stop()
            join.stop()
        }
    }

    // Appena il nome letto via NFC compare fra i dispositivi trovati dalla
    // ricerca (già in corso), ci si connette — senza aspettare che l'utente
    // tocchi nulla, stesso comportamento "automatico" di prima. `state` in
    // guardia: dopo la prima connessione (o un errore che fa tornare qui)
    // `pendingNfcMarker` potrebbe ancora valere, ma la lista continua ad
    // aggiornarsi solo mentre si è davvero in ascolto.
    LaunchedEffect(pendingNfcMarker, join.discovered.size) {
        val marker = pendingNfcMarker ?: return@LaunchedEffect
        if (state != JoinLobbyState.Listening) return@LaunchedEffect
        join.discovered.firstOrNull { it.name == marker }?.let { connect(it.device) }
    }

    // Cancel ancorato al fondo pagina, su richiesta esplicita — stesso
    // schema già applicato al resto del flusso "Tempo insieme" (vedi
    // GroupPauseBluetoothLobbyHostScreen.kt): il contenuto per-stato vive
    // in una Column interna con weight(1f) e la stessa `Arrangement.Center`
    // che CalmScreenColumn usava di default per tutto, Cancel resta
    // l'ultimo fratello non pesato.
    CalmScreenColumn(contentPadding = PaddingValues(32.dp), verticalArrangement = Arrangement.Top) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (val current = state) {
                JoinLobbyState.Listening -> {
                    val hasResults = join.discovered.isNotEmpty()
                    SearchingIllustration(hasResults = hasResults, searching = allReady) {
                        // L'icona resta un indizio di *come* cercare, non di
                        // *se* si sta cercando (quello lo dice l'anello/le
                        // onde) — tap se l'NFC c'è, altrimenti l'otter
                        // generico già usato altrove nel flusso.
                        if (nfcAvailable) OtterTapMark(markSize = 88.dp) else OtterZenMark(markSize = 88.dp)
                    }
                    // Il testo non racconta più lo stato dei permessi (vedi il
                    // commento di classe): [GentleReadinessBanner] qui sotto
                    // dice cosa manca. Titolo condizionato solo da nfcAvailable
                    // (fisso per tutta la visita: un adattatore NFC non compare
                    // o scompare mentre si guarda questa schermata).
                    Text(
                        text = stringResource(
                            if (nfcAvailable) R.string.group_pause_join_nfc_title
                            else R.string.group_pause_join_search_hero_title
                        ),
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 14.dp)
                    )
                    if (!hasResults) {
                        // Stesso sottotitolo per NFC e ricerca quando la lista
                        // è vuota: menziona entrambe le vie, non solo quella
                        // che questo device ha — chi non ha NFC non ha motivo
                        // di sapere che esiste.
                        Text(
                            text = stringResource(
                                if (nfcAvailable) R.string.group_pause_join_listening_subtitle
                                else R.string.group_pause_join_searching
                            ),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    } else {
                        // Prima l'elenco non spiegava cosa farne — segnalato
                        // insieme al sottotitolo NFC: chi ci arriva non sa se
                        // deve toccare un nome o aspettare altro. Un'unica
                        // frase basta per entrambe le vie (NFC/ricerca):
                        // arrivati qui la scelta è sempre "tocca il nome".
                        Text(
                            text = stringResource(R.string.group_pause_join_pick_hint),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            join.discovered.forEach { found ->
                                Surface(
                                    onClick = { connect(found.device) },
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                ) {
                                    Text(
                                        text = found.name,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                    // Segnalato: senza vedere il proprio nome Bluetooth, non
                    // c'è modo di controllare con chi sta aspettando (via
                    // messaggio, voce) che il nome giusto sia comparso
                    // dall'altra parte — soprattutto quando la lista mostra
                    // più di un dispositivo. `localName` è lo stesso valore
                    // già inviato nell'handshake (vedi `connect()` più sopra),
                    // non un valore diverso da tenere sincronizzato a mano.
                    Text(
                        text = stringResource(R.string.group_pause_join_your_name, localName),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 14.dp)
                    )
                    if (!allReady) {
                        GentleReadinessBanner(hasPermissions, onReadinessAction)
                    }
                    // Link nudo, non più pastiglia tinta — segnalato
                    // ("alleggerire la pagina"): fusa con l'ex NfcHero, questa
                    // schermata ha già l'elenco Bluetooth potenzialmente
                    // visibile, non solo titolo+sottotitolo. Stesso
                    // [CalmLinkRow] della lobby host.
                    CalmLinkRow(
                        text = stringResource(R.string.group_pause_join_code_link),
                        onClick = onWantCodeInstead,
                        modifier = Modifier.padding(top = 20.dp),
                    )
                }
                JoinLobbyState.Connecting -> {
                    OtterRingIllustration(dashed = false) { OtterZenMark(markSize = 88.dp) }
                    Text(
                        text = stringResource(R.string.group_pause_join_connecting),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 14.dp)
                    )
                }
                JoinLobbyState.WaitingForHost -> {
                    OtterRingIllustration(dashed = false) { OtterZenMark(markSize = 88.dp) }
                    // Chi ospita e per quanto, appena l'host lo comunica: è ciò a
                    // cui si sta dicendo di sì, e va detto prima che la pausa
                    // cominci, non quando è già cominciata.
                    val name = hostName
                    val minutes = hostDurationMinutes
                    if (name != null) {
                        Text(
                            text = name,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 14.dp)
                        )
                    }
                    if (minutes != null) {
                        Text(
                            text = stringResource(R.string.group_pause_join_lobby_duration, minutesLabel(minutes)),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.group_pause_join_waiting_host),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = if (name != null || minutes != null) 12.dp else 14.dp)
                    )
                }
                is JoinLobbyState.Error -> {
                    Text(
                        text = current.message,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                    Button(onClick = {
                        // Fa ripartire la ricerca da capo — l'effetto qui
                        // sopra non lo fa più da sé al rientro in Listening
                        // (vedi il suo commento). **Non** `join.stop()`
                        // prima: cancellerebbe per sempre `scope`, e con lui
                        // la capacità di questa stessa istanza di
                        // connettersi di nuovo — vedi il commento su
                        // `GroupPauseBluetoothJoin.stop()`.
                        // `startDiscovery()` è già sicura da richiamare più
                        // volte di fila (ripulisce il proprio
                        // `BroadcastReceiver` precedente da sé).
                        if (allReady) startListening()
                        state = JoinLobbyState.Listening
                    }) {
                        Text(stringResource(R.string.group_pause_join_retry_button))
                    }
                }
            }
        }

        // A tutta larghezza, ancorato al fondo pagina, 48.dp di altezza —
        // stesso standard applicato al resto dell'app (vedi
        // specs/onboarding-and-password/design.md, "CTA height made
        // explicit").
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp)
                .height(48.dp),
        ) {
            Text(stringResource(android.R.string.cancel))
        }
    }
}

@Composable
private fun GentleReadinessBanner(hasPermissions: Boolean, onAction: () -> Unit) {
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
                text = stringResource(
                    if (!hasPermissions) R.string.group_pause_bt_permission_notice
                    else R.string.group_pause_bt_gentle_notice
                ),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(
                    if (!hasPermissions) R.string.group_pause_bt_permission_action
                    else R.string.group_pause_bt_gentle_action
                ),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

/**
 * Otter al centro dello stesso specchio d'acqua del resto del flusso, con
 * le increspature che si espandono e svaniscono in loop mentre
 * [hasResults] è falso — "sto cercando un amico", non un dispositivo. Si
 * fermano da sole appena la lista smette di essere vuota. [otter] è un
 * parametro (non sempre `OtterZenMark`) da quando questa schermata fonde
 * NFC e ricerca: la mascotte cambia a seconda che l'NFC sia disponibile o
 * no, l'anello e le onde no.
 *
 * `size = 200.dp` (non il default 160dp di [OtterRingIllustration]) e
 * `pulsing`/le onde vere e proprie sono entrambi su richiesta esplicita
 * ("unificare la grafica" con la Home e con `TandemPawsMedallion" della
 * schermata di scelta — vedi `OtterRingIllustration`, e "non si capisce
 * che l'app sta cercando"): prima questa era l'unica illustrazione del
 * flusso ad avere già un'animazione propria (le onde, scritte a mano qui
 * dentro), ma un disegno più piccolo delle altre e con un pulse diverso da
 * quello della Home — due stagni diversi invece di uno solo disegnato in
 * due punti.
 */
@Composable
private fun SearchingIllustration(hasResults: Boolean, searching: Boolean, otter: @Composable () -> Unit) {
    OtterRingIllustration(
        dashed = !searching,
        size = 200.dp,
        // Le increspature raccontano una ricerca in corso: vanno mostrate
        // solo quando la discovery Bluetooth sta davvero girando. Con
        // permessi o Bluetooth mancanti non parte nulla (vedi il
        // DisposableEffect su allReady), e animarle comunque sarebbe una
        // scansione finta.
        pulsing = !hasResults && searching,
        otter = otter,
    )
}

