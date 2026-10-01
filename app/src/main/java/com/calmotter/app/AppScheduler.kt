package com.calmotter.app

import android.content.Context

/**
 * I codici delle richieste dei PendingIntent di allarme, in un posto solo.
 * Due allarmi con lo stesso codice e lo stesso receiver si sostituiscono a
 * vicenda **in silenzio**: tenerli qui rende una collisione visibile
 * leggendo, invece che scoprirla quando una pausa non finisce.
 */
object AlarmIds {
    const val SESSION_EXPIRY = 1001
    const val SLOW_EXIT = 1002
    const val WEEKLY_NOTE = 5001

    /** Pause programmate: due codici per programmazione (avviso, partenza) da 3000 in su. */
    fun schedule(id: Int, start: Boolean): Int = 3000 + id * 2 + if (start) 1 else 0
}

/**
 * Rimette in ordine tutto ciò che il sistema può perdere o spostare: gli
 * allarmi delle pause programmate e della nota della domenica, e il servizio
 * in primo piano di una pausa in corso. Idempotente, quindi la si chiama
 * senza pensarci da ogni punto in cui il mondo può essere cambiato:
 * all'apertura dell'app, al riavvio, e al cambio di ora o di fuso (prima di
 * questo, dopo un viaggio gli allarmi restavano sull'orario vecchio finché
 * non si apriva l'app).
 *
 * Non tocca la pausa in corso: la sua fine è un istante assoluto, non
 * un'ora locale, quindi un cambio di fuso non la sposta.
 */
object AppScheduler {
    fun reconcile(context: Context) {
        ScheduleAlarms.armAll(context)
        WeeklySummary.arm(context)
        if (SessionManager.getInstance(context).isSessionActive()) {
            SessionForegroundService.start(context)
        }
    }
}
