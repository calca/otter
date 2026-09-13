package com.calmotter.app

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Disegna un [GroupPauseRecipe.encode]d come `Bitmap` in bianco e nero —
 * usato solo qui, lato host (GroupPauseHostScreen): la decodifica lato
 * fotocamera vive in GroupPauseJoinScreen.kt, che usa `zxing:core` in modo
 * indipendente da questa funzione.
 */
fun generateQrCodeBitmap(content: String, sizePx: Int): Bitmap {
    val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M)
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
    for (x in 0 until sizePx) {
        for (y in 0 until sizePx) {
            bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
        }
    }
    return bitmap
}
