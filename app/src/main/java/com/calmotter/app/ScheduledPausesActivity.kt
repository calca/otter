package com.calmotter.app

import android.annotation.SuppressLint
import android.content.Intent
import androidx.core.net.toUri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.calmotter.app.ui.screens.PasswordVerifyDialog
import com.calmotter.app.ui.screens.ScheduleEditorScreen
import com.calmotter.app.ui.screens.newScheduledPause
import com.calmotter.app.ui.screens.ScheduledPausesScreen
import com.calmotter.app.ui.theme.CalmOtterTheme

/**
 * Le pause programmate (specs/scheduled-pauses/): l'elenco e, al posto suo,
 * la pagina di modifica di una pausa. Si apre senza password. Le pause non
 * protette si gestiscono liberamente; proteggerne una, e poi allentarla,
 * eliminarla o saltarne la prossima, passa dalla password (le regole in
 * [passwordNeededToSave]).
 */
class ScheduledPausesActivity : BaseActivity() {

    override val themeVariant = ThemeVariant.WITH_ACTION_BAR

    private lateinit var manager: ScheduleManager
    private var schedules by mutableStateOf<List<ScheduledPause>>(emptyList())
    /** La pausa aperta nella pagina di modifica; null = si vede l'elenco. */
    private var editing by mutableStateOf<ScheduledPause?>(null)
    /** Modifica in attesa della password; null = nessuna. */
    private var pending by mutableStateOf<(() -> Unit)?>(null)
    /** Permesso "Sveglie e promemoria", riletto a ogni ripresa (si concede fuori dall'app). */
    private var exactAlarmsAllowed by mutableStateOf(true)
    /** Il messaggio del dialogo della password: proteggere o allentare. */
    private var pendingMessage by mutableIntStateOf(R.string.schedule_password_prompt)

    // Lo stato a schermo sopravvive a una ricreazione dell'Activity (tema
    // scuro, dimensione del testo, lingua; su tablet anche la rotazione, che
    // da targetSdk 36 ignora screenOrientation): senza, si richiudeva.
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_EDITING_ID, editing?.id ?: NOT_EDITING)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.apply {
            title = getString(R.string.schedule_title)
            setDisplayHomeAsUpEnabled(true)
        }
        manager = ScheduleManager.getInstance(applicationContext)
        schedules = manager.all()
        // La pausa aperta nell'editor: per id (0 = una nuova). I campi già
        // cambiati li ripristina l'editor stesso (rememberSaveable).
        editing = when (val id = savedInstanceState?.getInt(STATE_EDITING_ID, NOT_EDITING) ?: NOT_EDITING) {
            NOT_EDITING -> null
            0 -> newScheduledPause()
            else -> manager.byId(id)
        }
        val passwordManager = PasswordManager.getInstance(applicationContext)
        val profiles = AllowedAppsManager.getInstance(applicationContext).profiles()

        setContent {
            CalmOtterTheme(appTheme = ThemeManager.getTheme(this)) {
                val current = editing
                LaunchedEffect(current?.id, current == null) {
                    invalidateOptionsMenu()
                    supportActionBar?.title = getString(
                        when {
                            current == null -> R.string.schedule_title
                            current.id == 0 -> R.string.schedule_new_title
                            else -> R.string.schedule_edit_title
                        }
                    )
                }
                if (current == null) {
                    ScheduledPausesScreen(
                        schedules = schedules,
                        onToggle = ::saveGuarded,
                        onEdit = { editing = it },
                        exactAlarmsAllowed = exactAlarmsAllowed,
                        onAllowExactAlarms = ::requestExactAlarms,
                    )
                } else {
                    // Indietro torna all'elenco senza salvare. La pagina si
                    // chiude solo a modifica fatta: se la password viene
                    // annullata, quanto impostato resta lì.
                    BackHandler { editing = null }
                    ScheduleEditorScreen(
                        original = current,
                        profiles = profiles,
                        onSave = { updated -> saveGuarded(updated) { editing = null } },
                        onSkipNext = {
                            guard(current.locked) {
                                val skipUntil = skipNextUntil(current) ?: return@guard
                                save(current.copy(skipUntil = skipUntil, drawnAt = 0L))
                                editing = null
                            }
                        },
                    )
                }
                pending?.let { action ->
                    PasswordVerifyDialog(
                        passwordManager = passwordManager,
                        title = stringResource(R.string.schedule_title),
                        message = stringResource(pendingMessage),
                        confirmLabel = stringResource(R.string.confirm),
                        onDismiss = { pending = null },
                        onVerified = { pending = null; action() },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        exactAlarmsAllowed = ScheduleAlarms.canBeExact(this)
    }

    /** Apre "Sveglie e promemoria" per Calm Otter (Android 12+; prima non serve). */
    private fun requestExactAlarms() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        runCatching {
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:$packageName".toUri()))
        }
    }

    private fun saveGuarded(updated: ScheduledPause, then: () -> Unit = {}) {
        val old = manager.byId(updated.id)
        val protecting = updated.locked && old?.locked != true
        guard(
            passwordNeededToSave(old, updated),
            if (protecting) R.string.schedule_lock_prompt else R.string.schedule_password_prompt,
        ) { save(updated); then() }
    }

    /** Esegue subito, o dopo la password se serve. */
    private fun guard(needsPassword: Boolean, message: Int = R.string.schedule_password_prompt, action: () -> Unit) {
        if (needsPassword) {
            pendingMessage = message
            pending = action
        } else {
            action()
        }
    }

    /**
     * Fin dove salta "salta la prossima". A sorpresa tutta la finestra di
     * quel giorno, non solo l'istante estratto: altrimenti la nuova
     * estrazione potrebbe cadere più tardi nella stessa finestra.
     */
    private fun skipNextUntil(schedule: ScheduledPause): Long? {
        val now = System.currentTimeMillis()
        if (!schedule.isSurprise) return nextOccurrence(schedule, now)
        val stored = manager.byId(schedule.id) ?: schedule
        val next = stored.drawnAt.takeIf { it > now } ?: nextSurpriseStart(stored, now) ?: return null
        return surpriseWindowEndOn(stored, next)
    }

    private fun save(schedule: ScheduledPause) {
        // Giorni, durata o finestra cambiati: l'estrazione vecchia non vale più.
        val old = manager.byId(schedule.id)
        val toSave = if (old != null && schedule.drawChangedFrom(old)) schedule.copy(drawnAt = 0L) else schedule
        val saved = manager.save(toSave)
        ScheduleAlarms.arm(this, saved)
        schedules = manager.all()
    }

    private fun delete(schedule: ScheduledPause) {
        manager.delete(schedule.id)
        ScheduleAlarms.cancel(this, schedule.id)
        schedules = manager.all()
    }

    /**
     * Il cestino in barra, solo nella pagina di modifica di una pausa che
     * esiste già: lontano da "Salva" (fisso in fondo), come nella sveglia di
     * Android. Il titolo fa da tooltip e da etichetta per TalkBack.
     */
    @SuppressLint("AlwaysShowAction")
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, MENU_DELETE, 0, getString(R.string.schedule_delete))
            .setIcon(R.drawable.ic_history_delete)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(MENU_DELETE)?.isVisible = editing.let { it != null && it.id != 0 }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            if (editing != null) editing = null else finish()
            return true
        }
        if (item.itemId == MENU_DELETE) {
            val current = editing ?: return true
            guard(manager.byId(current.id)?.locked == true) { delete(current); editing = null }
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private companion object {
        const val MENU_DELETE = 1
        const val STATE_EDITING_ID = "editing_id"
        const val NOT_EDITING = -1
    }
}
