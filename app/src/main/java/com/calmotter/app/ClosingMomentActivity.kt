package com.calmotter.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import com.calmotter.app.ui.screens.ClosingMomentScreen
import com.calmotter.app.ui.theme.CalmOtterTheme

/**
 * Ospita il momento di chiusura (specs/closing-moment/) per la pausa
 * completata ancora senza risposta ([SessionManager.pendingReflectionId]).
 * Un'Activity a sé perché ci si arriva da tre punti — la fine della pausa in
 * Home, quella sulla schermata di blocco aperta dal servizio di
 * accessibilità, e la riga "Pausa finita alle…" della Home — e deve
 * comportarsi allo stesso modo in tutti e tre.
 */
class ClosingMomentActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sessionManager = SessionManager.getInstance(applicationContext)
        val history = SessionHistoryManager.getInstance(applicationContext)
        val id = sessionManager.pendingReflectionId()
        val record = id.takeIf { it > 0 }?.let { history.byId(it) }
        if (record == null) {
            sessionManager.clearPendingReflection()
            finish()
            return
        }

        setContent {
            CalmOtterTheme(appTheme = ThemeManager.getTheme(this)) {
                ClosingMomentScreen(
                    effectiveMinutes = record.effectiveMinutes,
                    onDone = { mood, note ->
                        history.saveReflection(record.id, mood, note)
                        sessionManager.clearPendingReflection()
                        finish()
                    },
                    onSkip = {
                        sessionManager.clearPendingReflection()
                        finish()
                    },
                )
            }
        }
    }

    companion object {
        /** Apre il momento di chiusura se c'è una pausa completata in attesa di risposta. */
        fun startIfPending(context: Context): Boolean {
            if (SessionManager.getInstance(context).pendingReflectionId() == 0L) return false
            context.startActivity(
                Intent(context, ClosingMomentActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return true
        }
    }
}
