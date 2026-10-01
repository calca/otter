package com.calmotter.app.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import com.calmotter.app.AppTheme
import com.calmotter.app.R
import com.calmotter.app.ui.mascot.OtterZenMark
import com.calmotter.app.ui.theme.CalmOtterTheme
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Genera le immagini per la scheda di Google Play in docs/store/: l'icona ad
 * alta risoluzione (512x512) e l'immagine in evidenza (1024x500), dagli
 * stessi vettori e dallo stesso font dell'app. Non è un controllo: gira solo
 * su richiesta, quando la grafica cambia —
 * `./gradlew testStableDebugUnitTest --tests "*StoreGraphicsTest*" -Pstore.graphics=true`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StoreGraphicsTest {

    @get:Rule val compose = createComposeRule()
    private val outDir = File("../docs/store/graphics")

    private fun enabled() = assumeTrue(System.getProperty("store.graphics") == "true")

    private fun save(bitmap: Bitmap, name: String) {
        outDir.mkdirs()
        File(outDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Icona di Play: quadrato pieno, senza maschera (la applica Play), dai due livelli dell'icona adattiva. */
    @Test
    fun playIcon() {
        enabled()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val icon = context.getDrawable(R.mipmap.ic_launcher) as AdaptiveIconDrawable
        val size = 512
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // I livelli adattivi sono 108dp con la zona sicura nei 72 centrali:
        // si disegnano ingranditi del 50%, così il quadrato mostra la stessa
        // porzione che mostra un launcher.
        val bleed = size / 4
        listOf(icon.background, icon.foreground).forEach { layer ->
            layer.setBounds(-bleed, -bleed, size + bleed, size + bleed)
            layer.draw(canvas)
        }
        save(bitmap, "icon-512.png")
    }

    /** Immagine in evidenza di Play: 1024x500, otter e nome sullo sfondo di Salvia. */
    @Test
    @Config(qualifiers = "w1024dp-h500dp-mdpi")
    fun featureGraphic() {
        enabled()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CalmOtterTheme(appTheme = AppTheme.SAGE) {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                        OtterZenMark(markSize = 220.dp)
                        Column {
                            Text("Calm Otter", fontSize = 76.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(
                                "Put the phone down, on purpose.",
                                fontSize = 30.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        save(compose.onRoot().captureToImage().asAndroidBitmap(), "feature-1024x500.png")
    }
}
