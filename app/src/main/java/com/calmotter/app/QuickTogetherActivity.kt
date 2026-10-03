package com.calmotter.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import com.calmotter.app.bluetooth.localBluetoothDisplayName
import com.calmotter.app.nfc.GroupPauseNfcReader
import com.calmotter.app.nfc.QuickTogetherInbox
import com.calmotter.app.nfc.QuickTogetherOutcome
import com.calmotter.app.nfc.QuickTogetherProtocol
import com.calmotter.app.ui.screens.QuickTogetherCountdownScreen
import com.calmotter.app.ui.screens.QuickTogetherReaderScreen
import com.calmotter.app.ui.screens.rememberActivitySuggestion
import com.calmotter.app.ui.theme.CalmOtterTheme
import kotlin.random.Random

/**
 * "Avvicina i telefoni" (specs/nfc-quick-together/). Due ingressi:
 * - **lettore** (dalla Home): in ascolto finché la pagina è a schermo; al
 *   tocco propone la pausa all'altro telefono e passa al conto alla rovescia;
 * - **carta** ([cardIntent], dal servizio NFC o dalla notifica): la proposta
 *   ricevuta, con lo stesso conto alla rovescia; se si arriva dopo l'inizio
 *   (notifica aperta tardi) la pausa parte subito, con la stessa fine.
 */
class QuickTogetherActivity : BaseActivity() {

    private lateinit var sessionManager: SessionManager
    private val reader by lazy { GroupPauseNfcReader(this) }
    private val main = Handler(Looper.getMainLooper())

    /** Dopo il tocco, sul lettore: con chi e quando si parte. */
    private var accepted by mutableStateOf<QuickTogetherOutcome.Accepted?>(null)
    private var message by mutableStateOf<String?>(null)
    private var listening = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = SessionManager.getInstance(applicationContext)
        NotificationManagerCompat.from(this).cancel(QuickTogetherInbox.NOTIFICATION_ID)

        if (intent.hasExtra(EXTRA_START_AT)) {
            showCard()
        } else {
            showReader(intent.getIntExtra(EXTRA_DURATION, DEFAULT_SESSION_DURATION_MINUTES))
        }
    }

    // ── Lettore ────────────────────────────────────────────────────────

    private fun showReader(durationMinutes: Int) {
        val groupTag = Random.nextInt(1, Short.MAX_VALUE.toInt())
        val name = localDisplayName()
        setContent {
            CalmOtterTheme(appTheme = ThemeManager.getTheme(this)) {
                val suggestion = rememberActivitySuggestion(durationMinutes)
                val current = accepted
                if (current == null) {
                    // La proposta si legge al momento del tocco: l'attività
                    // può cambiare con ↻ mentre si aspetta.
                    latestActivity = suggestion.activityId
                    QuickTogetherReaderScreen(
                        durationMinutes = durationMinutes,
                        activityId = suggestion.activityId,
                        message = message,
                        onAnotherActivity = { suggestion.another(durationMinutes) },
                        onBack = { finish() },
                    )
                } else {
                    QuickTogetherCountdownScreen(
                        companion = current.companion,
                        durationMinutes = durationMinutes,
                        activityId = suggestion.activityId,
                        startAt = current.startAt,
                        onReady = {
                            sessionManager.startSession(
                                durationMinutes,
                                isGroupSession = true,
                                companions = listOf(current.companion),
                                groupTag = groupTag,
                                isHost = true,
                                activityId = suggestion.activityId,
                            )
                            finish()
                        },
                        onCancel = { finish() },
                    )
                }
            }
        }
        proposal = {
            QuickTogetherProtocol.Proposal(
                version = QuickTogetherProtocol.VERSION,
                durationMinutes = durationMinutes,
                activityId = latestActivity,
                groupTag = groupTag,
                startDelayMillis = QuickTogetherProtocol.START_DELAY_MILLIS,
                name = name,
            )
        }
    }

    @Volatile private var latestActivity = TogetherActivities.NONE
    private var proposal: (() -> QuickTogetherProtocol.Proposal)? = null

    override fun onResume() {
        super.onResume()
        val p = proposal ?: return
        if (accepted != null) return
        listening = true
        reader.startQuickTogether(p) { outcome -> main.post { onOutcome(outcome) } }
    }

    override fun onPause() {
        if (listening) {
            reader.stop()
            listening = false
        }
        super.onPause()
    }

    private fun onOutcome(outcome: QuickTogetherOutcome) {
        if (accepted != null) return
        when (outcome) {
            is QuickTogetherOutcome.Accepted -> {
                accepted = outcome
                reader.stop()
                listening = false
            }
            is QuickTogetherOutcome.Refused -> message = when (outcome.state) {
                QuickTogetherProtocol.State.IN_PAUSE -> getString(R.string.quick_together_refused_in_pause, outcome.companion)
                QuickTogetherProtocol.State.NOT_SET_UP -> getString(R.string.quick_together_refused_setup, outcome.companion)
                else -> getString(R.string.group_pause_other_version_lobby)
            }
            QuickTogetherOutcome.NotReady -> message = getString(R.string.quick_together_not_ready)
        }
    }

    // ── Carta ──────────────────────────────────────────────────────────

    private fun showCard() {
        val companion = intent.getStringExtra(EXTRA_COMPANION).orEmpty()
        val duration = intent.getIntExtra(EXTRA_DURATION, DEFAULT_SESSION_DURATION_MINUTES)
        val activityId = intent.getIntExtra(EXTRA_ACTIVITY, TogetherActivities.NONE)
        val groupTag = intent.getIntExtra(EXTRA_GROUP_TAG, 0)
        val startAt = intent.getLongExtra(EXTRA_START_AT, 0L)
        val endAt = intent.getLongExtra(EXTRA_END_AT, 0L)
        val start = {
            if (System.currentTimeMillis() < endAt && !sessionManager.isSessionActive()) {
                sessionManager.startSession(
                    duration,
                    isGroupSession = true,
                    companions = listOf(companion),
                    groupTag = groupTag,
                    isHost = false,
                    activityId = activityId,
                    endAtMillis = endAt,
                )
            }
            finish()
        }
        // Notifica aperta dopo l'inizio: "Unisciti" è già la conferma.
        if (System.currentTimeMillis() >= startAt) {
            start()
            return
        }
        setContent {
            CalmOtterTheme(appTheme = ThemeManager.getTheme(this)) {
                QuickTogetherCountdownScreen(
                    companion = companion,
                    durationMinutes = duration,
                    activityId = activityId,
                    startAt = startAt,
                    onReady = start,
                    onCancel = { finish() },
                )
            }
        }
    }

    /** Il nome del telefono, come nella lobby Bluetooth: è quello che l'altro vedrà. */
    private fun localDisplayName(): String = localBluetoothDisplayName(this)

    companion object {
        private const val EXTRA_DURATION = "duration"
        private const val EXTRA_COMPANION = "companion"
        private const val EXTRA_ACTIVITY = "activity"
        private const val EXTRA_GROUP_TAG = "group_tag"
        private const val EXTRA_START_AT = "start_at"
        private const val EXTRA_END_AT = "end_at"

        /** Dalla Home: il telefono diventa il lettore, con la durata scelta lì. */
        fun readerIntent(context: Context, durationMinutes: Int): Intent =
            Intent(context, QuickTogetherActivity::class.java).putExtra(EXTRA_DURATION, durationMinutes)

        /** La proposta ricevuta via NFC (vedi QuickTogetherInbox). */
        fun cardIntent(
            context: Context,
            companion: String,
            durationMinutes: Int,
            activityId: Int,
            groupTag: Int,
            startAt: Long,
            endAt: Long,
        ): Intent = Intent(context, QuickTogetherActivity::class.java)
            .putExtra(EXTRA_COMPANION, companion)
            .putExtra(EXTRA_DURATION, durationMinutes)
            .putExtra(EXTRA_ACTIVITY, activityId)
            .putExtra(EXTRA_GROUP_TAG, groupTag)
            .putExtra(EXTRA_START_AT, startAt)
            .putExtra(EXTRA_END_AT, endAt)
    }
}
