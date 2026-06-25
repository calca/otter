package com.calmotter.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import java.util.Calendar

/**
 * Grafico a barre minimalista che mostra i minuti di pausa per ciascuno
 * degli ultimi 7 giorni. Disegnato interamente su Canvas — nessuna libreria
 * esterna. Lo stile segue la palette del tema corrente via attributi risolti
 * a runtime, così funziona con tutti i temi e in modalità notte.
 *
 * Layout:
 *  ┌──────────────────────────────────────────┐
 *  │  [bar] [bar] [bar] [bar] [bar] [bar] [bar] ← barre proporzionali
 *  │   Lu    Ma    Me    Gi    Ve    Sa   [og]  ← etichette giorno
 *  └──────────────────────────────────────────┘
 */
class WeeklyChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // Dati: indice 0 = 6 giorni fa, indice 6 = oggi
    var data: IntArray = IntArray(7)
        set(value) { field = value; invalidate() }

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val todayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val barRect = RectF()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, (w * 0.45f).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val dp = resources.displayMetrics.density

        // Risolve i colori dal tema corrente a runtime
        val accentColor  = resolveAttrColor(com.google.android.material.R.attr.colorPrimary)
        val surfaceColor = resolveAttrColor(com.google.android.material.R.attr.colorSurface)
        val textSecColor = android.util.TypedValue().also {
            context.theme.resolveAttribute(android.R.attr.textColorSecondary, it, true)
        }.data

        barPaint.color   = accentColor and 0x00FFFFFF or 0x55000000.toInt() // 33% opacità
        todayPaint.color = accentColor
        labelPaint.color = textSecColor
        valuePaint.color = accentColor

        labelPaint.textSize  = 11 * dp
        valuePaint.textSize  = 10 * dp

        val labelHeight  = 20 * dp
        val valueHeight  = 14 * dp
        val barAreaTop   = valueHeight + 4 * dp
        val barAreaBot   = h - labelHeight
        val barAreaH     = barAreaBot - barAreaTop
        val barCount     = 7
        val totalPadding = 16 * dp
        val barWidth     = (w - totalPadding) / barCount
        val barPadding   = barWidth * 0.2f
        val cornerRadius = 6 * dp
        val minBarH      = 4 * dp

        val maxVal = data.max().coerceAtLeast(1)

        // Etichette giorno — calcolate partendo da oggi
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -6)
        val dayLabels = Array(7) {
            val label = when (cal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY    -> "Lu"
                Calendar.TUESDAY   -> "Ma"
                Calendar.WEDNESDAY -> "Me"
                Calendar.THURSDAY  -> "Gi"
                Calendar.FRIDAY    -> "Ve"
                Calendar.SATURDAY  -> "Sa"
                else               -> "Do"
            }
            cal.add(Calendar.DAY_OF_YEAR, 1)
            label
        }

        for (i in 0 until barCount) {
            val cx = totalPadding / 2 + barWidth * i + barWidth / 2
            val isToday = i == barCount - 1
            val paint = if (isToday) todayPaint else barPaint

            // Barra
            val barH = (barAreaH * data[i].toFloat() / maxVal).coerceAtLeast(
                if (data[i] > 0) minBarH else 0f
            )
            barRect.set(
                cx - barWidth / 2 + barPadding,
                barAreaBot - barH,
                cx + barWidth / 2 - barPadding,
                barAreaBot
            )
            canvas.drawRoundRect(barRect, cornerRadius, cornerRadius, paint)

            // Valore sopra la barra (solo se > 0)
            if (data[i] > 0) {
                val valueText = formatMinutesShort(data[i])
                canvas.drawText(valueText, cx, barAreaBot - barH - 2 * dp, valuePaint)
            }

            // Etichetta giorno sotto
            val labelY = barAreaBot + labelHeight * 0.7f
            val lPaint = if (isToday) valuePaint else labelPaint
            canvas.drawText(if (isToday) "oggi" else dayLabels[i], cx, labelY, lPaint)
        }
    }

    private fun resolveAttrColor(attr: Int): Int {
        val tv = android.util.TypedValue()
        context.theme.resolveAttribute(attr, tv, true)
        return tv.data
    }

    private fun formatMinutesShort(m: Int) = if (m < 60) "${m}m" else "${m / 60}h"
}
