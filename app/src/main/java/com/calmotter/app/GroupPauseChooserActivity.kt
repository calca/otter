package com.calmotter.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import com.calmotter.app.ui.screens.GroupPauseChooserScreen
import com.calmotter.app.ui.theme.CalmOtterTheme

/**
 * Primo passo di Tempo Insieme — ospitare o unirsi. Era un `AlertDialog`
 * dentro [MainScreen][com.calmotter.app.ui.screens.MainScreen], ora è una
 * schermata a sé: vedi GroupPauseChooserScreen.kt per il perché.
 *
 * `themeVariant` resta BASE come le altre due Activity del flusso: la barra
 * in cima è disegnata in Compose dalla schermata stessa, non è un'ActionBar
 * di sistema, così la freccia indietro sta sullo stesso sfondo velato del
 * resto invece che su una barra opaca.
 *
 * Non tiene stato proprio: inoltra la durata scelta in Home a
 * [GroupPauseHostActivity] e poi si chiude, così il tasto indietro dalla
 * lobby riporta a Home e non qui — una volta scelta la strada, tornare al
 * bivio non serve a nulla.
 */
class GroupPauseChooserActivity : BaseActivity() {

    companion object {
        /** Durata (minuti) scelta in Home, da girare a [GroupPauseHostActivity]. */
        const val EXTRA_DURATION_MINUTES = "duration_minutes"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val durationMinutes = intent.getIntExtra(EXTRA_DURATION_MINUTES, 30)

        setContent {
            CalmOtterTheme(appTheme = ThemeManager.getTheme(this)) {
                GroupPauseChooserScreen(
                    onCreate = {
                        startActivity(
                            Intent(this, GroupPauseHostActivity::class.java)
                                .putExtra(GroupPauseHostActivity.EXTRA_DURATION_MINUTES, durationMinutes)
                        )
                        finish()
                    },
                    onJoin = {
                        startActivity(Intent(this, GroupPauseJoinActivity::class.java))
                        finish()
                    },
                    onBack = { finish() },
                )
            }
        }
    }
}
