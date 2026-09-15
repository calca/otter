package com.calmotter.app

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Disegna un [GroupPauseRecipe.encode]d come `Bitmap` — usato solo qui, lato
 * host (GroupPauseHostScreen): la decodifica lato fotocamera vive in
 * GroupPauseJoinScreen.kt, che usa `zxing:core` in modo indipendente da
 * questa funzione.
 *
 * I colori sono parametri e non più nero/bianco fissi, così il chiamante può
 * intonare il QR alla palette attiva (vedi `GroupPauseShareHeader`): il
 * quadrato bianco puro su sfondo tenue stonava. [lightColor] accetta anche
 * `Color.TRANSPARENT` — da cui `ARGB_8888` invece del precedente `RGB_565`,
 * che non ha canale alpha e renderebbe il trasparente nero pieno.
 *
 * **Vincolo di leggibilità, non estetico**: i moduli devono restare *scuri su
 * chiaro*. Il joiner decodifica con `MultiFormatReader` + `HybridBinarizer`
 * senza `DecodeHintType.ALSO_INVERTED`, quindi un QR invertito (chiaro su
 * scuro) semplicemente non verrebbe letto — e nemmeno dalla maggior parte
 * delle app fotocamera di sistema. Con [lightColor] trasparente il "chiaro"
 * lo mette lo sfondo dietro al QR, che quindi deve essere chiaro a sua volta:
 * vedi il chiamante, che in tema scuro mette una lastra chiara invece di
 * lasciar trasparire lo sfondo scuro.
 */
fun generateQrCodeBitmap(
    content: String,
    sizePx: Int,
    darkColor: Int = Color.BLACK,
    lightColor: Int = Color.WHITE,
): Bitmap {
    val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M)
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    for (x in 0 until sizePx) {
        for (y in 0 until sizePx) {
            bitmap.setPixel(x, y, if (matrix[x, y]) darkColor else lightColor)
        }
    }
    return bitmap
}
