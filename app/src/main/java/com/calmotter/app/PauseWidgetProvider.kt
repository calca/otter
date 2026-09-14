package com.calmotter.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Le due tinte già in uso dal widget (vedi ic_otter_widget.xml/ic_pause_widget.xml
// e la nota "static hex literals" in specs/home-screen-widget/design.md) —
// riusate anche per l'anello e la nuova icona di sblocco invece di introdurre
// una terza tinta.
private const val WIDGET_BG = 0xFFEAF0E8
private const val IDLE_TINT = 0xFF2C4A3E.toInt()
private const val ACTIVE_TINT = 0xFF3D7A5C.toInt()
private const val RING_TRACK_TINT = 0x1F2C4A3E.toInt() // IDLE_TINT a ~12% alpha

private val SIZE_SMALL = DpSize(40.dp, 40.dp)
private val SIZE_SQUARE = DpSize(110.dp, 110.dp)
private val SIZE_WIDE = DpSize(250.dp, 110.dp)

/**
 * Widget per la home screen del launcher, in Jetpack Glance, ridimensionabile
 * su tre taglie (vedi [SizeMode.Responsive] sotto — un solo widget, non tre
 * da installare separatamente):
 * - **1×1** (40dp) — il widget originale, invariato: icona + etichetta compatta.
 *   Resta disponibile per chi preferisce il minimo.
 * - **2×2** (110dp) — l'otter al centro di un anello che segna quanto tempo
 *   è passato, disegnato su un [Bitmap] (vedi [buildRingBitmap] — Glance non
 *   offre un progress circolare con avanzamento reale, solo uno spinner
 *   indeterminato, verificato decompilando `androidx.glance:glance-appwidget`
 *   prima di scegliere questa strada).
 * - **4×2** (250×110dp) — stesso anello+otter a sinistra, più spazio a destra
 *   per il tempo rimanente e una frase riflessiva corta a rotazione
 *   ([R.array.widget_reflective_phrases] — versioni accorciate delle 12
 *   frasi di `pause_phrases`, che non ci starebbero in questo spazio).
 *
 * Comportamento al tap, invariato dalla versione 1×1 originale, identico su
 * tutte e tre le taglie (vedi [PauseWidgetTapAction]):
 * - Nessuna sessione attiva → avvia una sessione con l'ultima durata usata.
 * - Sessione già attiva → apre BlockOverlayActivity per lo sblocco.
 * Nelle taglie 2×2/4×2 un'icona a lucchetto (mai "stop": nell'app di oggi
 * nessuno termina una pausa senza la password del partner) segnala dove
 * toccare per sbloccare — è un'indicazione visiva, non una seconda azione:
 * l'intera card resta comunque cliccabile con lo stesso identico effetto.
 *
 * Il widget si aggiorna:
 * - Ogni 30 minuti (updatePeriodMillis in widget_pause_info.xml, minimo Android).
 * - Subito dopo startSession/endSession via updateAllWidgets().
 * - Ogni 60 secondi mentre una sessione è attiva, agganciato allo stesso tick
 *   che SessionForegroundService già usa per rinfrescare la notifica — non un
 *   nuovo meccanismo, così l'anello avanza e la frase ruota "a scatti" allo
 *   stesso ritmo della notifica persistente, non fluido come gli anelli Compose
 *   di Home/BlockScreen (RemoteViews non anima: stesso limite già accettato
 *   per la notifica, non un passo indietro introdotto qui).
 */
class PauseGlanceWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(SIZE_SMALL, SIZE_SQUARE, SIZE_WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val sessionManager = SessionManager.getInstance(context)

        provideContent {
            when (LocalSize.current) {
                SIZE_SMALL -> SmallWidgetContent(context, sessionManager)
                SIZE_WIDE -> WideWidgetContent(context, sessionManager)
                else -> SquareWidgetContent(context, sessionManager)
            }
        }
    }
}

/** Taglia 1×1 originale — logica e visuale invariate rispetto a prima di questa revisione. */
@Composable
private fun SmallWidgetContent(context: Context, sessionManager: SessionManager) {
    val isActive = sessionManager.isSessionActive()

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(8.dp)
            .background(Color(WIDGET_BG))
            .cornerRadius(16.dp)
            .clickable(actionRunCallback<PauseWidgetTapAction>()),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        if (isActive) {
            val minutes = (sessionManager.remainingMillis() / 60_000L).toInt().coerceAtLeast(0)
            val timeText = when {
                minutes < 5 -> context.getString(R.string.widget_soon)
                minutes < 60 -> context.getString(R.string.widget_minutes, (minutes / 5) * 5)
                else -> context.getString(R.string.widget_hours, minutes / 60, minutes % 60)
            }
            Image(
                provider = ImageProvider(R.drawable.ic_pause_widget),
                contentDescription = null,
                modifier = GlanceModifier.size(26.dp)
            )
            Text(
                text = timeText,
                style = TextStyle(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorProvider(day = Color(ACTIVE_TINT), night = Color(ACTIVE_TINT))
                )
            )
        } else {
            Image(
                provider = ImageProvider(R.drawable.ic_otter_widget),
                contentDescription = null,
                modifier = GlanceModifier.size(28.dp)
            )
            Text(
                text = context.getString(R.string.widget_label_idle),
                style = TextStyle(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorProvider(day = Color(IDLE_TINT), night = Color(IDLE_TINT))
                )
            )
        }
    }
}

/** Taglia 2×2 — l'otter al centro dell'anello, il tempo rimanente sotto, lucchetto in un angolo se attivo. */
@Composable
private fun SquareWidgetContent(context: Context, sessionManager: SessionManager) {
    val isActive = sessionManager.isSessionActive()
    val ringBitmap = remember(isActive, sessionManager) {
        buildRingFor(context, sessionManager, ringSizeDp = 72)
    }

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(8.dp)
            .background(Color(WIDGET_BG))
            .cornerRadius(16.dp)
            .clickable(actionRunCallback<PauseWidgetTapAction>()),
        contentAlignment = Alignment.TopEnd,
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Image(provider = ImageProvider(ringBitmap), contentDescription = null, modifier = GlanceModifier.size(72.dp))
                Image(
                    provider = ImageProvider(R.drawable.ic_otter_widget),
                    contentDescription = null,
                    modifier = GlanceModifier.size(30.dp),
                )
            }
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = if (isActive) {
                    CalmCountdown.format(sessionManager.remainingMillis(), context)
                } else {
                    context.getString(R.string.home_start_hint)
                },
                style = TextStyle(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = ColorProvider(
                        day = Color(if (isActive) ACTIVE_TINT else IDLE_TINT),
                        night = Color(if (isActive) ACTIVE_TINT else IDLE_TINT),
                    ),
                ),
            )
        }
        if (isActive) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_unlock),
                contentDescription = context.getString(R.string.widget_unlock_label),
                modifier = GlanceModifier.padding(4.dp).size(16.dp),
            )
        }
    }
}

/** Taglia 4×2 — anello+otter a sinistra, tempo + frase riflessiva a rotazione + sblocco a destra. */
@Composable
private fun WideWidgetContent(context: Context, sessionManager: SessionManager) {
    val isActive = sessionManager.isSessionActive()
    val ringBitmap = remember(isActive, sessionManager) {
        buildRingFor(context, sessionManager, ringSizeDp = 72)
    }

    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(8.dp)
            .background(Color(WIDGET_BG))
            .cornerRadius(16.dp)
            .clickable(actionRunCallback<PauseWidgetTapAction>()),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Image(provider = ImageProvider(ringBitmap), contentDescription = null, modifier = GlanceModifier.size(72.dp))
            Image(provider = ImageProvider(R.drawable.ic_otter_widget), contentDescription = null, modifier = GlanceModifier.size(30.dp))
        }
        Spacer(modifier = GlanceModifier.width(14.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            if (isActive) {
                Text(
                    text = CalmCountdown.format(sessionManager.remainingMillis(), context),
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ColorProvider(day = Color(ACTIVE_TINT), night = Color(ACTIVE_TINT))),
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    text = context.resources.getStringArray(R.array.widget_reflective_phrases).random(),
                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal, color = ColorProvider(day = Color(ACTIVE_TINT), night = Color(ACTIVE_TINT))),
                )
                Spacer(modifier = GlanceModifier.height(8.dp))
                Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_unlock),
                        contentDescription = null,
                        modifier = GlanceModifier.size(14.dp),
                    )
                    Spacer(modifier = GlanceModifier.width(4.dp))
                    Text(
                        text = context.getString(R.string.widget_unlock_label),
                        style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ColorProvider(day = Color(ACTIVE_TINT), night = Color(ACTIVE_TINT))),
                    )
                }
            } else {
                Text(
                    text = context.getString(R.string.home_start_hint),
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ColorProvider(day = Color(IDLE_TINT), night = Color(IDLE_TINT))),
                )
            }
        }
    }
}

/**
 * Genera l'anello per le taglie 2×2/4×2: tratteggiato e vuoto a riposo,
 * pieno in proporzione al tempo trascorso durante una sessione attiva —
 * stessa matematica di `ProgressRing` in `MainScreen.kt`
 * (`(total - remaining) / total`), qui "fotografata" una volta per
 * aggiornamento invece che animata dal vivo (vedi la doc della classe).
 */
private fun buildRingFor(context: Context, sessionManager: SessionManager, ringSizeDp: Int): Bitmap {
    if (!sessionManager.isSessionActive()) {
        return buildRingBitmap(context, ringSizeDp, strokeDp = 6f, fraction = null)
    }
    val remaining = sessionManager.remainingMillis()
    val total = sessionManager.totalMillis()
    val fraction = if (total > 0) ((total - remaining).toFloat() / total) else 0f
    return buildRingBitmap(context, ringSizeDp, strokeDp = 6f, fraction = fraction.coerceIn(0f, 1f))
}

/**
 * Disegna l'anello su un [Bitmap] con `android.graphics.Canvas`: Glance non
 * espone un progress circolare con avanzamento reale (il suo
 * `CircularProgressIndicator` è solo uno spinner indeterminato — verificato
 * decompilando `glance-appwidget:1.2.0` prima di scegliere questa strada
 * invece di dare per scontato che esistesse). [fraction] `null` disegna solo
 * la traccia tratteggiata (stato inattivo); un valore 0–1 disegna anche
 * l'arco pieno sopra la traccia (stato attivo).
 */
private fun buildRingBitmap(context: Context, sizeDp: Int, strokeDp: Float, fraction: Float?): Bitmap {
    val density = context.resources.displayMetrics.density
    val sizePx = (sizeDp * density).toInt().coerceAtLeast(1)
    val strokePx = strokeDp * density
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val inset = strokePx / 2f
    val rect = RectF(inset, inset, sizePx - inset, sizePx - inset)

    val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokePx
        strokeCap = Paint.Cap.ROUND
        color = RING_TRACK_TINT
        if (fraction == null) {
            pathEffect = DashPathEffect(floatArrayOf(strokePx * 1.6f, strokePx * 1.3f), 0f)
        }
    }
    canvas.drawArc(rect, 0f, 360f, false, trackPaint)

    if (fraction != null) {
        val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokePx
            strokeCap = Paint.Cap.ROUND
            color = ACTIVE_TINT
        }
        canvas.drawArc(rect, -90f, 360f * fraction, false, progressPaint)
    }
    return bitmap
}

/** Gestisce il tap sul widget: avvia una sessione oppure apre la schermata di blocco. */
class PauseWidgetTapAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val sessionManager = SessionManager.getInstance(context)

        if (sessionManager.isSessionActive()) {
            val intent = Intent(context, BlockOverlayActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(intent)
        } else {
            // sessionManager.startSession() salva già l'ultima durata e aggiorna
            // il widget (vedi SessionManager.kt).
            sessionManager.startSession(PauseWidgetProvider.getLastDuration(context))
        }
    }
}

class PauseWidgetProvider : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = PauseGlanceWidget()

    companion object {
        private const val PREFS_WIDGET = "calm_otter_widget"
        private const val KEY_LAST_DURATION = "last_duration"
        private const val DEFAULT_DURATION_MIN = 30

        /**
         * Aggiorna tutti i widget istanziati sulla home screen.
         * Chiamato da SessionManager dopo startSession/endSession, e da
         * SessionForegroundService ogni 60 secondi mentre una sessione è
         * attiva (vedi la doc di [PauseGlanceWidget]).
         */
        /**
         * Aggiorna tutti i widget istanziati sulla home screen.
         * Chiamato da SessionManager dopo startSession/endSession, e da
         * SessionForegroundService ogni 60 secondi mentre una sessione è
         * attiva (vedi la doc di [PauseGlanceWidget]).
         *
         * Nota su una latenza osservata (non risolvibile da qui): su
         * device/emulatore, il ridisegno effettivo del widget piazzato può
         * restare visibilmente indietro fino a un minuto o più dopo questa
         * chiamata, pur risultando nei log che Glance ha già ricomposto e
         * chiuso la sessione molto prima — il collo di bottiglia è nel
         * ridisegno lato sistema/launcher dell'AppWidgetHostView, non nella
         * generazione delle RemoteViews da parte di questo codice (un
         * secondo `updateAll()` a distanza di pochi secondi, provato
         * durante lo sviluppo, non ha misurabilmente accelerato nulla — la
         * ricomposizione Glance risultava già conclusa dai log ben prima
         * che il disegno a schermo si aggiornasse). Per una sessione attiva
         * questo si autocorregge da solo entro il tick successivo (60s);
         * per la fine sessione, l'aggiornamento periodico di sistema ogni
         * 30 minuti (updatePeriodMillis in widget_pause_info.xml) resta la
         * rete di sicurezza ultima, la stessa già accettata dal widget
         * originale prima di questa revisione.
         */
        fun updateAllWidgets(context: Context) {
            CoroutineScope(Dispatchers.Default).launch {
                PauseGlanceWidget().updateAll(context)
            }
        }

        /** Salva la durata appena usata così il widget la riusa al prossimo tap. */
        fun saveLastDuration(context: Context, minutes: Int) {
            context.getSharedPreferences(PREFS_WIDGET, Context.MODE_PRIVATE)
                .edit().putInt(KEY_LAST_DURATION, minutes).apply()
        }

        fun getLastDuration(context: Context): Int {
            val prefs = context.getSharedPreferences(PREFS_WIDGET, Context.MODE_PRIVATE)
            return prefs.getInt(KEY_LAST_DURATION, DEFAULT_DURATION_MIN)
        }
    }
}
