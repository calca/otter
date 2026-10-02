package com.calmotter.app

import android.content.Context
import android.content.Intent

/**
 * Condivisione e inviti, sempre dal menu di condivisione di Android: l'app
 * prepara il testo, chi lo manda e a chi lo sceglie l'utente con un tocco.
 * Nessuna chiamata di rete, come l'esportazione della Cronologia (vedi
 * CONTRIBUTING.md e docs/privacy-policy.md).
 *
 * Il link è sempre quello della versione stable sul Play Store, anche dalla
 * beta: è l'app che si consiglia di installare.
 */
object Share {

    const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.calmotter.app"

    /** "1 h di calma, lontano dal telefono" — dopo una pausa arrivata in fondo. */
    fun pause(context: Context, effectiveMinutes: Int) = send(
        context,
        context.getString(R.string.share_pause_text, durationPillLabel(effectiveMinutes.coerceAtLeast(1)), PLAY_STORE_URL),
    )

    /** L'invito a installare l'app: da Tempo insieme e da Impostazioni → Info. */
    fun invite(context: Context) = send(context, context.getString(R.string.share_invite_text, PLAY_STORE_URL))

    private fun send(context: Context, text: String) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(
            Intent.createChooser(send, context.getString(R.string.share_chooser_title))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
