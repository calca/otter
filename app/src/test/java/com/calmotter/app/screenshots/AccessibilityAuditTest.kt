package com.calmotter.app.screenshots

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.core.app.ApplicationProvider
import com.calmotter.app.AllowedAppsManager
import com.calmotter.app.AppTheme
import com.calmotter.app.CalmCountdown
import com.calmotter.app.PasswordManager
import com.calmotter.app.PhraseManager
import com.calmotter.app.ScheduledPause
import com.calmotter.app.SessionHistoryManager
import com.calmotter.app.SessionManager
import com.calmotter.app.UsagePauseRule
import com.calmotter.app.installFakeAndroidKeyStore
import com.calmotter.app.ui.screens.BlockScreen
import com.calmotter.app.ui.screens.ClosingMomentScreen
import com.calmotter.app.ui.screens.HistoryScreen
import com.calmotter.app.ui.screens.MainScreen
import com.calmotter.app.ui.screens.QuickTogetherReaderScreen
import com.calmotter.app.ui.screens.ScheduleEditorScreen
import com.calmotter.app.ui.screens.ScheduledPausesScreen
import com.calmotter.app.ui.screens.SettingsScreen
import com.calmotter.app.ui.screens.UsagePauseScreen
import com.calmotter.app.ui.theme.CalmOtterTheme
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.random.Random

/**
 * Accessibilità delle schermate principali, sull'albero semantico che legge
 * TalkBack (l'Accessibility Test Framework di Google sotto Robolectric non
 * controlla nulla: provato, passa anche un bottone 12dp senza nome).
 *
 * Fallisce se un elemento toccabile non ha un nome o ha un'area di tocco
 * sotto i 48dp. Con `-Pa11y.report=true` stampa anche l'ordine di lettura
 * (nome, ruolo, stato), da rileggere quando si tocca una schermata; con
 * `-Pa11y.fontscale=2` disegna le schermate col testo al 200% in build/a11y/.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-hdpi")
class AccessibilityAuditTest(private val night: Boolean) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "night={0}")
        fun params(): List<Array<Any>> = listOf(arrayOf<Any>(false), arrayOf<Any>(true))
        /** Con -Pa11y.fontscale=2 le schermate si disegnano col testo al 200% e finiscono in build/a11y/. */
        private val fontScale = System.getProperty("a11y.fontscale")?.toFloatOrNull()
    }

    @get:Rule val compose = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        installFakeAndroidKeyStore()
        if (night) RuntimeEnvironment.setQualifiers("+night")
        fontScale?.let { RuntimeEnvironment.setFontScale(it) }
        CalmCountdown.random = Random(1)
        compose.mainClock.autoAdvance = false
    }

    private fun audit(screen: String, content: @Composable () -> Unit) {
        compose.setContent {
            CalmOtterTheme(appTheme = AppTheme.SAGE) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
        val mode = if (night) "dark" else "light"
        if (fontScale != null && !night) {
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            java.io.File("build/a11y").mkdirs()
            java.io.File("build/a11y/$screen-font$fontScale.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        val root = compose.onRoot().fetchSemanticsNode()
        val density = root.layoutInfo.density.density
        val issues = mutableListOf<String>()
        val order = mutableListOf<String>()
        fun label(n: SemanticsNode): String {
            val c = n.config
            return listOfNotNull(
                c.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" "),
                c.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text },
                c.getOrNull(SemanticsProperties.EditableText)?.text,
            ).joinToString(" ").trim()
        }
        fun visit(n: SemanticsNode) {
            val c = n.config
            val clickable = SemanticsActions.OnClick in c
            val role = c.getOrNull(SemanticsProperties.Role)
            val toggle = c.getOrNull(SemanticsProperties.ToggleableState)
            val selected = c.getOrNull(SemanticsProperties.Selected)
            val state = c.getOrNull(SemanticsProperties.StateDescription)
            val heading = SemanticsProperties.Heading in c
            val text = label(n)
            val focusable = clickable || text.isNotEmpty() || toggle != null
            if (focusable && (c.isMergingSemanticsOfDescendants || n.children.isEmpty() || clickable)) {
                // L'area di tocco vera, che Compose allarga fino a 48dp
                // (minimumInteractiveComponentSize) anche se il disegno è più piccolo.
                val w = n.touchBoundsInRoot.width / density
                val h = n.touchBoundsInRoot.height / density
                val tags = listOfNotNull(
                    role?.toString(), if (clickable) "click" else null, toggle?.let { "toggle=$it" },
                    selected?.let { "selected=$it" }, state?.let { "state=$it" }, if (heading) "heading" else null,
                ).joinToString(",")
                order += "  · \"${text.take(70)}\" [$tags] ${w.toInt()}x${h.toInt()}dp"
                if (clickable && text.isEmpty()) issues += "senza etichetta: [$tags] ${w.toInt()}x${h.toInt()}dp"
                if (clickable && (w < 47.5f || h < 47.5f) && w > 0f && h > 0f) {
                    issues += "area toccabile piccola ${w.toInt()}x${h.toInt()}dp: \"${text.take(40)}\""
                }
            }
            if (!c.isMergingSemanticsOfDescendants || !clickable) n.children.forEach(::visit)
        }
        visit(root)
        if (System.getProperty("a11y.report") == "true") {
            println("A11Y $screen-$mode\n" + order.joinToString("\n"))
        }
        assertTrue("$screen-$mode:\n" + issues.joinToString("\n"), issues.isEmpty())
    }

    @Test fun home() = audit("home") {
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
            onQuickTogether = {},
            selectedDurationMinutes = 60,
            onSelectDuration = {},
            showPermissionDialog = false,
            onDismissPermissionDialog = {},
        )
    }

    @Test fun block() {
        SessionManager.getInstance(context).startSession(60)
        audit("block") {
            BlockScreen(
                sessionManager = SessionManager.getInstance(context),
                passwordManager = PasswordManager.getInstance(context),
                phraseText = "“Look out the window.”",
                onExpiredImmediately = {},
                onExpiredNaturally = {},
                onUnlocked = {},
            )
        }
    }

    @Test fun history() = audit("history") { HistoryScreen(sessions = emptyList(), goal = null, onEditGoal = {}) }

    @Test fun quickTogether() = audit("quick-together") {
        QuickTogetherReaderScreen(durationMinutes = 60, activityId = 16, message = null, onAnotherActivity = {}, onOtherWays = {}, onBack = {})
    }

    @Test fun closing() = audit("closing") { ClosingMomentScreen(effectiveMinutes = 60, onDone = { _, _ -> }, onSkip = {}) }

    @Test fun schedules() = audit("schedules") {
        ScheduledPausesScreen(
            schedules = listOf(ScheduledPause(id = 1, days = 0b0011111, startMinuteOfDay = 21 * 60, durationMinutes = 60)),
            onToggle = {},
            onEdit = {},
        )
    }

    @Test fun scheduleEditor() = audit("schedule-editor") {
        ScheduleEditorScreen(
            original = ScheduledPause(id = 1, days = 0b0011111, startMinuteOfDay = 21 * 60, durationMinutes = 60),
            profiles = AllowedAppsManager.getInstance(context).profiles(),
            onSave = {},
            onSkipNext = {},
        )
    }

    @Test fun usagePause() = audit("usage-pause") {
        UsagePauseScreen(
            rule = UsagePauseRule(enabled = true),
            profiles = AllowedAppsManager.getInstance(context).profiles(),
            accessibilityOn = true,
            onChange = {},
        )
    }

    @Test fun settings() = audit("settings") {
        SettingsScreen(
            currentTheme = AppTheme.SAGE,
            partnerName = "Alex",
            passwordManager = PasswordManager.getInstance(context),
            phraseManager = PhraseManager.getInstance(context),
            resumeSignal = 0,
            isAccessibilityServiceEnabled = { true },
            isDndAccessGranted = { true },
            onGrantAccessibility = {},
            onGrantDnd = {},
            onPickTheme = {},
            onManageAppsVerified = {},
            onChangePassword = {},
            onOpenSource = {},
        )
    }
}
