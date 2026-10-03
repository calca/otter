package com.calmotter.app

import android.app.Application
import android.content.Context

/**
 * L'Application di Calm Otter: esiste solo per tenere l'[AppGraph].
 */
class CalmOtterApplication : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(AppForeground)
    }
}

/**
 * Le istanze uniche dell'app, create la prima volta che servono. Prima ogni
 * gestore teneva la propria in un campo statico (`@Volatile instance`, doppio
 * controllo, `resetInstanceForTests()`): un campo statico sopravvive fra un
 * test e l'altro, quindi ogni test doveva ricordarsi di resettare tutti i
 * gestori toccati, anche indirettamente — e dimenticarne uno faceva passare
 * lo stato da un test al successivo. Qui le istanze appartengono
 * all'Application: Robolectric ne crea una nuova per ogni test, e ogni test
 * parte pulito da solo.
 *
 * Niente framework di dependency injection, per scelta del progetto: è un
 * contenitore scritto a mano. I chiamanti non cambiano, `X.getInstance(context)`
 * passa di qui. `lazy` è sincronizzato per default, quindi resta thread-safe
 * come prima (i receiver chiamano da thread diversi).
 */
class AppGraph(private val app: Application) {
    val database: CalmOtterDatabase by lazy { CalmOtterDatabase.create(app) }
    val sessionHistoryManager: SessionHistoryManager by lazy { SessionHistoryManager(app) }
    val sessionManager: SessionManager by lazy { SessionManager(app) }
    val passwordManager: PasswordManager by lazy { PasswordManager(app) }
    val allowedAppsManager: AllowedAppsManager by lazy { AllowedAppsManager(app) }
    val launcherManager: LauncherManager by lazy { LauncherManager(app) }
    val phraseManager: PhraseManager by lazy { PhraseManager(app) }
    val weeklyGoalManager: WeeklyGoalManager by lazy { WeeklyGoalManager(app) }
    val scheduleManager: ScheduleManager by lazy { ScheduleManager(app) }
    val slowExitManager: SlowExitManager by lazy { SlowExitManager(app) }
}

/** Il contenitore dell'app, da qualunque Context (Activity, Service, receiver). */
val Context.appGraph: AppGraph
    get() = (applicationContext as CalmOtterApplication).graph
