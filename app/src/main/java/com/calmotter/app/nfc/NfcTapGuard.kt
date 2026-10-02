package com.calmotter.app.nfc

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Tiene i tocchi NFC dentro Calm Otter mentre il telefono fa da "carta"
 * (lobby dell'host, passo di rilascio degli altri), invece di lasciarli al
 * sistema — che su Samsung apre il selettore "Completa azione con" invece di
 * far partire la sessione (segnalato più volte).
 *
 * Due cose, entrambe legate al ciclo di vita dell'Activity:
 *
 * 1. **Servizio preferito** ([CardEmulation.setPreferredService]): dice ad
 *    Android di instradare il nostro AID a GroupPauseHceService senza
 *    chiedere. Vale solo mentre l'Activity è in primo piano, e la
 *    documentazione chiede di impostarlo in onResume e toglierlo in onPause.
 *    Prima lo si impostava **una volta sola**, subito dopo aver lanciato il
 *    dialogo di sistema "rendi visibile via Bluetooth": quel dialogo mette in
 *    pausa l'Activity e la preferenza si perdeva, quindi ogni tocco
 *    successivo finiva al selettore. Qui si reimposta a ogni ripresa.
 * 2. **Letture di tag intercettate** ([swallowTags]): mentre fa da carta, il
 *    telefono continua anche a cercare tag, e se l'altro telefono non è (o
 *    non è ancora) in modalità lettore lo legge come un tag qualunque e lo
 *    passa al sistema, cioè di nuovo al selettore. Con il foreground
 *    dispatch quelle letture arrivano all'Activity, che le ignora. Da usare
 *    solo in un'Activity a cui un Intent in più non fa nulla.
 */
@Composable
fun NfcTapGuard(enabled: Boolean, swallowTags: Boolean = false) {
    val activity = LocalActivity.current ?: return
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(enabled, lifecycleOwner) {
        val adapter = NfcAdapter.getDefaultAdapter(activity)
        if (!enabled || adapter == null) return@DisposableEffect onDispose {}
        val service = ComponentName(activity, GroupPauseHceService::class.java)

        fun engage() {
            runCatching { CardEmulation.getInstance(adapter).setPreferredService(activity, service) }
            if (swallowTags) {
                val intent = Intent(activity, activity.javaClass)
                    .setAction(ACTION_IGNORED_TAG)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
                val pending = PendingIntent.getActivity(activity, 0, intent, flags)
                runCatching { adapter.enableForegroundDispatch(activity, pending, null, null) }
            }
        }

        fun release() {
            runCatching { CardEmulation.getInstance(adapter).unsetPreferredService(activity) }
            if (swallowTags) runCatching { adapter.disableForegroundDispatch(activity) }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> engage()
                Lifecycle.Event.ON_PAUSE -> release()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) engage()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            release()
        }
    }
}

/** Azione degli Intent con cui il foreground dispatch consegna i tag letti: vanno ignorati. */
const val ACTION_IGNORED_TAG = "com.calmotter.app.action.IGNORED_NFC_TAG"
