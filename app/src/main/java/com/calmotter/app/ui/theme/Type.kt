package com.calmotter.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import com.calmotter.app.R

/**
 * Plus Jakarta Sans, il carattere del redesign (design system "Calm Otter
 * Sanctuary"). Un solo file per stile: sono **font variabili**, quindi i
 * pesi non sono file separati ma istanze dello stesso asse `wght` — 360KB
 * in tutto per otto pesi, invece di un file per peso.
 *
 * `FontVariation` richiede API 26, che è il minSdk del progetto: nessun
 * ramo di compatibilità da scrivere.
 *
 * Il corsivo è un file suo (`plus_jakarta_sans_italic`), dichiarato qui
 * dentro la stessa famiglia: così `fontStyle = FontStyle.Italic` — usato
 * dalle frasi riflessive di BlockScreen e dello stato vuoto della
 * cronologia — prende il vero corsivo disegnato, non l'inclinazione
 * sintetica che Android applicherebbe da sé.
 *
 * Licenza: SIL Open Font License 1.1, vedi THIRD_PARTY_LICENSES.md.
 */
private fun jakarta(weight: Int, italic: Boolean = false) = Font(
    resId = if (italic) R.font.plus_jakarta_sans_italic else R.font.plus_jakarta_sans,
    weight = FontWeight(weight),
    style = if (italic) FontStyle.Italic else FontStyle.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val PlusJakartaSans = FontFamily(
    jakarta(400), jakarta(500), jakarta(600), jakarta(700),
    jakarta(400, italic = true), jakarta(500, italic = true),
    jakarta(600, italic = true), jakarta(700, italic = true),
)

/**
 * Le dimensioni restano quelle di Material 3: le schermate dell'app fissano
 * quasi sempre `fontSize` per conto proprio, quindi riscalare qui
 * cambierebbe solo i componenti di libreria (bottoni, dialog, campi) e li
 * scollegherebbe dal resto. Quello che cambia è ciò che il design system
 * prescrive davvero e che M3 non dà: il carattere, i pesi dei titoli
 * (700/600 invece di 400) e la spaziatura negativa che li tiene compatti.
 */
private val Default = Typography()

val CalmOtterTypography = Typography(
    displayLarge = Default.displayLarge.copy(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em),
    displayMedium = Default.displayMedium.copy(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em),
    displaySmall = Default.displaySmall.copy(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em),
    headlineLarge = Default.headlineLarge.copy(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, letterSpacing = (-0.015).em),
    headlineMedium = Default.headlineMedium.copy(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, letterSpacing = (-0.015).em),
    headlineSmall = Default.headlineSmall.copy(fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.01).em),
    titleLarge = Default.titleLarge.copy(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, letterSpacing = (-0.015).em),
    titleMedium = Default.titleMedium.copy(fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold),
    titleSmall = Default.titleSmall.copy(fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold),
    bodyLarge = Default.bodyLarge.copy(fontFamily = PlusJakartaSans),
    bodyMedium = Default.bodyMedium.copy(fontFamily = PlusJakartaSans),
    bodySmall = Default.bodySmall.copy(fontFamily = PlusJakartaSans),
    labelLarge = Default.labelLarge.copy(fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold),
    labelMedium = Default.labelMedium.copy(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Medium),
    labelSmall = Default.labelSmall.copy(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Medium),
)
