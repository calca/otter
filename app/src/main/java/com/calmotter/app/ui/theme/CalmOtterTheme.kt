package com.calmotter.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.calmotter.app.AppTheme

/**
 * Schemi colore Material3 per le 3 palette dell'app (Sage/Lavender/Terracotta),
 * chiaro e scuro, ricavati 1:1 dai valori usati dal sistema di temi XML
 * esistente (values/colors.xml + values/themes.xml e le rispettive varianti
 * values-night/). Solo i ruoli effettivamente usati dalle schermate Compose
 * finora migrate sono impostati esplicitamente; gli altri ruoli Material3
 * restano ai default della libreria.
 */

// onError è bianco per tutte le palette/modalità: entrambe le tonalità di
// errore (#B85450 chiaro, #E07A78 scuro) sono rossi di saturazione media
// abbastanza scuri da garantire contrasto sufficiente con testo bianco.
private val OnError = Color.White

private val SageLight = lightColorScheme(
    background = Color(0xFFEAF0E8),
    surface = Color(0xFFF5F8F4),
    onBackground = Color(0xFF2C4A3E),
    onSurface = Color(0xFF557A6A),
    primary = Color(0xFF3D7A5C),
    onPrimary = Color(0xFFEAF0E8),
    error = Color(0xFFB85450),
    onError = OnError,
)

private val SageDark = darkColorScheme(
    background = Color(0xFF1A2B38),
    surface = Color(0xFF223040),
    onBackground = Color(0xFFD4E8DC),
    onSurface = Color(0xFF8AADA0),
    primary = Color(0xFF6BBFA0),
    onPrimary = Color(0xFF1A2B38),
    error = Color(0xFFE07A78),
    onError = OnError,
)

private val LavenderLight = lightColorScheme(
    background = Color(0xFFF0EDF8),
    surface = Color(0xFFF8F6FC),
    onBackground = Color(0xFF2E2547),
    onSurface = Color(0xFF6B5E8A),
    primary = Color(0xFF7C6FA0),
    onPrimary = Color(0xFFF0EDF8),
    error = Color(0xFFB85450),
    onError = OnError,
)

private val LavenderDark = darkColorScheme(
    background = Color(0xFF1E1A2E),
    surface = Color(0xFF262240),
    onBackground = Color(0xFFE0D8F5),
    onSurface = Color(0xFF9B8FC0),
    primary = Color(0xFFA99ED0),
    onPrimary = Color(0xFF1E1A2E),
    error = Color(0xFFE07A78),
    onError = OnError,
)

private val TerracottaLight = lightColorScheme(
    background = Color(0xFFF5EDEA),
    surface = Color(0xFFFBF6F4),
    onBackground = Color(0xFF3D2218),
    onSurface = Color(0xFF7A4A38),
    primary = Color(0xFFA0604A),
    onPrimary = Color(0xFFF5EDEA),
    error = Color(0xFFB85450),
    onError = OnError,
)

private val TerracottaDark = darkColorScheme(
    background = Color(0xFF2A1812),
    surface = Color(0xFF381F15),
    onBackground = Color(0xFFF0DDD8),
    onSurface = Color(0xFFC09080),
    primary = Color(0xFFC4927A),
    onPrimary = Color(0xFF2A1812),
    error = Color(0xFFE07A78),
    onError = OnError,
)

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
    MaterialTheme(colorScheme = colorScheme, content = content)
}
