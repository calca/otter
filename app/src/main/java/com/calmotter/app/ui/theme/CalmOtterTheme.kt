package com.calmotter.app.ui.theme

import androidx.annotation.ColorRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.colorResource
import com.calmotter.app.AppTheme
import com.calmotter.app.R

/**
 * I colori di una palette, come *riferimenti* alle risorse di
 * values/colors.xml — che è l'unica fonte: i valori non sono ripetuti in
 * Kotlin. Ogni risorsa ha il suo gemello in values-night/colors.xml, e
 * `colorResource` prende da solo quello giusto per la modalità corrente;
 * la finestra XML (overlay in values/themes.xml) punta alle stesse risorse.
 * Non c'è quindi niente da tenere allineato a mano: CalmOtterPaletteTest
 * controlla che ogni palette definisca tutti i ruoli, chiaro e scuro, e che
 * il contrasto regga.
 *
 * `secondary` e `tertiary` **sono impostati**, a differenza degli altri ruoli
 * lasciati ai default: nel design system del redesign non sono variazioni di
 * `primary` ma due colori con un compito preciso — `secondary` (accent) è la
 * mascotte e i controlli, `tertiary` (veil) sono gli anelli dello stagno e i
 * riempimenti tenui. Impostato anche `surfaceBright`, il disco centrale dello
 * stagno: in chiaro è bianco pieno (nel mockup è più chiaro dello sfondo, non
 * uguale), in scuro è la superficie schiarita — cioè esattamente ciò che quel
 * ruolo M3 significa in entrambi i temi. Derivarli da `primary` con
 * l'opacità, come si faceva prima, dava grigi-verdi al posto dei verdi del
 * design (segnalato guardando la Home: "non sono i colori di Stitch").
 *
 * Solo i ruoli effettivamente usati dalle schermate Compose sono impostati
 * esplicitamente (background/surface/onBackground/onSurface/primary/
 * onPrimary/error/onError, più `surfaceContainerHigh` per lo sfondo di
 * `AlertDialog` — vedi [dialogContainerFor]); gli altri ruoli Material3
 * (`surfaceVariant`, `primaryContainer`, ecc.) restano ai default della
 * libreria — se un componente nuovo legge uno di questi e sembra sbagliato
 * a prescindere dalla palette scelta, è probabilmente questo, non un bug nel
 * componente stesso.
 */
internal class PaletteColors(
    @ColorRes val background: Int,
    @ColorRes val surface: Int,
    @ColorRes val surfaceBright: Int,
    @ColorRes val onBackground: Int,
    @ColorRes val onSurface: Int,
    @ColorRes val primary: Int,
    @ColorRes val onPrimary: Int,
    @ColorRes val accent: Int,
    @ColorRes val veil: Int,
) {
    /** Tutte le risorse della palette, per i controlli di CalmOtterPaletteTest. */
    val all get() = listOf(background, surface, surfaceBright, onBackground, onSurface, primary, onPrimary, accent, veil)
}

private val SagePalette = PaletteColors(
    R.color.sage_background, R.color.sage_surface, R.color.sage_surface_bright,
    R.color.sage_on_background, R.color.sage_on_surface,
    R.color.sage_primary, R.color.sage_on_primary, R.color.sage_accent, R.color.sage_veil,
)
private val StillWaterPalette = PaletteColors(
    R.color.still_water_background, R.color.still_water_surface, R.color.still_water_surface_bright,
    R.color.still_water_on_background, R.color.still_water_on_surface,
    R.color.still_water_primary, R.color.still_water_on_primary, R.color.still_water_accent, R.color.still_water_veil,
)
private val DuskSandPalette = PaletteColors(
    R.color.dusk_sand_background, R.color.dusk_sand_surface, R.color.dusk_sand_surface_bright,
    R.color.dusk_sand_on_background, R.color.dusk_sand_on_surface,
    R.color.dusk_sand_primary, R.color.dusk_sand_on_primary, R.color.dusk_sand_accent, R.color.dusk_sand_veil,
)
private val DawnClayPalette = PaletteColors(
    R.color.dawn_clay_background, R.color.dawn_clay_surface, R.color.dawn_clay_surface_bright,
    R.color.dawn_clay_on_background, R.color.dawn_clay_on_surface,
    R.color.dawn_clay_primary, R.color.dawn_clay_on_primary, R.color.dawn_clay_accent, R.color.dawn_clay_veil,
)

internal fun paletteFor(appTheme: AppTheme): PaletteColors = when (appTheme) {
    AppTheme.SAGE -> SagePalette
    AppTheme.STILL_WATER -> StillWaterPalette
    AppTheme.DUSK_SAND -> DuskSandPalette
    AppTheme.DAWN_CLAY -> DawnClayPalette
}

// onError è bianco per tutte le palette/modalità: entrambe le tonalità di
// errore (m3_error/#ba1a1a chiaro, #E07A78 scuro) sono rosse abbastanza
// scure da garantire contrasto sufficiente con testo bianco.
private val OnError = Color.White

// Ruoli letti da AlertDialog (e potenzialmente da altri componenti M3 in
// futuro) per lo sfondo del dialog di default — non impostarlo qui vuol
// dire cadere sul viola/rosa di base di Material3 a prescindere dalla
// palette scelta, la stessa "trappola" già documentata per surfaceVariant/
// primaryContainer in CLAUDE.md/specs/mascot-marks/specs/home-and-settings
// (in particolare per questo stesso AlertDialog, prima accettato come "tono
// neutro di sistema" quando c'era un solo dialog in tutta l'app — bug reale
// una volta che i dialog sono diventati cinque, vedi
// specs/home-and-settings/design.md). Stesso ingrediente già usato per gli
// sfondi delle card di Settings/Home (`primary.copy(alpha = 0.06f-0.09f)`),
// qui composito su `surface` con `compositeOver` per ottenere un colore
// pieno invece di uno semi-trasparente — un `AlertDialog` con `containerColor`
// ancora parzialmente trasparente lascerebbe intravedere lo scrim scuro
// dietro di sé.
private fun dialogContainerFor(primary: Color, surface: Color): Color =
    primary.copy(alpha = 0.08f).compositeOver(surface)

/**
 * Applica lo schema colore Material3 della palette scelta dall'utente
 * ([appTheme], da [com.calmotter.app.ThemeManager]), chiaro o scuro a seconda
 * dell'impostazione di sistema — gli stessi valori che l'overlay XML dà alla
 * finestra, perché sono le stesse risorse (vedi [PaletteColors]).
 *
 * Il contenuto è avvolto in [MaterialExpressiveTheme] (non il semplice
 * MaterialTheme): applica Material 3 Expressive — motion scheme a molla,
 * forme e tipografia "expressive" — a ogni componente M3 dell'app senza
 * doverlo impostare schermata per schermata. Richiede material3 1.5.0-alpha
 * (compose-bom-alpha in app/build.gradle.kts): in material3 1.4.0 stabile
 * MaterialExpressiveTheme e MotionScheme.expressive()/standard() sono
 * `internal` in Kotlin, verificato decompilando il jar.
 */
@Composable
fun CalmOtterTheme(
    appTheme: AppTheme,
    content: @Composable () -> Unit
) {
    val palette = paletteFor(appTheme)
    val surface = colorResource(palette.surface)
    val primary = colorResource(palette.primary)
    // onError è bianco per tutte le palette/modalità: entrambe le tonalità di
    // errore (m3_error chiaro/scuro) sono rosse abbastanza scure da garantire
    // contrasto sufficiente con testo bianco.
    val colorScheme: ColorScheme = if (isSystemInDarkTheme()) {
        darkColorScheme(
            background = colorResource(palette.background),
            surface = surface,
            onBackground = colorResource(palette.onBackground),
            onSurface = colorResource(palette.onSurface),
            primary = primary,
            onPrimary = colorResource(palette.onPrimary),
            error = colorResource(R.color.m3_error),
            onError = OnError,
            surfaceContainerHigh = dialogContainerFor(primary, surface),
            secondary = colorResource(palette.accent),
            tertiary = colorResource(palette.veil),
            surfaceBright = colorResource(palette.surfaceBright),
        )
    } else {
        lightColorScheme(
            background = colorResource(palette.background),
            surface = surface,
            onBackground = colorResource(palette.onBackground),
            onSurface = colorResource(palette.onSurface),
            primary = primary,
            onPrimary = colorResource(palette.onPrimary),
            error = colorResource(R.color.m3_error),
            onError = OnError,
            surfaceContainerHigh = dialogContainerFor(primary, surface),
            secondary = colorResource(palette.accent),
            tertiary = colorResource(palette.veil),
            surfaceBright = colorResource(palette.surfaceBright),
        )
    }
    MaterialExpressiveTheme(colorScheme = colorScheme, typography = CalmOtterTypography, content = content)
}
