package com.calmotter.app.ui.theme

import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.calmotter.app.AppTheme
import com.calmotter.app.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * TODO.md "2.3": `values/colors.xml` (+ `values/themes.xml`) e
 * `CalmOtterTheme.kt`'s `light*` `ColorScheme`s define the same four
 * palettes twice, by hand — CLAUDE.md states the rule, but nothing checked
 * it. Drift here already caused one real bug (see
 * `specs/mascot-marks/design.md`).
 *
 * Every line of `CalmOtterTheme.kt`'s light schemes already carries a
 * `// @color/xxx` comment recording which XML resource it's supposed to
 * match — this test turns those comments into an assertion instead of
 * trusting them to stay true.
 *
 * **Only the light schemes**, on purpose: `CalmOtterTheme.kt`'s own class
 * doc is explicit that dark schemes come from a completely different
 * source (`values-night/themes.xml`'s old MaterialComponents colors, never
 * touched by the redesign), not from `values/colors.xml` — there is
 * nothing in this file to compare them against.
 *
 * Also intentionally out of scope: `surfaceBright` and
 * `surfaceContainerHigh`. Both are Compose-only decisions with no
 * `values/colors.xml` counterpart (see the class doc on
 * [dialogContainerFor] and the "stagno" comment on [SageLight]) — there is
 * nothing to compare them against either.
 */
@RunWith(RobolectricTestRunner::class)
class CalmOtterThemeColorSyncTest {

    private data class PaletteResources(
        val theme: AppTheme,
        val surface: Int,
        val background: Int,
        val onSurface: Int,
        val primary: Int,
        val onPrimary: Int,
        val accent: Int,
        val veil: Int,
    )

    private val palettes = listOf(
        PaletteResources(
            theme = AppTheme.SAGE,
            surface = R.color.sage_surface,
            background = R.color.sage_background,
            onSurface = R.color.sage_on_surface,
            primary = R.color.sage_primary,
            onPrimary = R.color.sage_on_primary,
            accent = R.color.sage_accent,
            veil = R.color.sage_veil,
        ),
        PaletteResources(
            theme = AppTheme.DUSK_SAND,
            surface = R.color.dusk_sand_surface,
            background = R.color.dusk_sand_background,
            onSurface = R.color.dusk_sand_on_surface,
            primary = R.color.dusk_sand_primary,
            onPrimary = R.color.dusk_sand_on_primary,
            accent = R.color.dusk_sand_accent,
            veil = R.color.dusk_sand_veil,
        ),
        PaletteResources(
            theme = AppTheme.DAWN_CLAY,
            surface = R.color.dawn_clay_surface,
            background = R.color.dawn_clay_background,
            onSurface = R.color.dawn_clay_on_surface,
            primary = R.color.dawn_clay_primary,
            onPrimary = R.color.dawn_clay_on_primary,
            accent = R.color.dawn_clay_accent,
            veil = R.color.dawn_clay_veil,
        ),
        PaletteResources(
            theme = AppTheme.DEEP_FOREST,
            surface = R.color.deep_forest_surface,
            background = R.color.deep_forest_background,
            onSurface = R.color.deep_forest_on_surface,
            primary = R.color.deep_forest_primary,
            onPrimary = R.color.deep_forest_on_primary,
            accent = R.color.deep_forest_accent,
            veil = R.color.deep_forest_veil,
        ),
    )

    @Test
    fun everyLightSchemeMatchesItsXmlPaletteRoleByRole() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        for (palette in palettes) {
            val scheme = lightSchemeFor(palette.theme)

            assertEquals(
                "${palette.theme}: surface vs @color/*_surface",
                ContextCompat.getColor(context, palette.surface),
                scheme.surface.toArgb(),
            )
            assertEquals(
                "${palette.theme}: background vs @color/*_background",
                ContextCompat.getColor(context, palette.background),
                scheme.background.toArgb(),
            )
            assertEquals(
                "${palette.theme}: onSurface vs @color/*_on_surface",
                ContextCompat.getColor(context, palette.onSurface),
                scheme.onSurface.toArgb(),
            )
            // onBackground riusa @color/*_on_surface (nessun
            // android:colorOnBackground esplicito) — stesso valore atteso
            // sia per onSurface sia per onBackground, come documentato sopra
            // SageLight in CalmOtterTheme.kt.
            assertEquals(
                "${palette.theme}: onBackground vs @color/*_on_surface",
                ContextCompat.getColor(context, palette.onSurface),
                scheme.onBackground.toArgb(),
            )
            assertEquals(
                "${palette.theme}: primary vs @color/*_primary",
                ContextCompat.getColor(context, palette.primary),
                scheme.primary.toArgb(),
            )
            assertEquals(
                "${palette.theme}: onPrimary vs @color/*_on_primary",
                ContextCompat.getColor(context, palette.onPrimary),
                scheme.onPrimary.toArgb(),
            )
            // secondary = "accent" e tertiary = "veil" nel design system —
            // vedi il commento di classe in CalmOtterTheme.kt.
            assertEquals(
                "${palette.theme}: secondary vs @color/*_accent",
                ContextCompat.getColor(context, palette.accent),
                scheme.secondary.toArgb(),
            )
            assertEquals(
                "${palette.theme}: tertiary vs @color/*_veil",
                ContextCompat.getColor(context, palette.veil),
                scheme.tertiary.toArgb(),
            )
            // error/onError sono condivisi da tutte le palette (m3_error/
            // m3_on_error), non un *_error per palette.
            assertEquals(
                "${palette.theme}: error vs @color/m3_error",
                ContextCompat.getColor(context, R.color.m3_error),
                scheme.error.toArgb(),
            )
            assertEquals(
                "${palette.theme}: onError vs @color/m3_on_error",
                ContextCompat.getColor(context, R.color.m3_on_error),
                scheme.onError.toArgb(),
            )
        }
    }
}
