package com.calmotter.app.ui.screens

import android.view.View
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.calmotter.app.AppTheme
import com.calmotter.app.PhraseManager
import com.calmotter.app.R

/**
 * Configurazione: personalizzazione (palette) e le azioni protette da
 * password (cambio password, elenco app consentite), più le preferenze
 * non critiche (frasi riflessive). Spostate qui dalla Home — vedi
 * MainScreen.kt — per tenere quest'ultima ridotta al solo avvio di una
 * pausa: controllare/cambiare queste impostazioni è un'azione occasionale,
 * non quella che l'utente compie ogni volta che apre l'app.
 */
@Composable
fun SettingsScreen(
    currentTheme: AppTheme,
    phraseManager: PhraseManager,
    onPickTheme: (AppTheme) -> Unit,
    onManageApps: () -> Unit,
    onChangePassword: () -> Unit,
) {
    var phrasesEnabled by remember { mutableStateOf(phraseManager.isEnabled()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_theme_label),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )
        ThemePicker(currentTheme = currentTheme, onPickTheme = onPickTheme)

        Button(
            onClick = onChangePassword,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp)
        ) {
            Text(stringResource(R.string.change_password))
        }

        Button(
            onClick = onManageApps,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Text(stringResource(R.string.manage_allowed_apps))
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 24.dp)
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
