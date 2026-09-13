package com.calmotter.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush

/**
 * Velo tenue di "primary" sullo sfondo (più percepibile in alto, sfuma verso
 * il neutro scendendo). Dà un accenno di identità di marca (segue la
 * palette Sage/Lavender/Terracotta scelta) senza introdurre un blocco di
 * colore saturo: coerente con la scelta di tenere il resto della UI su
 * tinte tenui (vedi specs/home-and-settings "Living Pond redesign").
 *
 * Usato da Home, Onboarding, BlockScreen, e le schermate di Pausa di gruppo
 * (GroupPauseHostScreen/GroupPauseJoinScreen/GroupPauseCountdownScreen, vedi
 * specs/group-pause/) — stessa categoria "di rito"/apertura di una pausa,
 * non amministrazione. Deliberatamente NON usato su Settings/Cronologia:
 * quelle restano schermate di navigazione/amministrazione, non "schermate
 * di apertura" o di sessione attiva.
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
