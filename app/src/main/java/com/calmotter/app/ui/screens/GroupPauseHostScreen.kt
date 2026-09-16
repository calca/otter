package com.calmotter.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calmotter.app.GroupPauseRecipe
import com.calmotter.app.R
import com.calmotter.app.encode
import com.calmotter.app.generateQrCodeBitmap
import com.calmotter.app.ui.mascot.OtterFloatMark
import kotlin.random.Random

private val DURATION_OPTIONS = listOf(15, 30, 60, 90, 120)
private val DELAY_OPTIONS = listOf(1, 2, 5)

private sealed class HostFlowStep {
    data object Setup : HostFlowStep()
    data class BluetoothLobby(val durationMinutes: Int) : HostFlowStep()
    data class QrDelayPicker(val durationMinutes: Int) : HostFlowStep()
    // I nomi raccolti nella lobby viaggiano fin qui per poter finire nella
    // sessione: il percorso QR non ne ha (lista vuota), ed è il motivo per cui
    // lì l'indicazione resta generica.
    data class Countdown(
        val recipe: GroupPauseRecipe,
        val showShareHeader: Boolean,
        val companions: List<String> = emptyList(),
    ) : HostFlowStep()
}

/**
 * Prepara il Tempo Insieme lato host: durata → lobby dal vivo (Bluetooth+NFC,
 * predefinita — vedi GroupPauseBluetoothLobbyHostScreen) → conto alla
 * rovescia condiviso. Chi preferisce QR/codice invece del vivo può tornare
 * indietro dalla lobby stessa ("Preferisci un codice o un QR?") verso
 * [GroupPauseQrDelayScreen], che chiede solo il "tra quanto iniziare" che il
 * percorso dal vivo non usa. Nessuno stato persistito prima che il conto
 * alla rovescia arrivi a zero: uscire da questa schermata prima (onCancel)
 * non lascia nulla in sospeso.
 */
@Composable
fun GroupPauseHostScreen(
    onStarted: (durationMinutes: Int, companions: List<String>, groupTag: Int) -> Unit,
    onCancel: () -> Unit,
) {
    var step by remember { mutableStateOf<HostFlowStep>(HostFlowStep.Setup) }

    when (val current = step) {
        HostFlowStep.Setup -> GroupPauseSetupScreen(
            onContinue = { durationMinutes -> step = HostFlowStep.BluetoothLobby(durationMinutes) },
            onCancel = onCancel,
        )
        is HostFlowStep.BluetoothLobby -> GroupPauseBluetoothLobbyHostScreen(
            durationMinutes = current.durationMinutes,
            onRecipeReady = { recipe, companions ->
                step = HostFlowStep.Countdown(recipe, showShareHeader = false, companions = companions)
            },
            onWantCodeInstead = { step = HostFlowStep.QrDelayPicker(current.durationMinutes) },
            onCancel = onCancel,
        )
        is HostFlowStep.QrDelayPicker -> GroupPauseQrDelayScreen(
            onCreate = { delayMinutes ->
                step = HostFlowStep.Countdown(
                    recipe = GroupPauseRecipe(
                        durationMinutes = current.durationMinutes,
                        startAtEpochMillis = System.currentTimeMillis() + delayMinutes * 60_000L,
                        groupTag = Random.nextInt(0, 65536),
                    ),
                    showShareHeader = true,
                )
            },
            onCancel = onCancel,
        )
        is HostFlowStep.Countdown -> GroupPauseCountdownScreen(
            durationMinutes = current.recipe.durationMinutes,
            startAtEpochMillis = current.recipe.startAtEpochMillis,
            onReady = { onStarted(current.recipe.durationMinutes, current.companions, current.recipe.groupTag) },
            onCancel = onCancel,
            header = {
                if (current.showShareHeader) GroupPauseShareHeader(code = current.recipe.encode())
            },
        )
    }
}

@Composable
private fun GroupPauseSetupScreen(
    onContinue: (durationMinutes: Int) -> Unit,
    onCancel: () -> Unit,
) {
    var selectedDuration by remember { mutableIntStateOf(30) }

    CalmScreenColumn(contentPadding = PaddingValues(32.dp)) {
        OtterFloatMark(markSize = 88.dp)
        Text(
            text = stringResource(R.string.group_pause_host_title),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
        )
        Text(
            text = stringResource(R.string.group_pause_host_intro),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 28.dp)
        )

        SetupLabel(stringResource(R.string.group_pause_duration_label))
        MinutePillRow(
            options = DURATION_OPTIONS,
            selected = selectedDuration,
            onSelect = { selectedDuration = it },
            labelFor = { minutesLabel(it) },
        )

        Row(modifier = Modifier.fillMaxWidth().padding(top = 32.dp)) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            ) {
                Text(stringResource(android.R.string.cancel))
            }
            Button(
                onClick = { onContinue(selectedDuration) },
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.group_pause_host_continue_button))
            }
        }
    }
}

/**
 * Ripiego per chi preferisce un codice/QR invece della lobby dal vivo —
 * raggiunta dal link "Preferisci un codice o un QR?" dentro
 * [GroupPauseBluetoothLobbyHostScreen]. Chiede solo il "tra quanto
 * iniziare", l'unico dato che il percorso dal vivo non usa (lì l'avvio è
 * un'azione dell'host, non pianificata).
 */
@Composable
private fun GroupPauseQrDelayScreen(
    onCreate: (delayMinutes: Int) -> Unit,
    onCancel: () -> Unit,
) {
    var selectedDelay by remember { mutableIntStateOf(1) }

    CalmScreenColumn(contentPadding = PaddingValues(32.dp)) {
        Text(
            text = stringResource(R.string.group_pause_qr_delay_title),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        SetupLabel(stringResource(R.string.group_pause_start_in_label))
        MinutePillRow(
            options = DELAY_OPTIONS,
            selected = selectedDelay,
            onSelect = { selectedDelay = it },
            labelFor = { minutesLabel(it) },
        )

        Row(modifier = Modifier.fillMaxWidth().padding(top = 32.dp)) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            ) {
                Text(stringResource(android.R.string.cancel))
            }
            Button(
                onClick = { onCreate(selectedDelay) },
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.group_pause_create_button))
            }
        }
    }
}

@Composable
private fun SetupLabel(text: String, topPadding: androidx.compose.ui.unit.Dp = 0.dp) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topPadding, bottom = 8.dp),
        textAlign = TextAlign.Center,
    )
}

internal fun minutesLabel(minutes: Int): String {
    if (minutes < 60) return "${minutes}m"
    val hours = minutes / 60
    val remainder = minutes % 60
    return if (remainder == 0) "${hours}h" else "${hours}h${remainder}"
}

/**
 * Riga di pillole generica per un valore in minuti — stesso stile a bassa
 * opacità di `DurationChipRow`/`GoalChipRow` (privati ai rispettivi file,
 * da cui la duplicazione qui, stessa convenzione già seguita altrove in
 * questo codebase).
 */
@Composable
private fun MinutePillRow(
    options: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
    labelFor: (Int) -> String,
) {
    // FlowRow e non Row: con cinque opzioni ("15m 30m 1h 1h30 2h") la riga
    // piatta non ci stava in larghezza e l'ultimo chip veniva compresso fino
    // a mandare a capo la propria etichetta — "2h" diventava "2" sopra e "h"
    // sotto, con il chip più alto degli altri. Andando a capo per intero
    // restano tutte le opzioni visibili insieme (utile: si sta scegliendo fra
    // loro) e non serve uno scorrimento che ne nasconda qualcuna. Regge anche
    // etichette più lunghe in altre lingue, che è il vero motivo per non
    // limitarsi a ridurre il padding.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { value ->
            val isSelected = value == selected
            Surface(
                onClick = { onSelect(value) },
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primary.copy(alpha = if (isSelected) 0.22f else 0.08f),
            ) {
                Text(
                    text = labelFor(value),
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isSelected) 1f else 0.65f),
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

/** QR + codice manuale da condividere — mostrato sopra al conto alla rovescia condiviso. */
@Composable
private fun GroupPauseShareHeader(code: String) {
    // Il QR deve restare scuro-su-chiaro per essere leggibile (vedi
    // generateQrCodeBitmap), ma il "chiaro" non deve per forza essere un
    // quadrato bianco pieno, che sulle palette tenui dell'app stonava.
    //
    // - Tema chiaro: sfondo del QR trasparente, così il gradiente di
    //   calmBackground passa attraverso e il QR sembra appoggiato sulla
    //   pagina invece che incollato sopra. I moduli usano onBackground
    //   (quasi nero in tutte e tre le palette chiare): 14:1 abbondanti di
    //   contrasto sul punto più scuro del gradiente.
    // - Tema scuro: la trasparenza qui è impossibile — moduli scuri su
    //   sfondo scuro non li legge nessuno, e invertire il QR lo renderebbe
    //   illeggibile al joiner. Serve una lastra chiara, ma tinta di palette
    //   (onBackground, che in scuro è un tono chiaro tenue) invece del
    //   bianco, con angoli tondi e padding perché legga come una card
    //   voluta e non come un rettangolo appiccicato.
    val darkTheme = isSystemInDarkTheme()
    val scheme = MaterialTheme.colorScheme
    val moduleColor = if (darkTheme) scheme.background else scheme.onBackground
    val fieldColor = if (darkTheme) scheme.onBackground else Color.Transparent

    val qrBitmap = remember(code, moduleColor, fieldColor) {
        generateQrCodeBitmap(
            content = code,
            sizePx = 512,
            darkColor = moduleColor.toArgb(),
            lightColor = fieldColor.toArgb(),
        )
    }

    Text(
        text = stringResource(R.string.group_pause_share_hint),
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(bottom = 16.dp)
    )
    Image(
        bitmap = qrBitmap.asImageBitmap(),
        contentDescription = null,
        modifier = Modifier
            .then(
                // La lastra esiste solo dove serve davvero: in tema chiaro
                // aggiungerla riporterebbe esattamente il rettangolo che
                // questo cambiamento toglie.
                if (darkTheme) {
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(fieldColor)
                        .padding(12.dp)
                } else {
                    Modifier
                }
            )
            .size(220.dp)
    )
    Text(
        text = code,
        fontFamily = FontFamily.Monospace,
        fontSize = 16.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp)
    )
}
