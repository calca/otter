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
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.State
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect

private sealed class JoinLobbyState {
    data object NfcHero : JoinLobbyState()
    data object SearchHero : JoinLobbyState()
    data object Connecting : JoinLobbyState()
    data object WaitingForHost : JoinLobbyState()
    data class Error(val message: String) : JoinLobbyState()
}

/**
 * Lobby dal vivo lato joiner (Fase 2, vedi specs/group-pause/design.md):
 * l'NFC è il gesto predefinito quando disponibile ("avvicina i telefoni"),
 * con la ricerca Bluetooth manuale e il codice/QR come ripieghi secondari —
 * non tre opzioni alla pari come nella prima versione. Stesso avviso
 * gentile permessi/Bluetooth di [GroupPauseBluetoothLobbyHostScreen], nessuna
 * schermata dedicata per quei due passi.
 *
 * Stessi due ritocchi fatti lì, per la stessa ragione — vedi il commento di
 * classe di [GroupPauseBluetoothLobbyHostScreen]: via la card tinta di
 * fondo (sfondo piatto come il resto del flusso), e titolo/sottotitolo che
 * non swappano più su un testo "permessi mancanti" — quello lo dice ormai
 * solo [GentleReadinessBanner], e dirlo due volte era il residuo di quando
 * questi due passi erano ancora una schermata a sé.
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

    var state by remember { mutableStateOf(if (nfcAvailable) JoinLobbyState.NfcHero else JoinLobbyState.SearchHero) }
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

    // L'NFC/il discovery partono solo quando permessi+Bluetooth sono pronti
    // (allReady) — prima di allora la schermata mostra comunque l'illustrazione
    // e l'avviso gentile, ma nessuna chiamata Bluetooth/NFC reale.
    DisposableEffect(state, allReady) {
        if (allReady && state == JoinLobbyState.NfcHero) {
            nfcReader.start { marker ->
                mainHandler.post {
                    state = JoinLobbyState.Connecting
                    join.startDiscovery(autoConnectToNameMarker = marker, onAutoMatch = { device -> connect(device) })
                }
            }
        } else if (allReady && state == JoinLobbyState.SearchHero) {
            join.startDiscovery()
        }
        onDispose {
            nfcReader.stop()
            join.stop()
        }
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
                JoinLobbyState.NfcHero -> {
                    OtterTapMark(markSize = 88.dp)
                    // Il testo non racconta più lo stato dei permessi (vedi il
                    // commento di classe): resta sempre "Avvicinati a chi ti
                    // aspetta", vero o no che il lettore NFC sia già partito
                    // — [GentleReadinessBanner] qui sotto dice cosa manca.
                    Text(
                        text = stringResource(R.string.group_pause_join_nfc_title),
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 14.dp)
                    )
                    Text(
                        text = stringResource(R.string.group_pause_join_nfc_subtitle),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    if (!allReady) {
                        GentleReadinessBanner(hasPermissions, onReadinessAction)
                    }
                    // Lo switch NFC↔ricerca resta un link di testo: cambia
                    // solo il modo di cercare, e restando in questa lobby.
                    // Il codice/QR invece è l'uscita di sicurezza quando il
                    // Bluetooth non basta — una sola CTA secondaria per
                    // schermata prende il contenitore tinto, altrimenti si
                    // torna al punto di partenza, con tutto uguale.
                    TextButton(onClick = { state = JoinLobbyState.SearchHero }, modifier = Modifier.padding(top = 20.dp)) {
                        Text(stringResource(R.string.group_pause_join_search_link))
                    }
                    CalmSecondaryButton(
                        text = stringResource(R.string.group_pause_join_code_link),
                        onClick = onWantCodeInstead,
                    )
                }
                JoinLobbyState.SearchHero -> {
                    SearchingIllustration(hasResults = join.discovered.isNotEmpty(), searching = allReady)
                    Text(
                        text = stringResource(R.string.group_pause_join_search_hero_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 14.dp)
                    )
                    if (join.discovered.isEmpty()) {
                        Text(
                            text = stringResource(R.string.group_pause_join_searching),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    } else {
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
                    if (!allReady) {
                        GentleReadinessBanner(hasPermissions, onReadinessAction)
                    }
                    if (nfcAvailable) {
                        TextButton(onClick = { state = JoinLobbyState.NfcHero }, modifier = Modifier.padding(top = 16.dp)) {
                            Text(stringResource(R.string.group_pause_join_nfc_title))
                        }
                    }
                    // Stessa CTA dello stato NfcHero qui sopra, stesso
                    // trattamento: è la stessa schermata in due stati, non due.
                    CalmSecondaryButton(
                        text = stringResource(R.string.group_pause_join_code_link),
                        onClick = onWantCodeInstead,
                    )
                }
                JoinLobbyState.Connecting -> {
                    OtterZenMark(markSize = 88.dp)
                    Text(
                        text = stringResource(R.string.group_pause_join_connecting),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 14.dp)
                    )
                }
                JoinLobbyState.WaitingForHost -> {
                    OtterZenMark(markSize = 88.dp)
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
                    Button(onClick = { state = if (nfcAvailable) JoinLobbyState.NfcHero else JoinLobbyState.SearchHero }) {
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
 * Otter al centro di anelli concentrici che si espandono e svaniscono in
 * loop mentre [hasResults] è falso — "sto cercando un amico", non un
 * dispositivo. Si ferma da sola (nessun anello) appena la lista smette di
 * essere vuota.
 */
@Composable
private fun SearchingIllustration(hasResults: Boolean, searching: Boolean) {
    Box(modifier = Modifier.size(140.dp), contentAlignment = Alignment.Center) {
        // Le onde che si espandono raccontano una ricerca in corso: vanno
        // mostrate solo quando la discovery Bluetooth sta davvero girando.
        // Con permessi o Bluetooth mancanti non parte nulla (vedi il
        // DisposableEffect su allReady), e animarle comunque sarebbe una
        // scansione finta.
        if (!hasResults && searching) {
            // **A scatti, non a ogni fotogramma** — segnalato ("la CPU
            // frulla" su questa schermata), stesso bug già trovato e
            // corretto sul puntino della pagina QR (vedi
            // specs/group-pause/design.md, "The pulse was implemented
            // wrong the first time"): `rememberInfiniteTransition().
            // animateFloat(...)` campionava a ogni fotogramma del display
            // (60-120Hz), e qui il costo era anche peggiore che lì — non
            // un singolo Box da 8dp letto in fase di disegno, ma un intero
            // Canvas che ridisegna tre anelli su un'area di 140dp, dentro
            // la composable stessa (`val t by ...` letto in composizione,
            // non in un `graphicsLayer`), quindi ogni scatto ricomponeva
            // anche il `Canvas`. `rememberSearchPulse()` sotto usa lo
            // stesso `withInfiniteAnimationFrameMillis` + `delay` a 10
            // passi al secondo già stabilito altrove nell'app — resta la
            // ricerca può durare minuti, quindi non c'è un momento in cui
            // fermarsi come per il puntino, ma il costo per fotogramma
            // scende comunque di un ordine di grandezza.
            val t by rememberSearchPulse()
            val ringColor = MaterialTheme.colorScheme.primary
            Canvas(modifier = Modifier.size(140.dp)) {
                listOf(0f, 0.33f, 0.66f).forEach { phase ->
                    val localT = (t + phase) % 1f
                    drawCircle(
                        color = ringColor,
                        radius = size.minDimension / 5f + localT * size.minDimension / 2.6f,
                        alpha = (1f - localT) * 0.4f,
                        style = Stroke(width = 1.5.dp.toPx()),
                    )
                }
            }
        }
        OtterZenMark(markSize = 76.dp)
    }
}

/**
 * Frazione 0f→1f a scatti (10 al secondo) invece che a ogni fotogramma —
 * vedi il commento al punto d'uso in [SearchingIllustration] per la misura
 * che ha portato a scriverla così, e [rememberPulseAlpha] in
 * GroupPauseCountdownScreen.kt per lo stesso idioma applicato allo stesso
 * bug altrove in questo flusso.
 */
@Composable
private fun rememberSearchPulse(periodMillis: Int = 1800, stepsPerSecond: Int = 10): State<Float> {
    val fractionState = remember { mutableFloatStateOf(0f) }
    var fraction by fractionState
    LaunchedEffect(periodMillis, stepsPerSecond) {
        val steps = (periodMillis / 1000f * stepsPerSecond).toInt().coerceAtLeast(1)
        val stepMillis = (1000f / stepsPerSecond).toLong()
        while (true) {
            withInfiniteAnimationFrameMillis { now ->
                val step = ((now % periodMillis) / periodMillis.toFloat() * steps).toInt()
                val next = step / steps.toFloat()
                if (next != fraction) fraction = next
            }
            delay(stepMillis)
        }
    }
    return fractionState
}

