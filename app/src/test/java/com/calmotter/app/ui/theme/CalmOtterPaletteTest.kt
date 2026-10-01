package com.calmotter.app.ui.theme

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.calmotter.app.AppTheme
import com.calmotter.app.ThemeManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * I colori delle palette hanno **una sola fonte**: le risorse
 * `<palette>_<ruolo>` di values/colors.xml (chiaro) e values-night/colors.xml
 * (scuro). Compose le legge con `colorResource`, la finestra XML con gli
 * overlay di values/themes.xml — niente da tenere allineato a mano, che è ciò
 * che questo test sostituisce (prima un test confrontava i letterali di
 * CalmOtterTheme.kt con le risorse; ora i letterali non esistono più).
 *
 * Quello che resta da difendere è che una palette sia *completa* e
 * *leggibile*, in entrambe le modalità: una risorsa mancante in
 * values-night/ ricadrebbe in silenzio sul valore chiaro (testo scuro su
 * fondo scuro), e un CTA con poco contrasto è il difetto più facile da
 * introdurre cambiando un colore a occhio.
 */
@RunWith(RobolectricTestRunner::class)
class CalmOtterPaletteTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun colorsIn(night: Boolean): (Int) -> Color {
        val config = Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        val ctx = context.createConfigurationContext(config)
        return { res -> Color(ContextCompat.getColor(ctx, res)) }
    }

    private fun contrast(a: Color, b: Color): Double {
        val l1 = a.luminance().toDouble()
        val l2 = b.luminance().toDouble()
        return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
    }

    @Test
    fun everyPaletteResolvesInBothModes() {
        for (night in listOf(false, true)) {
            val color = colorsIn(night)
            for (theme in AppTheme.entries) {
                // getColor lancia se la risorsa non esiste.
                paletteFor(theme).all.forEach { color(it) }
            }
        }
    }

    @Test
    fun nightValuesAreDefinedNotInheritedFromDay() {
        // Se values-night/ non ridefinisce un ruolo, ricade sul valore chiaro.
        // Lo sfondo scuro deve essere scuro e quello chiaro chiaro, per ogni palette.
        val day = colorsIn(night = false)
        val night = colorsIn(night = true)
        for (theme in AppTheme.entries) {
            val p = paletteFor(theme)
            assertTrue("$theme: sfondo chiaro", day(p.background).luminance() > 0.8f)
            assertTrue("$theme: sfondo scuro", night(p.background).luminance() < 0.1f)
            assertNotEquals("$theme: primary uguale in chiaro e scuro", day(p.primary), night(p.primary))
        }
    }

    @Test
    fun textAndCallToActionStayReadableInBothModes() {
        for (night in listOf(false, true)) {
            val color = colorsIn(night)
            val mode = if (night) "scuro" else "chiaro"
            for (theme in AppTheme.entries) {
                val p = paletteFor(theme)
                assertTrue(
                    "$theme/$mode: onPrimary su primary (CTA)",
                    contrast(color(p.onPrimary), color(p.primary)) >= 4.5,
                )
                assertTrue(
                    "$theme/$mode: onSurface su surface",
                    contrast(color(p.onSurface), color(p.surface)) >= 4.5,
                )
                assertTrue(
                    "$theme/$mode: onBackground su background",
                    contrast(color(p.onBackground), color(p.background)) >= 4.5,
                )
                assertTrue(
                    "$theme/$mode: primary su surface (testo e icone colorati)",
                    contrast(color(p.primary), color(p.surface)) >= 3.0,
                )
            }
        }
    }

    @Test
    fun palettesAreVisiblyDistinct() {
        // La ragione di Acqua ferma: due palette non devono avere lo stesso
        // aspetto. Si confronta la tonalità del primary, non il valore
        // esatto, perché due verdi vicini sono "uguali" per chi li sceglie.
        // 15°: il caso da scoprire è Salvia contro la vecchia Foresta
        // profonda (~7° di distanza), mentre Sabbia e Argilla (~20°, marrone
        // contro terracotta) sono distinguibili e devono passare.
        val day = colorsIn(night = false)
        val hues = AppTheme.entries.map { it to hue(day(paletteFor(it).primary)) }
        for (i in hues.indices) for (j in i + 1 until hues.size) {
            val d = hueDistance(hues[i].second, hues[j].second)
            assertTrue("${hues[i].first} e ${hues[j].first} hanno tonalità troppo vicine ($d°)", d >= 15.0)
        }
    }

    @Test
    fun savedThemeKeyRoundTripsAndUnknownFallsBackToDefault() {
        for (theme in AppTheme.entries) {
            ThemeManager.setTheme(context, theme)
            assertEquals(theme, ThemeManager.getTheme(context))
        }
        assertEquals(AppTheme.SAGE, AppTheme.fromKey("non-esiste"))
    }

    private fun hue(c: Color): Double {
        val r = c.red
        val g = c.green
        val b = c.blue
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val d = max - min
        if (d == 0f) return 0.0
        val h = when (max) {
            r -> ((g - b) / d) % 6
            g -> (b - r) / d + 2
            else -> (r - g) / d + 4
        }
        return ((h * 60.0) + 360.0) % 360.0
    }

    private fun hueDistance(a: Double, b: Double): Double {
        val d = kotlin.math.abs(a - b) % 360.0
        return if (d > 180.0) 360.0 - d else d
    }
}
