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
 */
@Composable
fun WeeklyChart(data: IntArray, modifier: Modifier = Modifier) {
    // I colori del tema si leggono solo in scope @Composable, non dentro la
    // lambda di disegno di Canvas (DrawScope) — vanno quindi catturati qui,
    // prima di entrare in Canvas { ... }.
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.onSurface
    val tintColor = primaryColor.copy(alpha = 0.33f) // barre non-oggi: ~33% opacità

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f / 0.45f)
    ) {
        val w = size.width
        val h = size.height
        // DrawScope implementa Density: stesso ruolo di
        // resources.displayMetrics.density nella View originale.
        val dp = density

        val labelPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = android.graphics.Paint.Align.CENTER
            color = secondaryColor.toArgb()
            textSize = 11 * dp
        }
        val valuePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
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

        val maxVal = data.max().coerceAtLeast(1)

        // Etichette giorno — calcolate partendo da 6 giorni fa fino a oggi.
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -6)
        val dayLabels = Array(barCount) {
            val label = when (cal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> "Lu"
                Calendar.TUESDAY -> "Ma"
                Calendar.WEDNESDAY -> "Me"
                Calendar.THURSDAY -> "Gi"
                Calendar.FRIDAY -> "Ve"
                Calendar.SATURDAY -> "Sa"
                else -> "Do"
            }
            cal.add(Calendar.DAY_OF_YEAR, 1)
            label
        }

        for (i in 0 until barCount) {
            val cx = totalPadding / 2 + barWidth * i + barWidth / 2
            val isToday = i == barCount - 1
            val barColor = if (isToday) primaryColor else tintColor

            // Barra: altezza proporzionale al valore, con un'altezza minima
            // visibile per i valori non nulli (0 resta invisibile).
            val barH = (barAreaH * data[i].toFloat() / maxVal).coerceAtLeast(
                if (data[i] > 0) minBarH else 0f
            )

            drawRoundRect(
                color = barColor,
                topLeft = Offset(cx - barWidth / 2 + barPadding, barAreaBot - barH),
                size = Size(barWidth - barPadding * 2, barH),
                cornerRadius = CornerRadius(cornerRadius, cornerRadius)
            )

            // Valore sopra la barra, solo se > 0.
            if (data[i] > 0) {
                val valueText = formatMinutesShort(data[i])
                drawContext.canvas.nativeCanvas.drawText(
                    valueText, cx, barAreaBot - barH - 2 * dp, valuePaint
                )
            }

            // Etichetta giorno sotto: "oggi" (stile valore, accento) per
            // l'ultima barra, sigla del giorno (stile muted) per le altre.
            val labelY = barAreaBot + labelHeight * 0.7f
            val lPaint = if (isToday) valuePaint else labelPaint
            drawContext.canvas.nativeCanvas.drawText(
                if (isToday) "oggi" else dayLabels[i], cx, labelY, lPaint
            )
        }
    }
}

private fun formatMinutesShort(m: Int) = if (m < 60) "${m}m" else "${m / 60}h"
