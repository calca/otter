package com.calmotter.app.screenshots

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.calmotter.app.AllowedAppsManager
import com.calmotter.app.AppTheme
import com.calmotter.app.CalmCountdown
import com.calmotter.app.EndReason
import com.calmotter.app.Mood
import com.calmotter.app.PasswordManager
import com.calmotter.app.PhraseManager
import com.calmotter.app.ScheduledPause
import com.calmotter.app.SessionHistoryManager
import com.calmotter.app.SessionManager
import com.calmotter.app.SessionRecord
import com.calmotter.app.installFakeAndroidKeyStore
import com.calmotter.app.ui.screens.BlockScreen
import com.calmotter.app.ui.screens.ClosingMomentScreen
import com.calmotter.app.ui.screens.HistoryScreen
import com.calmotter.app.ui.screens.MainScreen
import com.calmotter.app.ui.screens.ScheduleEditorScreen
import com.calmotter.app.ui.screens.ScheduledPausesScreen
import com.calmotter.app.ui.screens.SettingsScreen
import com.calmotter.app.ui.theme.CalmOtterTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.random.Random

/**
 * Lo sfondo della finestra. Nell'app lo dà il tema XML dell'Activity (overlay
 * della palette, vedi values/themes.xml) e le schermate ci disegnano sopra
 * veli traslucidi; l'Activity vuota dei test ha la finestra bianca di
 * default, che in modalità scura si vedeva attraverso. Qui la si ridipinge
 * con lo stesso colore della palette.
 */
@Composable
private fun WindowBackground(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
}

/** Telefono di riferimento: 411x891 dp a densità hdpi (immagini leggere da tenere nel repo). */
private const val PHONE = "w411dp-h891dp-hdpi"

/** "Adesso" fisso per le schermate che dipendono dal giorno: giovedì 1 ottobre 2026, 18:00. */
private val NOW: Long = LocalDateTime.of(2026, 10, 1, 18, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

private fun at(day: Int, month: Int, hour: Int): Long =
    LocalDateTime.of(2026, month, day, hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

/**
 * Home e schermata di blocco: le due schermate che tutti vedono sempre, in
 * ogni palette e in chiaro e scuro. È qui che sono passate le regressioni
 * visive più frequenti (CTA troppo brillanti in scuro, bottone Sblocca
 * invisibile, pillola di durata invisibile).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE)
class PaletteScreenshotTest(private val theme: AppTheme, private val night: Boolean) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-{1}")
        fun params(): List<Array<Any>> =
            AppTheme.entries.flatMap { listOf(arrayOf<Any>(it, false), arrayOf<Any>(it, true)) }
    }

    @get:Rule val compose = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name get() = "${theme.key}-${if (night) "dark" else "light"}"

    @Before
    fun setUp() {
        installFakeAndroidKeyStore()
        if (night) RuntimeEnvironment.setQualifiers("+night")
        CalmCountdown.random = Random(1)
        // Fermo: le animazioni infinite (stagno, otter che galleggia) non
        // lascerebbero mai la schermata "ferma" per la cattura.
        compose.mainClock.autoAdvance = false
    }

    private fun capture(screen: String, content: @Composable () -> Unit) {
        compose.setContent { CalmOtterTheme(appTheme = theme) { WindowBackground(content) } }
        compose.mainClock.advanceTimeBy(1_000)
        Screenshots.assertMatches(compose.onRoot(), "$screen-$name")
    }

    @Test
    fun home() = capture("home") {
        MainScreen(
            resumeSignal = 0,
            sessionManager = SessionManager.getInstance(context),
            sessionHistoryManager = SessionHistoryManager.getInstance(context),
            isAccessibilityServiceEnabled = { true },
            isDndAccessGranted = { true },
            onGrantAccessibility = {},
            onGrantDnd = {},
            onHistory = {},
            onSettings = {},
            selectedDurationMinutes = 60,
            onSelectDuration = {},
            showPermissionDialog = false,
            onDismissPermissionDialog = {},
        )
    }

    @Test
    fun block() {
        SessionManager.getInstance(context).startSession(60)
        capture("block") {
            BlockScreen(
                sessionManager = SessionManager.getInstance(context),
                passwordManager = PasswordManager.getInstance(context),
                phraseText = "“Look out the window. The world is still there.”",
                onExpiredImmediately = {},
                onExpiredNaturally = {},
                onUnlocked = {},
            )
        }
    }
}

/** Le altre schermate principali, in Salvia chiaro e scuro. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE)
class ScreenScreenshotTest(private val night: Boolean) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "night={0}")
        fun params(): List<Array<Any>> = listOf(arrayOf<Any>(false), arrayOf<Any>(true))
    }

    @get:Rule val compose = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val mode get() = if (night) "dark" else "light"

    @Before
    fun setUp() {
        installFakeAndroidKeyStore()
        if (night) RuntimeEnvironment.setQualifiers("+night")
        compose.mainClock.autoAdvance = false
    }

    private fun capture(screen: String, content: @Composable () -> Unit) {
        compose.setContent { CalmOtterTheme(appTheme = AppTheme.SAGE) { WindowBackground(content) } }
        compose.mainClock.advanceTimeBy(1_000)
        Screenshots.assertMatches(compose.onRoot(), "$screen-sage-$mode")
    }

    private val history = listOf(
        SessionRecord(startTimeMs = at(1, 10, 9), plannedMinutes = 60, effectiveMinutes = 60, completedNaturally = true,
            isGroupSession = true, companions = "Marta", activityId = 16, mood = Mood.CALM, note = "letto al parco",
            endReason = EndReason.NATURAL),
        SessionRecord(startTimeMs = at(30, 9, 21), plannedMinutes = 45, effectiveMinutes = 45, completedNaturally = true,
            endReason = EndReason.NATURAL),
        SessionRecord(startTimeMs = at(29, 9, 18), plannedMinutes = 60, effectiveMinutes = 12, completedNaturally = false,
            endReason = EndReason.SLOW_EXIT),
        SessionRecord(startTimeMs = at(28, 9, 20), plannedMinutes = 90, effectiveMinutes = 90, completedNaturally = true,
            isGroupSession = true, companions = "Marta\nLuca", activityId = 10, endReason = EndReason.NATURAL),
        SessionRecord(startTimeMs = at(27, 9, 10), plannedMinutes = 30, effectiveMinutes = 30, completedNaturally = true,
            endReason = EndReason.NATURAL),
    )

    @Test
    fun history() = capture("history") {
        HistoryScreen(sessions = history, goal = null, onEditGoal = {}, now = NOW)
    }

    @Test
    fun closingMoment() = capture("closing") {
        ClosingMomentScreen(effectiveMinutes = 60, onDone = { _, _ -> }, onSkip = {})
    }

    @Test
    fun scheduledPauses() = capture("schedules") {
        ScheduledPausesScreen(
            schedules = listOf(
                ScheduledPause(id = 1, days = 0b0011111, startMinuteOfDay = 21 * 60, durationMinutes = 60),
                ScheduledPause(id = 2, days = 0b1000000, startMinuteOfDay = 9 * 60, durationMinutes = 120, enabled = false),
            ),
            onToggle = {},
            onEdit = {},
        )
    }

    @Test
    fun scheduleEditor() = capture("schedule-editor") {
        ScheduleEditorScreen(
            original = ScheduledPause(id = 1, days = 0b0011111, startMinuteOfDay = 21 * 60, durationMinutes = 60),
            profiles = AllowedAppsManager.getInstance(context).profiles(),
            onSave = {},
            onDelete = {},
            onSkipNext = {},
        )
    }

    @Test
    fun settings() = capture("settings") {
        SettingsScreen(
            currentTheme = AppTheme.SAGE,
            partnerName = "Alex",
            passwordManager = PasswordManager.getInstance(context),
            phraseManager = PhraseManager.getInstance(context),
            resumeSignal = 0,
            isAccessibilityServiceEnabled = { true },
            isDndAccessGranted = { true },
            isDefaultHome = { false },
            onGrantAccessibility = {},
            onGrantDnd = {},
            onSetHome = {},
            onPickTheme = {},
            onManageAppsVerified = {},
            onChangePassword = {},
            onOpenGitHub = {},
            onOpenLicense = {},
            onOpenDeveloper = {},
        )
    }
}
