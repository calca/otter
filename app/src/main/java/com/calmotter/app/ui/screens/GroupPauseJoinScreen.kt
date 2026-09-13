package com.calmotter.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer

private enum class JoinMode { SCAN, MANUAL }

private sealed class JoinFlowStep {
    data object Live : JoinFlowStep()
    data object QrOrCode : JoinFlowStep()
    data class Countdown(val recipe: GroupPauseRecipe) : JoinFlowStep()
}

/**
 * Unisciti al Tempo Insieme: la lobby dal vivo (Bluetooth+NFC — vedi
 * GroupPauseBluetoothLobbyJoinScreen) è il punto di ingresso predefinito;
 * chi preferisce un codice/QR può passare a [GroupPauseCodeEntryScreen]
 * tramite il link "Ho un codice o un QR" dentro alla lobby stessa. Stesso
 * conto alla rovescia condiviso alla fine di entrambi i percorsi.
 */
@Composable
fun GroupPauseJoinScreen(onJoined: (durationMinutes: Int) -> Unit, onCancel: () -> Unit) {
    var step by remember { mutableStateOf<JoinFlowStep>(JoinFlowStep.Live) }

    when (val current = step) {
        JoinFlowStep.Live -> GroupPauseBluetoothLobbyJoinScreen(
            onRecipeReady = { recipe -> step = JoinFlowStep.Countdown(recipe) },
            onWantCodeInstead = { step = JoinFlowStep.QrOrCode },
            onCancel = onCancel,
        )
        JoinFlowStep.QrOrCode -> GroupPauseCodeEntryScreen(
            onRecipeReady = { recipe -> step = JoinFlowStep.Countdown(recipe) },
            onCancel = onCancel,
        )
        is JoinFlowStep.Countdown -> GroupPauseCountdownScreen(
            durationMinutes = current.recipe.durationMinutes,
            startAtEpochMillis = current.recipe.startAtEpochMillis,
            onReady = { onJoined(current.recipe.durationMinutes) },
            onCancel = onCancel,
        )
    }
}

/** Ripiego QR/codice manuale — invariato rispetto alla Fase 1, solo raggiunto diversamente. */
@Composable
private fun GroupPauseCodeEntryScreen(onRecipeReady: (GroupPauseRecipe) -> Unit, onCancel: () -> Unit) {
    var mode by remember { mutableStateOf(JoinMode.SCAN) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .calmBackground()
            .safeDrawingPadding()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.group_pause_join_title),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 20.dp)) {
            JoinModePill(
                label = stringResource(R.string.group_pause_join_scan_tab),
                selected = mode == JoinMode.SCAN,
                onClick = { mode = JoinMode.SCAN },
            )
            JoinModePill(
                label = stringResource(R.string.group_pause_join_manual_tab),
                selected = mode == JoinMode.MANUAL,
                onClick = { mode = JoinMode.MANUAL },
            )
        }

        when (mode) {
            JoinMode.SCAN -> ScanTab(onRecipeReady)
            JoinMode.MANUAL -> ManualCodeTab(onRecipeReady)
        }

        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
            Text(stringResource(android.R.string.cancel))
        }
    }
}

@Composable
private fun JoinModePill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
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

@Composable
private fun ScanTab(onRecipeReady: (GroupPauseRecipe) -> Unit) {
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

    if (!hasPermission) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.group_pause_camera_permission_rationale),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                Text(stringResource(R.string.permission_action_grant))
            }
        }
    } else {
        var errorText by remember { mutableStateOf<String?>(null) }
        val invalidCodeText = stringResource(R.string.group_pause_invalid_code)

        Box(
            modifier = Modifier
                .size(260.dp)
                .padding(bottom = 12.dp)
        ) {
            QrScannerView(
                onDecoded = { raw ->
                    val recipe = decodeGroupPauseRecipe(raw)
                    if (recipe != null) onRecipeReady(recipe) else errorText = invalidCodeText
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        errorText?.let {
            Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ManualCodeTab(onRecipeReady: (GroupPauseRecipe) -> Unit) {
    var code by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf("") }
    val invalidCodeText = stringResource(R.string.group_pause_invalid_code)

    OutlinedTextField(
        value = code,
        onValueChange = { code = it; errorText = "" },
        label = { Text(stringResource(R.string.group_pause_manual_code_hint)) },
        singleLine = true,
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
    Button(
        onClick = {
            val recipe = decodeGroupPauseRecipe(code)
            if (recipe != null) onRecipeReady(recipe) else errorText = invalidCodeText
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.group_pause_manual_code_join_button))
    }
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
