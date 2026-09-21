package com.calmotter.app

import android.os.Bundle
import androidx.activity.compose.setContent
import com.calmotter.app.ui.screens.DEFAULT_SESSION_DURATION_MINUTES
import com.calmotter.app.ui.screens.GroupPauseHostScreen
import com.calmotter.app.ui.theme.CalmOtterTheme

/**
 * Crea una pausa di gruppo — vedi specs/group-pause/. `themeVariant` resta
 * BASE (default di BaseActivity): stesso trattamento "schermata di rito"
 * di Home/Onboarding/BlockScreen (calmBackground(), niente ActionBar),
 * non "amministrazione" come Settings/Cronologia — vedi
 * ui/screens/CalmBackground.kt.
 *
 * `onStarted`/`onCancel` chiamano entrambi `finish()`: nel primo caso la
 * sessione è già stata avviata (SessionManager.startSession, invariato) e
 * MainActivity la rileverà da sola al ritorno (vedi il controllo aggiunto
 * a MainActivity.onResume()); nel secondo caso non è stato persistito
 * nulla, quindi non c'è nulla da annullare oltre a chiudere questa
 * Activity.
 */
class GroupPauseHostActivity : BaseActivity() {

    companion object {
        /**
         * Durata (minuti) scelta in Home, passata da [MainActivity] così
         * che il flow di creazione (ora un'unica schermata, la lobby — vedi
         * GroupPauseBluetoothLobbyHostScreen) riparta da lì invece che da un
         * default indipendente. [DEFAULT_SESSION_DURATION_MINUTES] di
         * ripiego se l'extra manca (chiamanti futuri che non lo passano) —
         * lo stesso default che Home stessa usa per `selectedDurationIndex`,
         * non un numero indipendente da tenere allineato a mano.
         */
        const val EXTRA_DURATION_MINUTES = "duration_minutes"
    }

    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = SessionManager.getInstance(applicationContext)
        val initialDurationMinutes = intent.getIntExtra(EXTRA_DURATION_MINUTES, DEFAULT_SESSION_DURATION_MINUTES)

        setContent {
            CalmOtterTheme(appTheme = ThemeManager.getTheme(this)) {
                GroupPauseHostScreen(
                    initialDurationMinutes = initialDurationMinutes,
                    onStarted = { durationMinutes, companions, groupTag ->
                        sessionManager.startSession(
                            durationMinutes,
                            isGroupSession = true,
                            companions = companions,
                            groupTag = groupTag,
                            isHost = true,
                        )
                        finish()
                    },
                    onCancel = { finish() },
                )
            }
        }
    }

}
