package com.calmotter.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush

/**
 * Velo tenue di "primary" sullo sfondo (più percepibile in alto, sfuma verso
 * il neutro scendendo) — usato solo dalle due schermate "di apertura"
 * dell'app, Home e Onboarding. Dà un accenno di identità di marca (segue la
 * palette Sage/Lavender/Terracotta scelta) senza introdurre un blocco di
 * colore saturo: coerente con la scelta di tenere il resto della UI su
 * tinte tenui (vedi specs/home-and-settings "Living Pond redesign").
 *
 * Deliberatamente NON usato su Settings/Cronologia/schermata di blocco: è
 * una prima impressione, non un tema di sfondo applicato ovunque.
 */
@Composable
fun Modifier.calmBackground(): Modifier {
    val primary = MaterialTheme.colorScheme.primary
    return this.background(
        brush = Brush.verticalGradient(
            colors = listOf(primary.copy(alpha = 0.09f), primary.copy(alpha = 0.02f))
        )
    )
}
