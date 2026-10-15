package com.calmotter.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Testo secondario (sottotitoli, suggerimenti, etichette di sezione,
 * pillole non scelte): `onSurface` attenuato come prima, ma mai sotto
 * l'opacità che tiene il contrasto WCAG AA (4.5:1) su ogni sfondo dell'app —
 * pagina, card tinte di `primary`, pillole di `tertiary` — in tutte e
 * quattro le palette. I minimi sono calcolati sui colori di colors.xml e
 * controllati da `CalmOtterPaletteTest`.
 *
 * Il problema era soprattutto in tema scuro: lo stesso 0.6 che in chiaro
 * dava 4.4:1, su fondo scuro scendeva a 3.4:1.
 */
fun ColorScheme.muted(alpha: Float): Color =
    onSurface.copy(alpha = maxOf(alpha, mutedMinAlpha(isDark = background.luminance() < 0.5f)))

internal fun mutedMinAlpha(isDark: Boolean): Float = if (isDark) 0.80f else 0.66f
