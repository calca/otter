package com.calmotter.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.annotation.VisibleForTesting
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.calmotter.app.GroupPauseRecipe
import com.calmotter.app.R
import com.calmotter.app.decodeGroupPauseRecipe
import com.calmotter.app.ui.mascot.OtterZenMark
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer

private enum class JoinMode { SCAN, MANUAL }

private sealed class JoinFlowStep {
    data object Live : JoinFlowStep()
    data object QrOrCode : JoinFlowStep()
    // Il nome dell'host arriva dal LOBBY: della lobby dal vivo; dal percorso
    // QR/codice non c'è (null), ed è il motivo per cui lì resta generico.
    data class Countdown(val recipe: GroupPauseRecipe, val hostName: String? = null) : JoinFlowStep()
}

/**
 * Unisciti al Tempo Insieme: la lobby dal vivo (Bluetooth+NFC — vedi
 * GroupPauseBluetoothLobbyJoinScreen) è il punto di ingresso predefinito;
 * chi preferisce un codice/QR può passare a [GroupPauseCodeEntryScreen]
 * tramite il link "Scansiona o inserisci un codice" dentro alla lobby
 * stessa (prima "Ho un codice o un QR" — poco intuitivo come link, non
 * descriveva un'azione). Stesso conto alla rovescia condiviso alla fine di
 * entrambi i percorsi.
 *
 * [JoinFlowStep.QrOrCode] è un ripiego raggiunto dalla lobby, non un passo
 * alla pari: il suo `onCancel` torna a [JoinFlowStep.Live] invece di uscire
 * dal flusso — segnalato ("il cancel di scan qr deve tornare alla pagina
 * di look"), perché usciva fino in fondo, allo stesso posto di "Annulla"
 * dalla lobby stessa, cancellando la distinzione fra le due uscite.
 */
@Composable
fun GroupPauseJoinScreen(
    onJoined: (durationMinutes: Int, companions: List<String>, groupTag: Int) -> Unit,
    onCancel: () -> Unit,
) {
    var step by remember { mutableStateOf<JoinFlowStep>(JoinFlowStep.Live) }

    when (val current = step) {
        JoinFlowStep.Live -> GroupPauseBluetoothLobbyJoinScreen(
            onRecipeReady = { recipe, hostName -> step = JoinFlowStep.Countdown(recipe, hostName) },
            onWantCodeInstead = { step = JoinFlowStep.QrOrCode },
            onCancel = onCancel,
        )
        JoinFlowStep.QrOrCode -> GroupPauseCodeEntryScreen(
            onRecipeReady = { recipe -> step = JoinFlowStep.Countdown(recipe) },
            // Torna alla lobby dal vivo ("Cerca un amico…"), non fuori dal
            // flusso — segnalato: "Annulla" dallo scanner QR portava fino in
            // fondo, allo stesso posto di "Annulla" dalla lobby stessa.
            // Questo è un ripiego raggiunto da un link dentro la lobby, non
            // un passo suo pari: uscirne riporta a dov'era chi ci è entrato,
            // non un passo più indietro di quello.
            onCancel = { step = JoinFlowStep.Live },
        )
        is JoinFlowStep.Countdown -> GroupPauseCountdownScreen(
            durationMinutes = current.recipe.durationMinutes,
            startAtEpochMillis = current.recipe.startAtEpochMillis,
            onReady = { onJoined(current.recipe.durationMinutes, listOfNotNull(current.hostName), current.recipe.groupTag) },
            onCancel = onCancel,
        )
    }
}

/**
 * Ripiego QR/codice manuale, raggiunto dal link "Scansiona o inserisci un
 * codice" dentro alla lobby dal vivo. La modalità Scansiona è ora **a
 * schermo intero** (richiesto esplicitamente): la fotocamera è l'unico
 * contenuto reale di quella schermata, quindi non ha senso comprimerla in
 * un riquadro dentro una colonna con padding come il resto dell'app — vedi
 * [ScanFullScreen]. La modalità manuale, che non ha nulla da mostrare a
 * piena pagina (solo un campo di testo), resta sul layout standard
 * [CalmScreenColumn].
 */
@Composable
// `internal`, non `private`: CtaButtonInvariantsTest (TODO.md "2.2") la
// compone direttamente per proteggere l'ordine Cancel/Scan appena
// corretto ("Scan dovrebbe essere l'ultimo bottone") da una regressione
// silenziosa.
@VisibleForTesting
internal fun GroupPauseCodeEntryScreen(onRecipeReady: (GroupPauseRecipe) -> Unit, onCancel: () -> Unit) {
    var mode by remember { mutableStateOf(JoinMode.SCAN) }

    when (mode) {
        JoinMode.SCAN -> ScanFullScreen(
            onRecipeReady = onRecipeReady,
            onSwitchToManual = { mode = JoinMode.MANUAL },
            onCancel = onCancel,
        )
        // Niente più CalmCard: sfondo piatto come il resto del flusso.
        //
        // Bottoni ancorati al fondo pagina, su richiesta esplicita — stesso
        // schema del resto del flusso "Tempo insieme" (vedi
        // GroupPauseBluetoothLobbyHostScreen.kt): titolo+campo vivono in una
        // Column interna con weight(1f) e la stessa `Arrangement.Center` che
        // CalmScreenColumn usava di default per tutto, "Scansiona invece" e
        // Cancel restano gli ultimi fratelli non pesati, a tutta larghezza e
        // 48.dp di altezza come le altre CTA di questa serie di modifiche.
        //
        // **L'otter e la gerarchia dei due bottoni** sono stati corretti su
        // segnalazione ("è un po' vuota e manca otter, le CTA sono errate,
        // scan è la primary"): prima questa schermata non aveva alcuna
        // mascotte (unica dell'intero flusso "Tempo insieme" a esserne
        // priva), e "Scansiona invece" era un `CalmSecondaryButton` mentre
        // il bottone di invio del codice manuale era il `Button` pieno —
        // al contrario di quanto dice il commento originale di
        // [ManualCodeTab] qui sotto: scansionare è la via più rapida delle
        // due, quindi è lei ad avere il trattamento da CTA primaria, non
        // l'inserimento manuale (il ripiego per chi non può o non vuole
        // usare la fotocamera).
        JoinMode.MANUAL -> CalmScreenColumn(contentPadding = PaddingValues(32.dp), verticalArrangement = Arrangement.Top) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                OtterZenMark(markSize = 88.dp)
                Text(
                    text = stringResource(R.string.group_pause_join_title),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 14.dp, bottom = 20.dp)
                )
                ManualCodeTab(onRecipeReady)
            }
            // Cancel sopra, Scan (la CTA primaria di questa schermata)
            // sotto — stesso ordine del resto del flusso "Tempo insieme"
            // (Cancel/"Iniziamo" nella lobby host, Cancel/conferma nel
            // countdown): l'azione primaria è sempre l'ultimo bottone,
            // non il primo.
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(48.dp),
            ) {
                Text(stringResource(android.R.string.cancel))
            }
            Button(
                onClick = { mode = JoinMode.SCAN },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(48.dp),
            ) {
                Text(stringResource(R.string.group_pause_join_scan_tab))
            }
        }
    }
}

/**
 * Fotocamera a piena pagina per la scansione del QR — prima confinata in un
 * riquadro di 260dp dentro alla stessa colonna con padding di tutte le altre
 * schermate (Fase 1, mai messo in discussione finché non segnalato).
 * [QrScannerView] riempie l'intero schermo, dietro a tutto il resto;
 * controlli e istruzioni stanno sopra come overlay, con un velo scuro
 * sfumato dietro al testo — l'anteprima della fotocamera può essere di
 * qualunque colore/luminosità reale, un testo colorato dal tema non
 * garantirebbe leggibilità sopra di essa.
 *
 * Prima del permesso fotocamera non c'è ancora nulla da mostrare a piena
 * pagina: quello stato usa lo sfondo/i colori standard dell'app
 * ([calmBackground]), non l'overlay scuro pensato per stare sopra
 * l'anteprima live.
 */
@Composable
private fun ScanFullScreen(
    onRecipeReady: (GroupPauseRecipe) -> Unit,
    onSwitchToManual: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    // Il popup di sistema parte subito, non solo dopo un tap su "Concedi"
    // nella schermata sotto — segnalato: si toccava "Scansiona o inserisci
    // un codice" e ci si ritrovava davanti a un'altra schermata da leggere
    // e un altro bottone da toccare prima di vedere il popup vero, con la
    // fotocamera già annunciata come lo scopo di questo passo. `Unit` come
    // chiave: riparte ogni volta che si rientra in questa schermata (non a
    // ogni ricomposizione), così un rifiuto non intrappola in un loop di
    // popup ma un nuovo ingresso — dalla lobby dal vivo, di nuovo — la
    // rischiede. La schermata di motivazione sotto resta: è il ripiego per
    // chi nega, con il link al codice manuale.
    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasPermission) {
            var errorText by remember { mutableStateOf<String?>(null) }
            val invalidCodeText = stringResource(R.string.group_pause_invalid_code)

            QrScannerView(
                onDecoded = { raw ->
                    val recipe = decodeGroupPauseRecipe(raw)
                    if (recipe != null) onRecipeReady(recipe) else errorText = invalidCodeText
                },
                modifier = Modifier.fillMaxSize()
            )

            // Riquadro guida puramente decorativo (zxing analizza l'intero
            // fotogramma, non solo quest'area) — indica dove inquadrare senza
            // costringere a una geometria di scansione che il decoder non ha.
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(240.dp)
                    .border(3.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(24.dp))
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                        )
                    )
                    .safeDrawingPadding()
                    .padding(24.dp)
            ) {
                Text(
                    text = stringResource(R.string.group_pause_scan_instruction),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = errorText?.let { 8.dp } ?: 0.dp)
                )
                errorText?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                }
                TextButton(onClick = onSwitchToManual, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.group_pause_manual_entry_link))
                }
            }
        } else {
            // "Concedi" ancorato al fondo pagina, a tutta larghezza, 48.dp —
            // stesso standard del resto dell'app in questa serie di
            // modifiche: il testo di motivazione vive in una Column interna
            // con weight(1f) e la stessa Arrangement.Center di prima, il
            // bottone (e il link "inserisci codice" sotto di lui) restano
            // gli ultimi fratelli non pesati.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .calmBackground()
                    .safeDrawingPadding()
                    .padding(32.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(R.string.group_pause_camera_permission_rationale),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Text(stringResource(R.string.permission_action_grant))
                }
                // Senza questo, negare la fotocamera era un vicolo cieco:
                // restavano solo "Concedi" e "Annulla", mentre il codice si
                // può benissimo digitare. Resta un link di testo e non un
                // [CalmSecondaryButton] perché qui l'azione principale
                // ("Concedi") è già un Button pieno.
                TextButton(
                    onClick = onSwitchToManual,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Text(stringResource(R.string.group_pause_manual_entry_link))
                }
            }
        }

        // Barra superiore: sempre visibile, indipendentemente dal permesso —
        // uscire non deve dipendere dall'aver concesso la fotocamera.
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent)
                    )
                )
                .safeDrawingPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.Start,
        ) {
            TextButton(onClick = onCancel) {
                Text(stringResource(android.R.string.cancel), color = Color.White)
            }
        }
    }
}

@Composable
private fun ManualCodeTab(onRecipeReady: (GroupPauseRecipe) -> Unit) {
    var code by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf("") }
    val invalidCodeText = stringResource(R.string.group_pause_invalid_code)

    CalmTextField(
        value = code,
        onValueChange = { code = it; errorText = "" },
        label = stringResource(R.string.group_pause_manual_code_hint),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    )
    if (errorText.isNotBlank()) {
        Text(
            text = errorText,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )
    }
    // Non più il `Button` pieno: la CTA primaria di questa schermata è
    // "Scansiona invece" (vedi il commento al punto di chiamata in
    // [GroupPauseCodeEntryScreen]) — l'invio del codice digitato resta
    // un'azione secondaria, coerente con [CalmSecondaryButton].
    CalmSecondaryButton(
        text = stringResource(R.string.group_pause_manual_code_join_button),
        onClick = {
            val recipe = decodeGroupPauseRecipe(code)
            if (recipe != null) onRecipeReady(recipe) else errorText = invalidCodeText
        },
        modifier = Modifier.fillMaxWidth().height(48.dp),
    )
}

/**
 * Anteprima fotocamera (CameraX) + decodifica QR (zxing) frame per frame.
 * `ImageAnalysis` gira sull'executor principale ([ContextCompat.getMainExecutor]):
 * [onDecoded] è quindi già sicuro da chiamare direttamente su stato Compose,
 * senza bisogno di un dispatch esplicito.
 */
@Composable
private fun QrScannerView(onDecoded: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }

    DisposableEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().apply {
                surfaceProvider = previewView.surfaceProvider
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .apply { setAnalyzer(ContextCompat.getMainExecutor(context), QrCodeAnalyzer(onDecoded)) }
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            cameraProviderFuture.get().unbindAll()
        }
    }

    AndroidView(modifier = modifier, factory = { previewView })
}

/**
 * Un solo formato atteso (QR_CODE): `MultiFormatReader.decode()` fallisce
 * con `NotFoundException` per la stragrande maggioranza dei fotogrammi
 * (nessun codice a barre nell'inquadratura in quel momento) — normale,
 * non un errore da segnalare, si ignora e si riprova al fotogramma
 * successivo.
 */
private class QrCodeAnalyzer(private val onDecoded: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val reader = MultiFormatReader()

    override fun analyze(image: ImageProxy) {
        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
            val source = PlanarYUVLuminanceSource(
                bytes, plane.rowStride, image.height,
                0, 0, image.width, image.height,
                false,
            )
            val bitmap = BinaryBitmap(HybridBinarizer(source))
            val result = reader.decode(bitmap)
            onDecoded(result.text)
        } catch (e: NotFoundException) {
            // Nessun QR in questo fotogramma — atteso, si riprova al prossimo.
        } catch (e: Exception) {
            // Fotogramma non decodificabile per un altro motivo — si ignora.
        } finally {
            image.close()
        }
    }
}
