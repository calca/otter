package com.calmotter.app.screenshots

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import org.junit.Assert.fail
import java.io.File
import kotlin.math.abs
import kotlin.math.max

/**
 * Screenshot test senza librerie in più: ogni schermata viene disegnata da
 * Robolectric (grafica nativa) e confrontata con un'immagine di riferimento
 * in `app/src/test/screenshots/`.
 *
 * - **Verifica** (default, anche in CI): se la schermata differisce, il
 *   test fallisce e scrive in `app/build/screenshots/` l'immagine attuale e
 *   una mappa delle differenze (in rosso), da guardare prima di decidere.
 * - **Registrazione**, quando un cambio è voluto:
 *   `./gradlew testStableDebugUnitTest --tests "*Screenshot*" -Pscreenshots.record=true`
 *   riscrive i riferimenti, che vanno committati insieme al cambio.
 *
 * Una piccola tolleranza (pixel che differiscono di poco su un canale, e una
 * frazione minima di pixel diversi) assorbe le differenze di antialiasing fra
 * macchine; un colore sbagliato o un bottone sparito la superano sempre.
 */
internal object Screenshots {
    private val record = System.getProperty("screenshots.record") == "true"
    private val goldenDir = File("src/test/screenshots")
    private val outDir = File("build/screenshots")

    private const val CHANNEL_TOLERANCE = 24
    private const val MAX_DIFFERENT_FRACTION = 0.002

    fun assertMatches(node: SemanticsNodeInteraction, name: String) {
        val actual = node.captureToImage().asAndroidBitmap()
        val golden = File(goldenDir, "$name.png")
        if (record) {
            goldenDir.mkdirs()
            golden.outputStream().use { actual.compress(Bitmap.CompressFormat.PNG, 100, it) }
            return
        }
        if (!golden.exists()) {
            fail("Manca lo screenshot di riferimento '$name'. Registralo con -Pscreenshots.record=true e committalo.")
        }
        val expected = BitmapFactory.decodeFile(golden.path)
        if (expected.width != actual.width || expected.height != actual.height) {
            save(actual, name, null)
            fail("'$name': dimensioni diverse (${actual.width}x${actual.height} invece di ${expected.width}x${expected.height}).")
        }
        val w = actual.width
        val h = actual.height
        val a = IntArray(w * h).also { actual.getPixels(it, 0, w, 0, 0, w, h) }
        val e = IntArray(w * h).also { expected.getPixels(it, 0, w, 0, 0, w, h) }
        val diff = IntArray(w * h)
        var different = 0
        for (i in a.indices) {
            val d = max(
                max(abs(Color.red(a[i]) - Color.red(e[i])), abs(Color.green(a[i]) - Color.green(e[i]))),
                max(abs(Color.blue(a[i]) - Color.blue(e[i])), abs(Color.alpha(a[i]) - Color.alpha(e[i]))),
            )
            if (d > CHANNEL_TOLERANCE) {
                different++
                diff[i] = Color.RED
            } else {
                diff[i] = Color.argb(40, Color.red(e[i]), Color.green(e[i]), Color.blue(e[i]))
            }
        }
        val fraction = different.toDouble() / a.size
        if (fraction > MAX_DIFFERENT_FRACTION) {
            save(actual, name, Bitmap.createBitmap(diff, w, h, Bitmap.Config.ARGB_8888))
            fail(
                "'$name' è cambiata: %.2f%% dei pixel diversi. Attuale e differenze in app/build/screenshots/. "
                    .format(fraction * 100) +
                    "Se il cambio è voluto, registra di nuovo con -Pscreenshots.record=true."
            )
        }
    }

    private fun save(actual: Bitmap, name: String, diff: Bitmap?) {
        outDir.mkdirs()
        File(outDir, "$name.actual.png").outputStream().use { actual.compress(Bitmap.CompressFormat.PNG, 100, it) }
        diff?.let { d -> File(outDir, "$name.diff.png").outputStream().use { d.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }
}
