package com.calmotter.app

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Il QR ha smesso di essere nero-su-bianco fisso per intonarsi alle palette
 * (vedi [generateQrCodeBitmap] e `GroupPauseShareHeader`), e in tema chiaro il
 * suo sfondo è ora *trasparente*. Sono entrambi cambiamenti che possono
 * romperne la leggibilità senza che si veda a occhio, quindi qui il QR viene
 * ridecodificato con la stessa identica pipeline zxing del joiner
 * (`MultiFormatReader` + `HybridBinarizer`, nessun hint — vedi
 * GroupPauseJoinScreen.kt): se un colore scelto non desse abbastanza
 * contrasto, questi test falliscono invece di lasciarlo scoprire a chi prova
 * a scansionare.
 */
@RunWith(RobolectricTestRunner::class)
class QrCodeGeneratorTest {

    private val code = "aqjdmh6VFxk"

    /**
     * Decodifica come farebbe la fotocamera del joiner. [background] è il
     * colore su cui il QR è realmente appoggiato a schermo: serve perché con
     * sfondo trasparente i pixel "chiari" hanno RGB 0x000000 con alpha 0, e
     * letti grezzi sembrerebbero neri — la fotocamera invece vede il
     * risultato già composto sopra alla pagina, ed è quello che va verificato.
     */
    private fun decode(bitmap: Bitmap, background: Int): String {
        val w = bitmap.width
        val h = bitmap.height
        val raw = IntArray(w * h)
        bitmap.getPixels(raw, 0, w, 0, 0, w, h)

        val composited = IntArray(w * h) { i ->
            val px = raw[i]
            val a = Color.alpha(px) / 255f
            fun mix(fg: Int, bg: Int) = (a * fg + (1 - a) * bg).toInt().coerceIn(0, 255)
            Color.rgb(
                mix(Color.red(px), Color.red(background)),
                mix(Color.green(px), Color.green(background)),
                mix(Color.blue(px), Color.blue(background)),
            )
        }

        val source = RGBLuminanceSource(w, h, composited)
        return MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source))).text
    }

    @Test
    fun blackOnWhiteStillDecodes() {
        val bitmap = generateQrCodeBitmap(code, sizePx = 512)
        assertEquals(code, decode(bitmap, Color.WHITE))
    }

    /**
     * Tema chiaro: sfondo trasparente, moduli `onBackground`. Lo sfondo usato
     * per la composizione è il punto *più scuro* del gradiente di
     * `calmBackground` (primary a alpha 0.09 sopra surface) — il caso
     * peggiore per il contrasto, non quello medio.
     */
    @Test
    fun transparentFieldDecodesOverEachLightPalette() {
        // surface, primary, onBackground per Sage/Lavender/Terracotta chiare,
        // presi da ui/theme/CalmOtterTheme.kt.
        val palettes = listOf(
            Triple(0xFFF9F9F8.toInt(), 0xFF0F5238.toInt(), 0xFF191C1C.toInt()),
            Triple(0xFFFDF7FF.toInt(), 0xFF7C6FA0.toInt(), 0xFF1C1B1F.toInt()),
            Triple(0xFFFFFBFF.toInt(), 0xFFA0604A.toInt(), 0xFF201A18.toInt()),
        )
        for ((surface, primary, onBackground) in palettes) {
            val gradientTop = Color.rgb(
                (0.09f * Color.red(primary) + 0.91f * Color.red(surface)).toInt(),
                (0.09f * Color.green(primary) + 0.91f * Color.green(surface)).toInt(),
                (0.09f * Color.blue(primary) + 0.91f * Color.blue(surface)).toInt(),
            )
            val bitmap = generateQrCodeBitmap(
                content = code,
                sizePx = 512,
                darkColor = onBackground,
                lightColor = Color.TRANSPARENT,
            )
            assertEquals(code, decode(bitmap, gradientTop))
        }
    }

    /**
     * Tema scuro: lastra chiara tinta di palette (`onBackground`) con moduli
     * scuri (`background`) — la trasparenza qui non è praticabile, vedi il
     * commento in `GroupPauseShareHeader`.
     */
    @Test
    fun tintedPlateDecodesOverEachDarkPalette() {
        // onBackground (lastra) e background (moduli) per le tre palette scure.
        val palettes = listOf(
            0xFFD4E8DC.toInt() to 0xFF1A2B38.toInt(),
            0xFFE0D8F5.toInt() to 0xFF1E1A2E.toInt(),
            0xFFF0DDD8.toInt() to 0xFF2A1812.toInt(),
        )
        for ((plate, module) in palettes) {
            val bitmap = generateQrCodeBitmap(
                content = code,
                sizePx = 512,
                darkColor = module,
                lightColor = plate,
            )
            assertEquals(code, decode(bitmap, plate))
        }
    }

    /**
     * Il contrario del test sopra: un QR invertito (chiaro su scuro) NON deve
     * risultare leggibile con questa pipeline. Serve a fissare il motivo per
     * cui in tema scuro c'è una lastra chiara invece della trasparenza — se un
     * giorno zxing iniziasse a gestire l'inversione da solo, questo test
     * fallirebbe e sarebbe il segnale che quella scelta si può riaprire.
     */
    @Test(expected = com.google.zxing.NotFoundException::class)
    fun invertedQrIsNotReadableByTheJoinersPipeline() {
        val bitmap = generateQrCodeBitmap(
            content = code,
            sizePx = 512,
            darkColor = 0xFFD4E8DC.toInt(),
            lightColor = 0xFF1A2B38.toInt(),
        )
        decode(bitmap, 0xFF1A2B38.toInt())
    }
}
