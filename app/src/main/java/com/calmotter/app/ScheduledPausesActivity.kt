package com.calmotter.app

import android.os.Bundle
import android.view.MenuItem
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.calmotter.app.ui.screens.PasswordVerifyDialog
import com.calmotter.app.ui.screens.ScheduledPausesScreen
import com.calmotter.app.ui.theme.CalmOtterTheme

/**
 * Le pause programmate (specs/scheduled-pauses/). Si apre senza password:
 * aggiungere una pausa o renderla più severa stringe il patto. Ogni modifica
 * che lo allenta ([ScheduledPause.isLooserThan], la cancellazione, "salta la
 * prossima") passa prima dalla password.
 */
class ScheduledPausesActivity : BaseActivity() {

    override val themeVariant = ThemeVariant.WITH_ACTION_BAR

    private lateinit var manager: ScheduleManager
    private var schedules by mutableStateOf<List<ScheduledPause>>(emptyList())
    /** Modifica in attesa della password; null = nessuna. */
    private var pending by mutableStateOf<(() -> Unit)?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.apply {
            title = getString(R.string.schedule_title)
            setDisplayHomeAsUpEnabled(true)
        }
        manager = ScheduleManager.getInstance(applicationContext)
        schedules = manager.all()
        val passwordManager = PasswordManager.getInstance(applicationContext)
        val profiles = AllowedAppsManager.getInstance(applicationContext).profiles()

        setContent {
            CalmOtterTheme(appTheme = ThemeManager.getTheme(this)) {
                ScheduledPausesScreen(
                    schedules = schedules,
                    profiles = profiles,
                    onSave = { updated ->
                        val old = manager.byId(updated.id)
                        guard(old != null && updated.isLooserThan(old)) { save(updated) }
                    },
                    onDelete = { schedule -> guard(true) { delete(schedule) } },
                    onSkipNext = { schedule ->
                        guard(true) {
                            val next = nextOccurrence(schedule, System.currentTimeMillis()) ?: return@guard
                            save(schedule.copy(skipUntil = next))
                        }
                    },
                )
                pending?.let { action ->
                    PasswordVerifyDialog(
                        passwordManager = passwordManager,
                        title = stringResource(R.string.schedule_title),
                        message = stringResource(R.string.schedule_password_prompt),
                        confirmLabel = stringResource(R.string.confirm),
                        onDismiss = { pending = null },
                        onVerified = { pending = null; action() },
                    )
                }
            }
        }
    }

    /** Esegue subito, o dopo la password se la modifica allenta il patto. */
    private fun guard(needsPassword: Boolean, action: () -> Unit) {
        if (needsPassword) pending = action else action()
    }

    private fun save(schedule: ScheduledPause) {
        val saved = manager.save(schedule)
        ScheduleAlarms.arm(this, saved)
        schedules = manager.all()
    }

    private fun delete(schedule: ScheduledPause) {
        manager.delete(schedule.id)
        ScheduleAlarms.cancel(this, schedule.id)
        schedules = manager.all()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
}
