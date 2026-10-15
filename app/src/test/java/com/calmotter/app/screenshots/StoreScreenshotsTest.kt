package com.calmotter.app.screenshots

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import com.calmotter.app.AppTheme
import com.calmotter.app.CalmCountdown
import com.calmotter.app.EndReason
import com.calmotter.app.Mood
import com.calmotter.app.PasswordManager
import com.calmotter.app.ScheduledPause
import com.calmotter.app.SessionHistoryManager
import com.calmotter.app.SessionManager
import com.calmotter.app.SessionRecord
import com.calmotter.app.installFakeAndroidKeyStore
import com.calmotter.app.ui.mascot.OtterZenMark
import com.calmotter.app.ui.screens.BlockScreen
import com.calmotter.app.ui.screens.ClosingMomentScreen
import com.calmotter.app.ui.screens.HistoryScreen
import com.calmotter.app.ui.screens.MainScreen
import com.calmotter.app.ui.screens.QuickTogetherReaderScreen
import com.calmotter.app.ui.screens.ScheduledPausesScreen
import com.calmotter.app.ui.theme.CalmOtterTheme
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/**
 * Le immagini della scheda di Google Play, in italiano e in inglese:
 * screenshot "da store" (didascalia in alto, la schermata vera dell'app in
 * una cornice di telefono) e l'immagine in evidenza. Le schermate sono le
 * stesse composable dell'app con dati finti, come negli screenshot test,
 * quindi si rigenerano dopo ogni cambio di grafica invece di rifare le
 * catture a mano sull'emulatore. Non è un controllo: gira solo su richiesta —
 * `./gradlew testStableDebugUnitTest --tests "*StoreScreenshotsTest*" -Pstore.graphics=true`.
 * Escono in docs/store/screenshots/<lingua>/ e docs/store/graphics/.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StoreScreenshotsTest(private val lang: String) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun params(): List<Array<Any>> = listOf(arrayOf<Any>("it"), arrayOf<Any>("en"))

        /** Il telefono degli screenshot test, che qui viene rimpicciolito nella cornice. */
        private val PHONE_W = 411.dp
        private val PHONE_H = 891.dp
    }

    @get:Rule val compose = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun t(it: String, en: String) = if (lang == "it") it else en

    @Before
    fun setUp() {
        assumeTrue(System.getProperty("store.graphics") == "true")
        installFakeAndroidKeyStore()
        CalmCountdown.random = Random(1)
        compose.mainClock.autoAdvance = false
    }

    private fun save(name: String, dir: File) {
        compose.mainClock.advanceTimeBy(1_000)
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        dir.mkdirs()
        File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** 1080x1920 (9:16, il formato che Play preferisce per i telefoni). */
    private fun storeShot(name: String, caption: String, screen: @Composable () -> Unit) {
        RuntimeEnvironment.setQualifiers("$lang-w360dp-h640dp-xxhdpi")
        compose.setContent {
            CalmOtterTheme(appTheme = AppTheme.SAGE) {
                val colors = MaterialTheme.colorScheme
                Column(
                    modifier = Modifier.fillMaxSize().background(colors.primary),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        caption,
                        color = colors.onPrimary,
                        fontSize = 29.sp,
                        lineHeight = 35.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().height(128.dp).padding(start = 28.dp, end = 28.dp, top = 36.dp),
                    )
                    Phone(scale = 0.54f, screen = screen)
                }
            }
        }
        save(name, File("../docs/store/screenshots/$lang"))
    }

    /** La schermata vera, larga e alta come un telefono, rimpicciolita dentro una cornice. */
    @Composable
    private fun Phone(scale: Float, screen: @Composable () -> Unit) {
        val bezel = 6.dp
        val w: Dp = PHONE_W * scale
        val h: Dp = PHONE_H * scale
        Box(
            modifier = Modifier
                .size(w + bezel * 2, h + bezel * 2)
                .clip(RoundedCornerShape(30.dp))
                .background(Color(0xFF1C2420))
                .padding(bezel),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(w, h)
                    .clip(RoundedCornerShape(24.dp))
                    .border(0.5.dp, Color.Black.copy(alpha = 0.2f), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .requiredSize(PHONE_W, PHONE_H)
                        .graphicsLayer(scaleX = scale, scaleY = scale)
                        .background(MaterialTheme.colorScheme.background),
                ) { screen() }
            }
        }
    }

    private fun daysAgo(days: Long, hour: Int): Long =
        LocalDate.now().minusDays(days).atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private val history by lazy {
        listOf(
            SessionRecord(startTimeMs = daysAgo(0, 9), plannedMinutes = 60, effectiveMinutes = 60, completedNaturally = true,
                isGroupSession = true, companions = "Marta", activityId = 16, mood = Mood.CALM,
                note = t("letto al parco", "read in the park"), endReason = EndReason.NATURAL),
            SessionRecord(startTimeMs = daysAgo(1, 21), plannedMinutes = 45, effectiveMinutes = 45, completedNaturally = true,
                endReason = EndReason.NATURAL),
            SessionRecord(startTimeMs = daysAgo(2, 18), plannedMinutes = 60, effectiveMinutes = 60, completedNaturally = true,
                mood = Mood.CALM, endReason = EndReason.NATURAL),
            SessionRecord(startTimeMs = daysAgo(3, 20), plannedMinutes = 90, effectiveMinutes = 90, completedNaturally = true,
                isGroupSession = true, companions = "Marta\nLuca", activityId = 10, endReason = EndReason.NATURAL),
            SessionRecord(startTimeMs = daysAgo(4, 10), plannedMinutes = 30, effectiveMinutes = 30, completedNaturally = true,
                endReason = EndReason.NATURAL),
        )
    }

    @Test
    fun s1Home() {
        val historyManager = SessionHistoryManager.getInstance(context)
        history.forEach { historyManager.add(it) }
        storeShot("01-home.png", t("Una pausa vera\ndal telefono", "A real break\nfrom your phone")) {
            MainScreen(
                resumeSignal = 0,
                sessionManager = SessionManager.getInstance(context),
                sessionHistoryManager = historyManager,
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
    }

    @Test
    fun s2Block() {
        SessionManager.getInstance(context).startSession(60)
        storeShot("02-pause.png", t("Si ferma tutto,\ntranne il telefono", "Everything stops\nbut the phone")) {
            BlockScreen(
                sessionManager = SessionManager.getInstance(context),
                passwordManager = PasswordManager.getInstance(context),
                phraseText = t("“Guarda fuori dalla finestra. Il mondo è ancora lì.”",
                    "“Look out the window. The world is still there.”"),
                onExpiredImmediately = {},
                onExpiredNaturally = {},
                onUnlocked = {},
            )
        }
    }

    @Test
    fun s3Together() = storeShot("03-together.png", t("Una pausa insieme,\ncon un tocco", "Pause together,\nwith one tap")) {
        QuickTogetherReaderScreen(durationMinutes = 60, activityId = 16, message = null, onAnotherActivity = {}, onBack = {})
    }

    @Test
    fun s4Schedules() = storeShot("04-schedules.png", t("Pause che partono\nda sole", "Pauses that start\non their own")) {
        ScheduledPausesScreen(
            schedules = listOf(
                ScheduledPause(id = 1, days = 0b0011111, startMinuteOfDay = 21 * 60, durationMinutes = 60),
                ScheduledPause(id = 2, days = 0b1000000, startMinuteOfDay = 9 * 60, durationMinutes = 120),
                ScheduledPause(id = 3, days = 0b0011111, startMinuteOfDay = 14 * 60, durationMinutes = 30, windowEndMinuteOfDay = 16 * 60),
            ),
            onToggle = {},
            onEdit = {},
        )
    }

    @Test
    fun s5Closing() = storeShot("05-closing.png", t("Un attimo per dire\ncom'è andata", "A moment to say\nhow it went")) {
        ClosingMomentScreen(effectiveMinutes = 60, onDone = { _, _ -> }, onSkip = {})
    }

    @Test
    fun s6History() = storeShot("06-history.png", t("La tua settimana,\na colpo d'occhio", "Your week,\nat a glance")) {
        HistoryScreen(sessions = history, goal = null, onEditGoal = {}, now = System.currentTimeMillis())
    }

    /**
     * Immagine in evidenza 1024x500: l'otter nello stagno della Home, con la
     * frase della scheda. Niente telefono né schermate (quelle stanno negli
     * screenshot): Play la mostra piccola e a volte ritagliata, quindi poco
     * testo e il soggetto lontano dai bordi.
     */
    @Test
    fun featureGraphic() {
        RuntimeEnvironment.setQualifiers("$lang-w1024dp-h500dp-mdpi")
        compose.setContent {
            CalmOtterTheme(appTheme = AppTheme.SAGE) { FeatureGraphic() }
        }
        save("feature-$lang.png", File("../docs/store/graphics"))
    }

    /**
     * Chiara di proposito: gli screenshot sono su verde scuro, e accanto a
     * loro un'immagine chiara si distingue invece di confondersi.
     */
    @Composable
    private fun FeatureGraphic() {
        val colors = MaterialTheme.colorScheme
        // Centro dello stagno, in dp sull'immagine 1024x500.
        val pondX = 748.dp
        val pondY = 250.dp
        val otter = 190.dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(colors.background, lerp(colors.background, colors.secondary, 0.28f)))),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val c = Offset(pondX.toPx(), pondY.toPx())
                val unit = otter.toPx()
                // Anelli larghi e tenui, come lo stagno della Home a riposo.
                listOf(2.6f to 0.025f, 2.0f to 0.035f, 1.5f to 0.045f, 1.1f to 0.06f).forEach { (r, a) ->
                    drawCircle(colors.primary.copy(alpha = a), radius = unit * r, center = c)
                }
                listOf(3.3f, 2.3f).forEach { r ->
                    drawCircle(colors.primary.copy(alpha = 0.12f), radius = unit * r, center = c, style = Stroke(1.5.dp.toPx()))
                }
                drawCircle(Color.White, radius = unit * 0.7f, center = c)
            }
            // Il disegno dell'otter occupa circa metà del suo riquadro: lo si
            // fa più grande dello stagno e lo si centra sul punto esatto.
            val mark = otter * 1.15f
            OtterZenMark(markSize = mark, modifier = Modifier.offset(x = pondX - mark / 2, y = pondY - mark / 2))
            Column(modifier = Modifier.align(Alignment.CenterStart).padding(start = 80.dp).width(470.dp)) {
                Text(
                    "CALM OTTER",
                    fontSize = 20.sp,
                    letterSpacing = 4.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.primary.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    t("Metti giù\nil telefono,\ndi proposito.", "Put the\nphone down,\non purpose."),
                    fontSize = 56.sp,
                    lineHeight = 62.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.primary,
                )
            }
        }
    }
}
