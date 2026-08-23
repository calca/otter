package com.calmotter.app.ui.screens

import android.view.View
import android.widget.NumberPicker
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.calmotter.app.AppTheme
import com.calmotter.app.PhraseManager
import com.calmotter.app.R
import com.calmotter.app.SessionManager

// Indice 1 = 30 min, indice 2 = 60 min, ... fino a 4 ore, a passi di 30 minuti
// (stessa tabella usata da MainActivity prima della migrazione a Compose).
private val DURATION_LABELS = arrayOf(
    "30 min", "1 h", "1 h 30", "2 h", "2 h 30", "3 h", "3 h 30", "4 h"
)

/**
 * Schermata home/dashboard (pilota #3 della migrazione a Compose).
 * Riproduce esattamente il comportamento della precedente bindMainScreen()
 * XML/View: stessa priorità di visibilità/enabled dei controlli, stesso
 * NumberPicker (nessun equivalente Compose, avvolto via AndroidView), stesso
 * selettore tema con i drawable esistenti (stroke di selezione pixel-identico
 * via AndroidView anziché una riproduzione nativa).
 *
 * Diversi valori (stato accessibilità/DND/home, sessione attiva) dipendono da
 * stato del sistema operativo che Compose non osserva automaticamente: vanno
 * ricalcolati manualmente a ogni onResume() dell'Activity tramite
 * [resumeSignal] (vedi MainActivity).
 */
@Composable
fun MainScreen(
    resumeSignal: Int,
    sessionManager: SessionManager,
    phraseManager: PhraseManager,
    currentTheme: AppTheme,
    isAccessibilityServiceEnabled: () -> Boolean,
    isDndAccessGranted: () -> Boolean,
    isDefaultHome: () -> Boolean,
    onGrantAccessibility: () -> Unit,
    onGrantDnd: () -> Unit,
    onSetHome: () -> Unit,
    onManageApps: () -> Unit,
    onChangePassword: () -> Unit,
    onHistory: () -> Unit,
    onPickTheme: (AppTheme) -> Unit,
) {
    val context = LocalContext.current

    var accessibilityOk by remember { mutableStateOf(false) }
    var dndOk by remember { mutableStateOf(false) }
    var homeOk by remember { mutableStateOf(false) }
    var sessionActive by remember { mutableStateOf(false) }
    var remainingMillis by remember { mutableStateOf(0L) }
    var phrasesEnabled by remember { mutableStateOf(phraseManager.isEnabled()) }

    fun refreshDerivedState() {
        accessibilityOk = isAccessibilityServiceEnabled()
        dndOk = isDndAccessGranted()
        homeOk = isDefaultHome()
        sessionActive = sessionManager.isSessionActive()
        remainingMillis = sessionManager.remainingMillis()
        phrasesEnabled = phraseManager.isEnabled()
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
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.app_name),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        ThemePicker(currentTheme = currentTheme, onPickTheme = onPickTheme)

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

        Button(
            onClick = {
                val durationMinutes = (numberPicker?.value ?: 1) * 30
                sessionManager.startSession(durationMinutes)
                Toast.makeText(context, context.getString(R.string.session_started), Toast.LENGTH_SHORT).show()
                refreshDerivedState()
            },
            enabled = accessibilityOk && dndOk && !sessionActive,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.start_pause))
        }

        Button(
            onClick = onManageApps,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp)
        ) {
            Text(stringResource(R.string.manage_allowed_apps))
        }

        Button(
            onClick = onChangePassword,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Text(stringResource(R.string.change_password))
        }

        Button(
            onClick = onHistory,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Text(stringResource(R.string.history_title))
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Checkbox(
                checked = phrasesEnabled,
                onCheckedChange = { checked ->
                    phrasesEnabled = checked
                    phraseManager.setEnabled(checked)
                }
            )
            Text(
                text = stringResource(R.string.phrases_toggle_label),
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

/**
 * Selettore tema a 3 pallini (Salvia/Lavanda/Terracotta): riusa i drawable
 * StateListDrawable esistenti (theme_dot_*.xml, con anello di selezione)
 * tramite AndroidView, pixel-identici alla versione XML — non c'è un
 * componente Material3 equivalente e riprodurre da zero l'anello di
 * selezione rischierebbe di introdurre differenze visive sottili.
 */
@Composable
private fun ThemePicker(
    currentTheme: AppTheme,
    onPickTheme: (AppTheme) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        ThemeDot(
            drawableRes = R.drawable.theme_dot_sage,
            labelRes = R.string.theme_sage,
            selected = currentTheme == AppTheme.SAGE,
            onClick = { onPickTheme(AppTheme.SAGE) },
            modifier = Modifier.weight(1f)
        )
        ThemeDot(
            drawableRes = R.drawable.theme_dot_lavender,
            labelRes = R.string.theme_lavender,
            selected = currentTheme == AppTheme.LAVENDER,
            onClick = { onPickTheme(AppTheme.LAVENDER) },
            modifier = Modifier.weight(1f)
        )
        ThemeDot(
            drawableRes = R.drawable.theme_dot_terracotta,
            labelRes = R.string.theme_terracotta,
            selected = currentTheme == AppTheme.TERRACOTTA,
            onClick = { onPickTheme(AppTheme.TERRACOTTA) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ThemeDot(
    drawableRes: Int,
    labelRes: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AndroidView(
            factory = { ctx -> View(ctx).apply { setBackgroundResource(drawableRes) } },
            update = { view -> view.isSelected = selected },
            modifier = Modifier.size(36.dp)
        )
        Text(
            text = stringResource(labelRes),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
