package com.calmotter.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.calmotter.app.AppTheme

/**
 * `secondary` e `tertiary` **sono impostati**, a differenza degli altri ruoli
 * lasciati ai default: nel design system del redesign non sono variazioni di
 * `primary` ma due colori con un compito preciso — `secondary` (#8fa693 nelle
 * palette verdi) è la mascotte e i controlli, `tertiary` (#d5e0d5) sono gli
 * anelli dello stagno e i riempimenti tenui. Derivarli da `primary` con
 * l'opacità, come faceva questo file prima, dava grigi-verdi al posto dei
 * verdi del design (segnalato guardando la Home: "non sono i colori di
 * Stitch"). Vivono anche in values/colors.xml come `*_accent` / `*_veil`,
 * per i punti che leggono le risorse invece del tema Compose.
 *
 * Schemi colore Material3 per le 4 palette dell'app (Sage/DuskSand/DawnClay),
 * chiaro e scuro. Le due modalità NON condividono la stessa fonte XML, quindi
 * NON aspettarti che i valori chiaro/scuro siano una semplice variazione di
 * tonalità l'uno dell'altro:
 * - Chiaro: ricavato 1:1 da values/colors.xml + values/themes.xml (schema
 *   Material3 "Desing.md"/redesign).
 * - Scuro: ricavato 1:1 da values-night/themes.xml, che non è mai stato
 *   aggiornato dal redesign e resta sullo schema MaterialComponents
 *   precedente (colori hardcoded lì, non in un colors.xml notturno).
 * Se aggiorni una palette in un file XML, aggiorna anche qui a mano — nulla
 * lega automaticamente questi valori a quelli letti dal sistema di temi
 * XML/AppCompat (vedi CLAUDE.md e specs/multi-theme-system/design.md).
 * Solo i ruoli effettivamente usati dalle schermate Compose finora migrate
 * sono impostati esplicitamente (background/surface/onBackground/onSurface/
 * primary/onPrimary/error/onError, più `surfaceContainerHigh` per lo sfondo
 * di `AlertDialog` — vedi [dialogContainerFor]); gli altri ruoli Material3
 * (`surfaceVariant`, `primaryContainer`, ecc.) restano ai default della
 * libreria — se un componente nuovo legge uno di questi e sembra sbagliato
 * a prescindere dalla palette scelta, è probabilmente questo, non un bug nel
 * componente stesso.
 *
 * Il contenuto è avvolto in [MaterialExpressiveTheme] (non il semplice
 * MaterialTheme): applica Material 3 Expressive — motion scheme a molla,
 * forme e tipografia "expressive" — a ogni componente M3 dell'app senza
 * doverlo impostare schermata per schermata. Richiede material3 1.5.0-alpha
 * (compose-bom-alpha in app/build.gradle.kts): in material3 1.4.0 stabile
 * MaterialExpressiveTheme e MotionScheme.expressive()/standard() sono
 * `internal` in Kotlin, verificato decompilando il jar.
 */

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

// Chiaro: sfondo e surface coincidono nella palette M3 attuale (nessun
// android:colorOnBackground esplicito in values/themes.xml), quindi onBackground
// riusa lo stesso valore di onSurface (@color/sage_on_surface) anziché un
// default M3 indovinato.
private val SageLight = run {
    val surface = Color(0xFFF9FAF6)   // @color/sage_surface
    val primary = Color(0xFF0F5238)   // @color/sage_primary
    lightColorScheme(
        background = surface,             // @color/sage_background
        surface = surface,
        onBackground = Color(0xFF191C1A), // @color/sage_on_surface
        onSurface = Color(0xFF191C1A),    // @color/sage_on_surface
        primary = primary,
        onPrimary = Color(0xFFFFFFFF),    // @color/sage_on_primary
        error = Color(0xFFBA1A1A),        // @color/m3_error
        onError = OnError,
        surfaceContainerHigh = dialogContainerFor(primary, surface),
        secondary = Color(0xFF8FA693),   // @color/*_accent
        tertiary = Color(0xFFD5E0D5),     // @color/*_veil
    )
}

private val SageDark = run {
    val surface = Color(0xFF151D17)
    val primary = Color(0xFF9ED3A8)
    darkColorScheme(
        background = Color(0xFF0E1510),
        surface = surface,
        onBackground = Color(0xFFDDE5DC),
        onSurface = Color(0xFF9AB3A3),
        primary = primary,
        onPrimary = Color(0xFF02391A),
        error = Color(0xFFE07A78),
        onError = OnError,
        surfaceContainerHigh = dialogContainerFor(primary, surface),
        secondary = Color(0xFF9ED3A8),   // @color/*_accent
        tertiary = Color(0xFF2B3A30),     // @color/*_veil
    )
}

private val DuskSandLight = run {
    val surface = Color(0xFFFDF7FF)   // @color/lavender_surface
    val primary = Color(0xFF7C6FA0)   // @color/lavender_primary
    lightColorScheme(
        background = surface,             // @color/lavender_background
        surface = surface,
        onBackground = Color(0xFF1C1B1F), // @color/lavender_on_surface
        onSurface = Color(0xFF1C1B1F),    // @color/lavender_on_surface
        primary = primary,
        onPrimary = Color(0xFFFFFFFF),    // @color/lavender_on_primary
        error = Color(0xFFBA1A1A),        // @color/m3_error
        onError = OnError,
        surfaceContainerHigh = dialogContainerFor(primary, surface),
        secondary = Color(0xFF8F7256),   // @color/*_accent
        tertiary = Color(0xFFE6D2B8),     // @color/*_veil
    )
}

private val DuskSandDark = run {
    val surface = Color(0xFF1C1711)
    val primary = Color(0xFFE3C39A)
    darkColorScheme(
        background = Color(0xFF15110B),
        surface = surface,
        onBackground = Color(0xFFECE5DA),
        onSurface = Color(0xFFBDA98D),
        primary = primary,
        onPrimary = Color(0xFF3A2A16),
        error = Color(0xFFE07A78),
        onError = OnError,
        surfaceContainerHigh = dialogContainerFor(primary, surface),
        secondary = Color(0xFFC9A880),   // @color/*_accent
        tertiary = Color(0xFF33291D),     // @color/*_veil
    )
}

private val DawnClayLight = run {
    val surface = Color(0xFFFFFBFF)   // @color/terracotta_surface
    val primary = Color(0xFFA0604A)   // @color/terracotta_primary
    lightColorScheme(
        background = surface,             // @color/terracotta_background
        surface = surface,
        onBackground = Color(0xFF201A18), // @color/terracotta_on_surface
        onSurface = Color(0xFF201A18),    // @color/terracotta_on_surface
        primary = primary,
        onPrimary = Color(0xFFFFFFFF),    // @color/terracotta_on_primary
        error = Color(0xFFBA1A1A),        // @color/m3_error
        onError = OnError,
        surfaceContainerHigh = dialogContainerFor(primary, surface),
        secondary = Color(0xFF965A4F),   // @color/*_accent
        tertiary = Color(0xFFE9C3B8),     // @color/*_veil
    )
}

private val DawnClayDark = run {
    val surface = Color(0xFF1E1512)
    val primary = Color(0xFFF0B3A2)
    darkColorScheme(
        background = Color(0xFF170F0C),
        surface = surface,
        onBackground = Color(0xFFEFE2DD),
        onSurface = Color(0xFFC79B8D),
        primary = primary,
        onPrimary = Color(0xFF3E1A12),
        error = Color(0xFFE07A78),
        onError = OnError,
        surfaceContainerHigh = dialogContainerFor(primary, surface),
        secondary = Color(0xFFD79C8C),   // @color/*_accent
        tertiary = Color(0xFF36241F),     // @color/*_veil
    )
}

private val DeepForestLight = run {
    val surface = Color(0xFFF8F9F5)   // @color/deep_forest_surface
    val primary = Color(0xFF1B3B2B)   // @color/deep_forest_primary
    lightColorScheme(
        background = surface,             // @color/deep_forest_background
        surface = surface,
        onBackground = Color(0xFF1A1C1A), // @color/deep_forest_on_surface
        onSurface = Color(0xFF1A1C1A),    // @color/deep_forest_on_surface
        primary = primary,
        onPrimary = Color(0xFFFFFFFF),    // @color/deep_forest_on_primary
        error = Color(0xFFBA1A1A),        // @color/m3_error
        onError = OnError,
        surfaceContainerHigh = dialogContainerFor(primary, surface),
        secondary = Color(0xFF8FA693),   // @color/*_accent
        tertiary = Color(0xFFD5E0D5),     // @color/*_veil
    )
}

private val DeepForestDark = run {
    val surface = Color(0xFF101711)
    val primary = Color(0xFFABCFB8)
    darkColorScheme(
        background = Color(0xFF0B110D),
        surface = surface,
        onBackground = Color(0xFFE0EAE2),
        onSurface = Color(0xFF94AC9C),
        primary = primary,
        onPrimary = Color(0xFF00281A),
        error = Color(0xFFE07A78),
        onError = OnError,
        surfaceContainerHigh = dialogContainerFor(primary, surface),
        secondary = Color(0xFFABCFB8),   // @color/*_accent
        tertiary = Color(0xFF26332B),     // @color/*_veil
    )
}

private fun lightSchemeFor(appTheme: AppTheme): ColorScheme = when (appTheme) {
    AppTheme.SAGE -> SageLight
    AppTheme.DUSK_SAND -> DuskSandLight
    AppTheme.DAWN_CLAY -> DawnClayLight
    AppTheme.DEEP_FOREST -> DeepForestLight
}

private fun darkSchemeFor(appTheme: AppTheme): ColorScheme = when (appTheme) {
    AppTheme.SAGE -> SageDark
    AppTheme.DUSK_SAND -> DuskSandDark
    AppTheme.DAWN_CLAY -> DawnClayDark
    AppTheme.DEEP_FOREST -> DeepForestDark
}

/**
 * Applica lo schema colore Material3 corrispondente alla palette scelta
 * dall'utente ([appTheme], da [com.calmotter.app.ThemeManager]), chiaro o
 * scuro a seconda dell'impostazione di sistema — coerente con il
 * comportamento del tema XML esistente, che segue anch'esso il sistema
 * tramite le risorse values-night/.
 */
@Composable
fun CalmOtterTheme(
    appTheme: AppTheme,
    content: @Composable () -> Unit
) {
    val colorScheme = if (isSystemInDarkTheme()) darkSchemeFor(appTheme) else lightSchemeFor(appTheme)
    MaterialExpressiveTheme(colorScheme = colorScheme, typography = CalmOtterTypography, content = content)
}
