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
 * Schemi colore Material3 per le 3 palette dell'app (Sage/Lavender/Terracotta),
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
    val surface = Color(0xFFF9F9F8)   // @color/sage_surface
    val primary = Color(0xFF0F5238)   // @color/sage_primary
    lightColorScheme(
        background = surface,             // @color/sage_background
        surface = surface,
        onBackground = Color(0xFF191C1C), // @color/sage_on_surface
        onSurface = Color(0xFF191C1C),    // @color/sage_on_surface
        primary = primary,
        onPrimary = Color(0xFFFFFFFF),    // @color/sage_on_primary
        error = Color(0xFFBA1A1A),        // @color/m3_error
        onError = OnError,
        surfaceContainerHigh = dialogContainerFor(primary, surface),
    )
}

private val SageDark = run {
    val surface = Color(0xFF223040)
    val primary = Color(0xFF6BBFA0)
    darkColorScheme(
        background = Color(0xFF1A2B38),
        surface = surface,
        onBackground = Color(0xFFD4E8DC),
        onSurface = Color(0xFF8AADA0),
        primary = primary,
        onPrimary = Color(0xFF1A2B38),
        error = Color(0xFFE07A78),
        onError = OnError,
        surfaceContainerHigh = dialogContainerFor(primary, surface),
    )
}

private val LavenderLight = run {
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
    )
}

private val LavenderDark = run {
    val surface = Color(0xFF262240)
    val primary = Color(0xFFA99ED0)
    darkColorScheme(
        background = Color(0xFF1E1A2E),
        surface = surface,
        onBackground = Color(0xFFE0D8F5),
        onSurface = Color(0xFF9B8FC0),
        primary = primary,
        onPrimary = Color(0xFF1E1A2E),
        error = Color(0xFFE07A78),
        onError = OnError,
        surfaceContainerHigh = dialogContainerFor(primary, surface),
    )
}

private val TerracottaLight = run {
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
    )
}

private val TerracottaDark = run {
    val surface = Color(0xFF381F15)
    val primary = Color(0xFFC4927A)
    darkColorScheme(
        background = Color(0xFF2A1812),
        surface = surface,
        onBackground = Color(0xFFF0DDD8),
        onSurface = Color(0xFFC09080),
        primary = primary,
        onPrimary = Color(0xFF2A1812),
        error = Color(0xFFE07A78),
        onError = OnError,
        surfaceContainerHigh = dialogContainerFor(primary, surface),
    )
}

private fun lightSchemeFor(appTheme: AppTheme): ColorScheme = when (appTheme) {
    AppTheme.SAGE -> SageLight
    AppTheme.LAVENDER -> LavenderLight
    AppTheme.TERRACOTTA -> TerracottaLight
}

private fun darkSchemeFor(appTheme: AppTheme): ColorScheme = when (appTheme) {
    AppTheme.SAGE -> SageDark
    AppTheme.LAVENDER -> LavenderDark
    AppTheme.TERRACOTTA -> TerracottaDark
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
    MaterialExpressiveTheme(colorScheme = colorScheme, content = content)
}
