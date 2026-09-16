package com.calmotter.app

import android.os.Bundle
import androidx.activity.compose.setContent
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

    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = SessionManager.getInstance(applicationContext)

        setContent {
            CalmOtterTheme(appTheme = ThemeManager.getTheme(this)) {
                GroupPauseHostScreen(
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
