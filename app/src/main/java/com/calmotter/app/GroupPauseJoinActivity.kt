package com.calmotter.app

import android.os.Bundle
import androidx.activity.compose.setContent
import com.calmotter.app.ui.screens.GroupPauseJoinScreen
import com.calmotter.app.ui.theme.CalmOtterTheme

/**
 * Unisciti a una pausa di gruppo — vedi specs/group-pause/ e la doc di
 * [GroupPauseHostActivity] per il resto del ragionamento (stesso
 * `themeVariant` BASE, stesso "avvia sessione poi finish(), MainActivity
 * se ne accorge da sola al ritorno").
 */
class GroupPauseJoinActivity : BaseActivity() {

    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = SessionManager.getInstance(applicationContext)

        setContent {
            CalmOtterTheme(appTheme = ThemeManager.getTheme(this)) {
                GroupPauseJoinScreen(
                    onJoined = { durationMinutes ->
                        sessionManager.startSession(durationMinutes, isGroupSession = true)
                        finish()
                    },
                    onCancel = { finish() },
                )
            }
        }
    }
}
