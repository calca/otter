package com.calmotter.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.calmotter.app.R
import java.util.Calendar

/**
 * Grafico a barre minimalista che mostra i minuti di pausa per ciascuno degli
 * ultimi 7 giorni (ultimo step della migrazione a Compose, sostituisce
 * WeeklyChartView — la View custom con onDraw/Canvas). [data]: indice 0 = 6
 * giorni fa, indice 6 = oggi.
 *
 * Le barre arrotondate usano il drawRoundRect nativo di [DrawScope]
 * (androidx.compose.ui.graphics.drawscope), più idiomatico del Paint-based
 * drawRoundRect originale. Le etichette di testo (valore sopra la barra,
 * giorno sotto) non hanno un equivalente Compose altrettanto semplice per il
 * testo centrato — DrawScope non ha un primitivo "drawText" pronto — quindi
 * riusiamo volutamente android.graphics.Paint/Canvas.drawText via
 * drawContext.canvas.nativeCanvas, esattamente come nella View originale
 * (stessa Paint.Align.CENTER, stesse dimensioni in dp convertiti in px).
 *
 * Sizing: altezza = larghezza * 0.45 nella View originale (via onMeasure).
 * Modifier.aspectRatio(ratio) vincola width/height = ratio, quindi qui
 * ratio = 1f / 0.45f riproduce esattamente lo stesso rapporto.
 *
 * **Un giorno vuoto disegna comunque un piccolo segno**, non il vuoto
 * assoluto di prima (`barH` restava 0, letteralmente nulla sullo schermo).
 * Confrontato col mockup ("Calm Otter - History & Journey", progetto Stitch
 * 3158702940609906617), che per i giorni senza sessioni disegna una pillola
 * bassa e grigia invece di lasciare la colonna vuota — un giorno "a riposo"
 * si vede ancora, non sparisce dal grafico. Stessa tinta già usata in
 * questo file per la traccia di [WeeklyGoalSection]'s
 * `LinearProgressIndicator` (`onSurface` a bassa opacità, non
 * `surfaceVariant`: quel ruolo non è personalizzato per palette in
 * CalmOtterTheme.kt, la stessa trappola già documentata più volte in questo
 * codebase).
 */
/**
 * TODO.md "4.2": disegnata con Canvas, quindi altrimenti invisibile a
 * TalkBack — un grafico senza alternativa testuale è un dato che chi usa
 * uno screen reader non può proprio raggiungere. [accessibilityLabel] non
 * inventa una frase nuova: è pensato per ricevere la stessa
 * [summaryText]/`weekly_summary_*` già calcolata e mostrata come `Text`
 * subito sotto questo grafico in [WeekOverviewCard] — non c'è un secondo
 * riepilogo da scrivere e mantenere sincronizzato con quello visibile,
 * solo da rendere raggiungibile anche da qui.
 */
@Composable
fun WeeklyChart(data: IntArray, modifier: Modifier = Modifier, accessibilityLabel: String? = null) {
    // I colori del tema si leggono solo in scope @Composable, non dentro la
    // lambda di disegno di Canvas (DrawScope) — vanno quindi catturati qui,
    // prima di entrare in Canvas { ... }.
    //
    // **Le barre con dati usano `secondary`, non `primary` a bassa
    // opacità.** Segnalato ("il colore dell'istogramma è uguale [al
    // mockup]?") — non lo era: ogni barra, oggi compreso, era una
    // variazione di opacità di `primary`, mentre il mockup tinge le barre
    // dei giorni passati di `secondary` (`#b5ccb8` nelle palette verdi, il
    // colore che CalmOtterTheme.kt riserva a "la mascotte e i controlli",
    // non a onSurface come faceva la vecchia variabile di questo nome) e
    // riserva `primary` a un solo segno: "oggi". `primary` resta quindi
    // solo sulla barra e l'etichetta di oggi — l'unica cosa che questa
    // riga deve far risaltare — mentre valori e barre degli altri giorni
    // prendono `secondary`, piena e non sbiadita, come nel mockup.
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val mutedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

    // Risolte qui (scope @Composable) e non dentro Canvas{}, stesso motivo dei
    // colori sopra — vale anche per stringResource()/stringArrayResource().
    // Convenzione di indicizzazione dell'array: Calendar.DAY_OF_WEEK - 1.
    // Prima di questo fix i giorni erano hardcoded in italiano ("Lu"/"Ma"/...)
    // e "oggi" era una stringa letterale, quindi questo grafico restava
    // sempre in italiano indipendentemente dalla lingua dell'app (a
    // differenza del grafico gemello che la Home aveva allora, già
    // correttamente localizzato — quello è poi stato rimosso, vedi
    // [SessionsSummaryLink] in MainScreen.kt: le barre degli ultimi 7 giorni
    // vivono ormai solo qui, in Cronologia).
    val weekdayInitials = stringArrayResource(R.array.weekday_initials)
    val todayLabel = stringResource(R.string.weekly_chart_today)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f / 0.45f)
            .then(
                if (accessibilityLabel != null) {
                    Modifier.semantics { contentDescription = accessibilityLabel }
                } else {
                    Modifier
                }
            )
    ) {
        val w = size.width
        val h = size.height
        // DrawScope implementa Density: stesso ruolo di
        // resources.displayMetrics.density nella View originale.
        val dp = density

        val labelPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = android.graphics.Paint.Align.CENTER
            color = mutedColor.toArgb()
            textSize = 11 * dp
        }
        // Valore sopra una barra con dati: `secondary`, come la barra
        // stessa. Separato da [todayPaint] apposta — prima erano lo stesso
        // Paint, quindi anche il valore di un giorno passato ("1m") finiva
        // tinto di `primary` come "oggi", che è l'unica cosa a cui
        // `primary` dovrebbe richiamare l'occhio su questa riga.
        val valuePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = android.graphics.Paint.Align.CENTER
            isFakeBoldText = true
            color = secondaryColor.toArgb()
            textSize = 10 * dp
        }
        val todayPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = android.graphics.Paint.Align.CENTER
            isFakeBoldText = true
            color = primaryColor.toArgb()
            textSize = 10 * dp
        }

        val labelHeight = 20 * dp
        val valueHeight = 14 * dp
        val barAreaTop = valueHeight + 4 * dp
        val barAreaBot = h - labelHeight
        val barAreaH = barAreaBot - barAreaTop
        val barCount = 7
        val totalPadding = 16 * dp
        val barWidth = (w - totalPadding) / barCount
        val barPadding = barWidth * 0.2f
        val cornerRadius = 6 * dp
        val minBarH = 4 * dp
        // Il segno di un giorno vuoto: una pillola bassa e ferma, non una
        // barra minima nel colore della barra vera — deve leggersi come
        // "niente qui", non come "pochissimo qui". `CornerRadius` pari a
        // metà della sua stessa altezza la rende una pillola indipendente
        // da [cornerRadius], che invece è tarato sulle barre alte.
        val emptyBarH = 3 * dp
        val emptyBarColor = mutedColor.copy(alpha = 0.14f)

        val maxVal = data.max().coerceAtLeast(1)

        // Etichette giorno — calcolate partendo da 6 giorni fa fino a oggi.
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -6)
        val dayLabels = Array(barCount) {
            val label = weekdayInitials.getOrElse(cal.get(Calendar.DAY_OF_WEEK) - 1) { "" }
            cal.add(Calendar.DAY_OF_YEAR, 1)
            label
        }

        for (i in 0 until barCount) {
            val cx = totalPadding / 2 + barWidth * i + barWidth / 2
            val isToday = i == barCount - 1
            val barColor = if (isToday) primaryColor else secondaryColor

            if (data[i] > 0) {
                // Barra vera: altezza proporzionale al valore, con
                // un'altezza minima visibile.
                val barH = (barAreaH * data[i].toFloat() / maxVal).coerceAtLeast(minBarH)
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(cx - barWidth / 2 + barPadding, barAreaBot - barH),
                    size = Size(barWidth - barPadding * 2, barH),
                    cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                )
                val valueText = formatMinutesShort(data[i])
                drawContext.canvas.nativeCanvas.drawText(
                    valueText, cx, barAreaBot - barH - 2 * dp, if (isToday) todayPaint else valuePaint
                )
            } else {
                // Giorno senza sessioni: non più il vuoto assoluto di prima
                // (colonna letteralmente bianca) ma una pillola grigia bassa,
                // segnalato contro il mockup — vedi il commento di classe.
                // Nessun valore sopra: non c'è un numero da scrivere.
                drawRoundRect(
                    color = emptyBarColor,
                    topLeft = Offset(cx - barWidth / 2 + barPadding, barAreaBot - emptyBarH),
                    size = Size(barWidth - barPadding * 2, emptyBarH),
                    cornerRadius = CornerRadius(emptyBarH / 2, emptyBarH / 2)
                )
            }

            // Etichetta giorno sotto: [todayLabel] (`primary`, l'unico segno
            // di questa riga) per l'ultima barra, sigla del giorno (muted)
            // per le altre.
            val labelY = barAreaBot + labelHeight * 0.7f
            val lPaint = if (isToday) todayPaint else labelPaint
            drawContext.canvas.nativeCanvas.drawText(
                if (isToday) todayLabel else dayLabels[i], cx, labelY, lPaint
            )
        }
    }
}

private fun formatMinutesShort(m: Int) = if (m < 60) "${m}m" else "${m / 60}h"
