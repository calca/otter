package com.calmotter.app

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * Se c'è un'Activity di Calm Otter in primo piano. Serve a "Avvicina i
 * telefoni" (QuickTogetherInbox): con l'app aperta la proposta ricevuta via
 * NFC apre subito il conto alla rovescia, altrimenti arriva come notifica.
 */
object AppForeground : Application.ActivityLifecycleCallbacks {
    @Volatile
    private var started = 0

    val isForeground: Boolean get() = started > 0

    override fun onActivityStarted(activity: Activity) { started++ }
    override fun onActivityStopped(activity: Activity) { started = (started - 1).coerceAtLeast(0) }
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
