package com.calmotter.app.ui.screens

import android.widget.NumberPicker
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.calmotter.app.R
import com.calmotter.app.SessionHistoryManager
import com.calmotter.app.SessionManager
import com.calmotter.app.SessionStreak

// Indice 1 = 30 min, indice 2 = 60 min, ... fino a 4 ore, a passi di 30 minuti
// (stessa tabella usata da MainActivity prima della migrazione a Compose).
private val DURATION_LABELS = arrayOf(
    "30 min", "1 h", "1 h 30", "2 h", "2 h 30", "3 h", "3 h 30", "4 h"
)

/**
 * Schermata home: ridotta al solo avvio di una pausa ("design calmo" —
 * tema, cambio password, gestione app consentite e frasi riflessive sono
 * stati spostati su SettingsScreen, raggiungibile dall'icona ingranaggio,
 * perché sono azioni occasionali, non quelle compiute ogni volta che si
 * apre l'app). La Cronologia resta qui (bottone + anteprima streak):
 * controllare i propri progressi è un'azione frequente e gratificante,
 * non una configurazione.
 *
 * Diversi valori (stato accessibilità/DND/home, sessione attiva, streak)
 * dipendono da stato esterno che Compose non osserva automaticamente:
 * vanno ricalcolati manualmente a ogni onResume() dell'Activity tramite
 * [resumeSignal] (vedi MainActivity).
 */
@Composable
fun MainScreen(
    resumeSignal: Int,
    sessionManager: SessionManager,
    sessionHistoryManager: SessionHistoryManager,
    isAccessibilityServiceEnabled: () -> Boolean,
    isDndAccessGranted: () -> Boolean,
    isDefaultHome: () -> Boolean,
    onGrantAccessibility: () -> Unit,
    onGrantDnd: () -> Unit,
    onSetHome: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current

    var accessibilityOk by remember { mutableStateOf(false) }
    var dndOk by remember { mutableStateOf(false) }
    var homeOk by remember { mutableStateOf(false) }
    var sessionActive by remember { mutableStateOf(false) }
    var remainingMillis by remember { mutableStateOf(0L) }
    var streakDays by remember { mutableIntStateOf(0) }

    fun refreshDerivedState() {
        accessibilityOk = isAccessibilityServiceEnabled()
        dndOk = isDndAccessGranted()
        homeOk = isDefaultHome()
        sessionActive = sessionManager.isSessionActive()
        remainingMillis = sessionManager.remainingMillis()
        streakDays = SessionStreak.currentStreakDays(sessionHistoryManager.getAll())
    }

    // Rieseguito a ogni onResume() dell'Activity (resumeSignal incrementato
    // lì): equivalente del vecchio refreshUi()/bindMainScreen() chiamato da
    // onResume(), dato che setContent {} viene invocato una sola volta.
    LaunchedEffect(resumeSignal) {
        refreshDerivedState()
    }

    // Riferimento al NumberPicker creato dall'AndroidView: letto solo al
    // click del pulsante di avvio, esattamente come durationPicker.value
    // nella versione precedente.
    var numberPicker by remember { mutableStateOf<NumberPicker?>(null) }

    val statusText = when {
        sessionActive -> {
            val remainingMin = (remainingMillis / 60_000L).toInt() + 1
            stringResource(R.string.session_active_with_time, remainingMin)
        }
        !accessibilityOk || !dndOk -> stringResource(R.string.permissions_missing)
        else -> stringResource(R.string.ready)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.app_name),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onSettings) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.settings_title),
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        Text(
            text = statusText,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )

        Text(
            text = stringResource(R.string.duration_label),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth()
        )

        if (!sessionActive) {
            AndroidView(
                factory = { ctx ->
                    NumberPicker(ctx).apply {
                        minValue = 1
                        maxValue = DURATION_LABELS.size
                        displayedValues = DURATION_LABELS
                        wrapSelectorWheel = false
                    }.also { numberPicker = it }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )
        }

        if (!sessionActive && (!accessibilityOk || !dndOk)) {
            Button(
                onClick = {
                    if (!accessibilityOk) onGrantAccessibility() else if (!dndOk) onGrantDnd()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Text(stringResource(R.string.grant_permissions))
            }
        }

        if (!sessionActive && !homeOk) {
            Button(
                onClick = onSetHome,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Text(stringResource(R.string.set_as_home))
            }
        }

        val sessionStartedText = stringResource(R.string.session_started)

        Button(
            onClick = {
                val durationMinutes = (numberPicker?.value ?: 1) * 30
                sessionManager.startSession(durationMinutes)
                Toast.makeText(context, sessionStartedText, Toast.LENGTH_SHORT).show()
                refreshDerivedState()
            },
            enabled = accessibilityOk && dndOk && !sessionActive,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.start_pause))
        }

        if (streakDays >= 1) {
            Text(
                text = stringResource(R.string.streak_days, streakDays),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 24.dp)
            )
        }

        Button(
            onClick = onHistory,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (streakDays >= 1) 8.dp else 24.dp)
        ) {
            Text(stringResource(R.string.history_title))
        }
    }
}
