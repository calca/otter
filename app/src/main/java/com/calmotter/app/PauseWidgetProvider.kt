package com.calmotter.app

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
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
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Le due tinte già in uso dal widget (vedi ic_otter_widget.xml e la nota
// "static hex literals" in specs/home-screen-widget/design.md).
// Sfondo all'85%: un velo sopra lo sfondo del launcher, non una piastra piena.
private const val WIDGET_BG = 0xD9EAF0E8
private const val IDLE_TINT = 0xFF2C4A3E.toInt()
private const val ACTIVE_TINT = 0xFF3D7A5C.toInt()

private val SIZE_SMALL = DpSize(40.dp, 40.dp)
private val SIZE_WIDE = DpSize(110.dp, 40.dp)

/**
 * Widget per la home screen del launcher, in Jetpack Glance: **una
 * scorciatoia per iniziare una pausa**, niente di più. Due taglie:
 * - **1×1** — l'otter e la durata che partirà ("1 h");
 * - **2×1** — l'otter, "Pausa" e la durata.
 *
 * Durante una pausa il launcher è coperto dalla schermata della pausa (non
 * è tra le app che restano), quindi il widget non si vede quasi mai: prima
 * aveva un intero stato "in pausa" — anello che avanzava, tempo, frase che
 * ruotava, lucchetto, taglie 2×2 e 4×2 per mostrarli — aggiornato ogni
 * minuto per nessuno (segnalato). Ora in pausa mostra solo "In pausa", e non
 * si aggiorna più ogni minuto: solo all'inizio e alla fine della pausa e
 * quando cambia la durata scelta in Home.
 *
 * Al tocco ([PauseWidgetTapAction]): nessuna pausa → la avvia con la durata
 * mostrata e apre la schermata della pausa, ma solo con Accessibilità e Non disturbare concessi (altrimenti
 * apre l'app sulla spiegazione, come il tocco sull'otter: prima partiva una
 * pausa che non bloccava né silenziava nulla); pausa in corso → la schermata
 * della pausa.
 */
class PauseGlanceWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(SIZE_SMALL, SIZE_WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val sessionManager = SessionManager.getInstance(context)

        provideContent {
            val active = sessionManager.isSessionActive()
            val duration = durationPillLabel(LastDuration.get(context))
            when (LocalSize.current) {
                SIZE_WIDE -> WideWidgetContent(context, active, duration)
                else -> SmallWidgetContent(context, active, duration)
            }
        }
    }
}

private fun tint(active: Boolean) =
    ColorProvider(day = Color(if (active) ACTIVE_TINT else IDLE_TINT), night = Color(if (active) ACTIVE_TINT else IDLE_TINT))

@Composable
private fun SmallWidgetContent(context: Context, active: Boolean, duration: String) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(6.dp)
            .background(Color(WIDGET_BG))
            .cornerRadius(16.dp)
            .clickable(actionRunCallback<PauseWidgetTapAction>()),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_otter_widget),
            contentDescription = null,
            modifier = GlanceModifier.size(30.dp)
        )
        Text(
            text = if (active) context.getString(R.string.widget_label_active) else duration,
            style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = tint(active)),
        )
    }
}

@Composable
private fun WideWidgetContent(context: Context, active: Boolean, duration: String) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .background(Color(WIDGET_BG))
            .cornerRadius(16.dp)
            .clickable(actionRunCallback<PauseWidgetTapAction>()),
        // Otter e testo centrati nella larghezza, non attaccati a sinistra.
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_otter_widget),
            contentDescription = null,
            modifier = GlanceModifier.size(36.dp),
        )
        Spacer(modifier = GlanceModifier.width(10.dp))
        Column {
            Text(
                text = context.getString(if (active) R.string.widget_label_active else R.string.widget_label_idle),
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = tint(active)),
            )
            if (!active) {
                Text(
                    text = duration,
                    style = TextStyle(fontSize = 11.sp, color = tint(false)),
                )
            }
        }
    }
}

/** Il tocco sul widget: avvia una pausa, o apre quella in corso. */
class PauseWidgetTapAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val sessionManager = SessionManager.getInstance(context)
        val permissionsOk = BuildConfig.DEBUG ||
            (isAccessibilityServiceEnabled(context) && isDndAccessGranted(context))

        when {
            sessionManager.isSessionActive() -> context.startActivity(
                Intent(context, BlockOverlayActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            )
            !permissionsOk -> context.startActivity(
                Intent(context, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_SHOW_PERMISSIONS, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            // startSession() salva già la durata e aggiorna il widget. Poi la
            // schermata della pausa, come il riquadro rapido: senza, il tocco
            // lasciava il launcher com'era e non si capiva se fosse partita
            // (segnalato).
            else -> {
                sessionManager.startSession(LastDuration.get(context))
                context.startActivity(
                    Intent(context, BlockOverlayActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                )
            }
        }
    }
}

class PauseWidgetProvider : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = PauseGlanceWidget()

    companion object {
        /**
         * Aggiorna tutti i widget istanziati sulla home screen.
         * Chiamato da SessionManager dopo startSession/endSession e da
         * LastDuration.save (la durata mostrata sul widget).
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
    }
}
