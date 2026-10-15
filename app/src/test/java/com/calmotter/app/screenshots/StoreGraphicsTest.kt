package com.calmotter.app.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.test.core.app.ApplicationProvider
import com.calmotter.app.R
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Genera l'icona ad alta risoluzione (512x512) per la scheda di Google Play
 * in docs/store/graphics/, dagli stessi vettori dell'app (l'immagine in
 * evidenza e gli screenshot sono in StoreScreenshotsTest). Non è un controllo: gira solo
 * su richiesta, quando la grafica cambia —
 * `./gradlew testStableDebugUnitTest --tests "*StoreGraphicsTest*" -Pstore.graphics=true`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StoreGraphicsTest {

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
}
