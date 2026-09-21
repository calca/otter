package com.calmotter.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.calmotter.app.GroupPauseRecipe
import com.calmotter.app.R
import com.calmotter.app.encode
import com.calmotter.app.generateQrCodeBitmap
import kotlin.random.Random
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.material3.TextButton
import android.widget.Toast
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.Canvas

/**
 * Opzioni di durata offerte sia dal selettore della lobby dal vivo
 * ([GroupPauseBluetoothLobbyHostScreen]) sia — indirettamente, tramite
 * [minutesLabel]/[MinutePillRow] — da nessun altro selettore proprio: stesso
 * elenco di `DURATION_LABELS` in `MainScreen.kt` (30 min → 4h a passi di 30)
 * e non un elenco indipendente, apposta perché il valore scelto in Home
 * (passato come durata iniziale della lobby) cada sempre su un'opzione
 * esistente qui invece di un valore "fuori lista" da normalizzare.
 */
internal val DURATION_OPTIONS = (1..8).map { it * 30 }
private val DELAY_OPTIONS = listOf(1, 2, 5)

/** Ritardo di partenza preselezionato sulla pagina QR — vedi [GroupPauseQrShareScreen]. */
private const val DEFAULT_QR_DELAY_MINUTES = 2

private sealed class HostFlowStep {
    data class BluetoothLobby(val durationMinutes: Int) : HostFlowStep()
    data class QrShare(val durationMinutes: Int) : HostFlowStep()
    // I nomi raccolti nella lobby viaggiano fin qui per poter finire nella
    // sessione: il percorso QR non ne ha (lista vuota), ed è il motivo per cui
    // lì l'indicazione resta generica.
    data class Countdown(
        val recipe: GroupPauseRecipe,
        val companions: List<String> = emptyList(),
    ) : HostFlowStep()
}

/**
 * Prepara il Tempo Insieme lato host: si entra **direttamente** nella lobby
 * dal vivo (Bluetooth+NFC, predefinita — vedi
 * GroupPauseBluetoothLobbyHostScreen), che include ora anche il selettore
 * della durata invece di essere un secondo passo separato — richiesto
 * esplicitamente per velocizzare la creazione, dato che prima occorreva
 * confermare la durata su una schermata a sé prima ancora di iniziare ad
 * ascoltare i partecipanti. [initialDurationMinutes] arriva da Home
 * (l'ultima scelta lì, vedi `DurationChipRow`/`selectedDurationIndex` in
 * `MainScreen.kt`) ed è solo il punto di partenza: resta modificabile nella
 * lobby stessa in qualsiasi momento prima di "Iniziamo".
 *
 * Chi preferisce QR/codice invece del vivo può tornare indietro dalla lobby
 * stessa ("Preferisci un codice o un QR?") verso [GroupPauseQrShareScreen],
 * che mostra **subito** il QR (stesso principio: una schermata dedicata solo
 * al "tra quanto iniziare" prima ancora di vedere il codice è stata rimossa,
 * il selettore ora sta sopra al QR stesso) — la durata scelta nella lobby lo
 * segue lì. Nessuno stato persistito prima che il conto alla rovescia arrivi
 * a zero: uscire da questa schermata prima (onCancel) non lascia nulla in
 * sospeso.
 */
@Composable
fun GroupPauseHostScreen(
    initialDurationMinutes: Int,
    onStarted: (durationMinutes: Int, companions: List<String>, groupTag: Int) -> Unit,
    onCancel: () -> Unit,
) {
    var step by remember {
        mutableStateOf<HostFlowStep>(HostFlowStep.BluetoothLobby(initialDurationMinutes))
    }

    when (val current = step) {
        is HostFlowStep.BluetoothLobby -> GroupPauseBluetoothLobbyHostScreen(
            initialDurationMinutes = current.durationMinutes,
            onRecipeReady = { recipe, companions ->
                step = HostFlowStep.Countdown(recipe, companions = companions)
            },
            onWantCodeInstead = { durationMinutes -> step = HostFlowStep.QrShare(durationMinutes) },
            onCancel = onCancel,
        )
        is HostFlowStep.QrShare -> GroupPauseQrShareScreen(
            durationMinutes = current.durationMinutes,
            onReady = { recipe -> onStarted(recipe.durationMinutes, emptyList(), recipe.groupTag) },
            onCancel = onCancel,
        )
        is HostFlowStep.Countdown -> GroupPauseCountdownScreen(
            durationMinutes = current.recipe.durationMinutes,
            startAtEpochMillis = current.recipe.startAtEpochMillis,
            onReady = { onStarted(current.recipe.durationMinutes, current.companions, current.recipe.groupTag) },
            onCancel = onCancel,
        )
    }
}

/**
 * Ripiego per chi preferisce un codice/QR invece della lobby dal vivo —
 * raggiunta dal link "Preferisci un codice o un QR?" dentro
 * [GroupPauseBluetoothLobbyHostScreen]. Mostra il QR **subito**, con il
 * selettore "tra quanto iniziare" sopra di esso invece che su una schermata
 * dedicata a sé (rimossa: chiedere solo quello prima ancora di vedere il
 * codice era un passo in più senza un vero motivo — l'host non ha comunque
 * ancora condiviso nulla con nessuno in quel momento).
 *
 * Il ritardo resta modificabile mentre la pagina è a schermo: cambiarlo
 * rigenera la ricetta (nuovo `startAtEpochMillis`, stesso `groupTag`), che a
 * sua volta rigenera il QR ([GroupPauseShareHeader] già osserva `code`) e
 * riavvia il conto alla rovescia da capo ([GroupPauseCountdownScreen] è già
 * keyed su `startAtEpochMillis`). Compromesso accettato: se qualcuno ha già
 * scansionato il codice precedente prima del cambio, quel dispositivo conta
 * alla rovescia verso l'orario vecchio — nessun canale per avvisarlo del
 * contrario, stesso limite già accettato altrove per questo percorso (vedi
 * "handshake poi autonomia", specs/group-pause/design.md); la finestra è
 * comunque quella di pochi minuti tipica di questo flow.
 */
@Composable
private fun GroupPauseQrShareScreen(
    durationMinutes: Int,
    onReady: (GroupPauseRecipe) -> Unit,
    onCancel: () -> Unit,
) {
    var delayMinutes by remember { mutableIntStateOf(DEFAULT_QR_DELAY_MINUTES) }
    val groupTag = remember { Random.nextInt(0, 65536) }
    val recipe = remember(durationMinutes, delayMinutes, groupTag) {
        GroupPauseRecipe(
            durationMinutes = durationMinutes,
            startAtEpochMillis = System.currentTimeMillis() + delayMinutes * 60_000L,
            groupTag = groupTag,
        )
    }

    GroupPauseCountdownScreen(
        durationMinutes = recipe.durationMinutes,
        startAtEpochMillis = recipe.startAtEpochMillis,
        onReady = { onReady(recipe) },
        onCancel = onCancel,
        header = {
            GroupPauseShareHeader(code = recipe.encode())
            SetupLabel(stringResource(R.string.group_pause_start_in_label), topPadding = 20.dp)
            MinutePillRow(
                options = DELAY_OPTIONS,
                selected = delayMinutes,
                onSelect = { delayMinutes = it },
                labelFor = { minutesLabel(it) },
            )
        },
    )
}

@Composable
internal fun SetupLabel(text: String, topPadding: androidx.compose.ui.unit.Dp = 0.dp) {
    // Stesso maiuscoletto spaziato delle sezioni di Impostazioni (redesign):
    // qui come là è un'etichetta che nomina il gruppo sotto, non contenuto.
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        letterSpacing = 0.12.em,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
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
 * questo codebase). Non `private`: usata anche da
 * [GroupPauseBluetoothLobbyHostScreen] per il selettore di durata ora
 * incorporato lì.
 */
@Composable
internal fun MinutePillRow(
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
            // Scelta piena e non solo più tinta, come le pillole di durata
            // della Home: stesso gesto, stessa forma, in tutte e due.
            Surface(
                onClick = { onSelect(value) },
                shape = RoundedCornerShape(50),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                },
            ) {
                Text(
                    text = labelFor(value),
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    },
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

/**
 * Come [MinutePillRow], ma **scorrevole invece che a capo** — stesso stile
 * pieno delle pillole di durata della Home (`DurationChipRow` in
 * MainScreen.kt: selezionata `primary`/`onPrimary`, le altre `tertiary` al
 * 55% con testo `onSurface` al 65%), duplicato qui per lo stesso motivo già
 * documentato sopra per [MinutePillRow].
 *
 * Nasce per [GroupPauseBluetoothLobbyHostScreen]: segnalato che le pillole
 * di durata lì non dovessero somigliare a quelle di [MinutePillRow] (a capo,
 * tinta debole) ma a quelle della Home — otto opzioni ("30m"→"4h") ci stanno
 * scomode su due righe fisse, mentre scorrere una riga sola è il gesto che
 * l'utente già conosce da lì. [MinutePillRow] resta invariata per chi la usa
 * già (il ritardo di avvio nella pagina QR, tre sole opzioni: andare a capo
 * non è mai stato un problema lì).
 */
@Composable
internal fun ScrollableMinutePillRow(
    options: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
    labelFor: (Int) -> String,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { value ->
            val isSelected = value == selected
            Surface(
                onClick = { onSelect(value) },
                shape = RoundedCornerShape(50),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f)
                },
            ) {
                Text(
                    text = labelFor(value),
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    },
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

/**
 * Icona "copia": due rettangoli arrotondati sovrapposti, disegnati a mano
 * con `drawRoundRect` invece che presi da Material — segnalato che al
 * bottone "Copia" ne mancava una, ma `Icons.Filled.ContentCopy` esiste solo
 * in `material-icons-extended` (verificato: non compila contro
 * `material-icons-core`, l'unico incluso in `app/build.gradle.kts`), e per
 * un glifo così semplice non vale il peso di quell'artefatto — stessa
 * ragione già scritta per [EyeGlyph], due rettangoli non hanno bisogno di
 * `PathParser` più di quanto ne avesse bisogno un occhio.
 */
@Composable
private fun CopyGlyph(modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.width * 0.09f
        val corner = CornerRadius(size.width * 0.14f)
        // Il rettangolo posteriore, spostato in basso a sinistra: quanto
        // basta perché si veda il "foglio sotto" senza confondersi con
        // quello davanti.
        drawRoundRect(
            color = tint,
            topLeft = Offset(0f, size.height * 0.22f),
            size = Size(size.width * 0.72f, size.height * 0.72f),
            cornerRadius = corner,
            style = Stroke(width = strokeWidth),
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(size.width * 0.28f, 0f),
            size = Size(size.width * 0.72f, size.height * 0.72f),
            cornerRadius = corner,
            style = Stroke(width = strokeWidth),
        )
    }
}

/** QR + codice manuale da condividere — mostrato sopra al conto alla rovescia condiviso. */
@Composable
private fun GroupPauseShareHeader(code: String) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val copiedText = stringResource(R.string.group_pause_code_copied)
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
    // Il codice va anche copiato, non solo letto: dettarlo a voce o
    // ricopiarlo a mano è l'alternativa quando non si può scansionare, e
    // ricopiarlo a mano è proprio il fastidio che questo bottone toglie —
    // segnalato contro il mockup, che ha la sua icona "copia" accanto.
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp)) {
        Text(
            text = code,
            fontFamily = FontFamily.Monospace,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.primary,
        )
        TextButton(
            onClick = {
                clipboard.setText(AnnotatedString(code))
                Toast.makeText(context, copiedText, Toast.LENGTH_SHORT).show()
            },
        ) {
            CopyGlyph(modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.group_pause_code_copy),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
