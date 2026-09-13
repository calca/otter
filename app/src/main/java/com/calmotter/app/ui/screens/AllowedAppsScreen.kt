package com.calmotter.app.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calmotter.app.R

/**
 * Modello UI di una singola app installata sul dispositivo.
 *
 * L'icona è già una [Bitmap] (non il [android.graphics.drawable.Drawable]
 * grezzo restituito da PackageManager): la conversione avviene una sola
 * volta in AllowedAppsActivity.loadApps(), sullo stesso thread IO che già
 * caricava l'icona, tramite androidx.core.graphics.drawable.toBitmap().
 * Compose Image richiede un ImageBitmap/Painter, non un Drawable — qui in
 * più teniamo il Bitmap "grezzo" nel data model e lo convertiamo in
 * ImageBitmap (view.asImageBitmap(), operazione economica) solo al
 * momento del disegno di ogni riga.
 */
data class AppItem(
    val label: String,
    val packageName: String,
    val icon: Bitmap,
    val isAllowed: Boolean,
)

/**
 * Elenco delle app extra consentite durante una pausa (step 5 della
 * migrazione a Compose). Riproduce il comportamento della precedente
 * AllowedAppsActivity XML/View:
 * - spinner (in cima, via pull-to-refresh) mentre [isLoading] è true, poi
 *   la lista (il caricamento vero e proprio resta nell'Activity, su
 *   Dispatchers.IO, per non bloccare la UI, dato che può richiedere qualche
 *   secondo su telefoni con molte app);
 * - filtro live per sottostringa del nome (case-insensitive) sulla lista
 *   già caricata, senza ri-interrogare il PackageManager a ogni carattere;
 * - salvataggio immediato ad ogni toggle tramite [onToggle], nessun
 *   pulsante Salva esplicito;
 * - tap su tutta la riga per attivare/disattivare: lo Switch si limita a
 *   riflettere lo stato (onCheckedChange = null) esattamente come
 *   clickable="false"/focusable="false" nell'item XML originale — è la riga
 *   nel suo complesso a gestire il click, non lo Switch. Una Checkbox era la
 *   scelta iniziale ma è stata sostituita su richiesta ("invece della
 *   checkbox un toggle?") — coerente con l'uso di Switch altrove nell'app
 *   (vedi calmSwitchColors()/PhrasesCard in SettingsScreen.kt).
 *
 * [onRefresh] ricarica l'elenco delle app installate da PackageManager: un
 * app appena installata/disinstallata mentre questa schermata era già
 * aperta non compare/scompare altrimenti finché non la si riapre.
 */
@Composable
fun AllowedAppsScreen(
    isLoading: Boolean,
    apps: List<AppItem>,
    onToggle: (AppItem) -> Unit,
    onRefresh: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredApps = remember(apps, searchQuery) {
        if (searchQuery.isEmpty()) apps
        else apps.filter { it.label.contains(searchQuery, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text(stringResource(R.string.allowed_apps_search)) },
            singleLine = true,
            leadingIcon = {
                Icon(imageVector = Icons.Default.Search, contentDescription = null)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = stringResource(R.string.allowed_apps_clear_search)
                        )
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )

        Text(
            text = stringResource(R.string.allowed_apps_intro),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp)
        )

        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (!isLoading && filteredApps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.allowed_apps_empty_filtered),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        fontSize = 15.sp,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(filteredApps, key = { it.packageName }) { item ->
                        AppRow(item = item, onToggle = onToggle)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    item: AppItem,
    onToggle: (AppItem) -> Unit,
) {
    Card(
        onClick = { onToggle(item) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            // Tinta di primary anche per le righe non consentite, non
            // colorScheme.surface: nella palette chiara surface COINCIDE con
            // background (vedi CalmOtterTheme.kt), quindi una card "plain
            // surface" è visivamente indistinguibile dallo sfondo pagina —
            // ogni riga sembrava fluttuare senza contorno. Due intensità
            // diverse di primary bastano a restare leggibili in entrambi i
            // temi mantenendo lo stesso "ingrediente" delle card di Settings
            // (vedi calmSwitchColors()/PermissionStatusCard in
            // SettingsScreen.kt).
            containerColor = if (item.isAllowed) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                bitmap = item.icon.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = item.label,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            )
            Switch(
                checked = item.isAllowed,
                // Nessun onCheckedChange: lo Switch non gestisce il tap da
                // solo (equivalente Compose di clickable="false"/focusable="false"
                // nell'item XML originale) — riflette soltanto lo stato, il tap
                // è gestito dall'intera riga tramite Card(onClick = ...) sopra.
                onCheckedChange = null,
                colors = calmSwitchColors(),
            )
        }
    }
}

/**
 * Colori dello Switch ristretti ai ruoli personalizzati per palette in
 * CalmOtterTheme.kt — stessa istanza di [calmSwitchColors] già definita in
 * SettingsScreen.kt (privata a quel file, non riesportabile, da qui la
 * duplicazione): SwitchDefaults.colors() di default userebbe surfaceVariant
 * per la track non selezionata, un ruolo NON personalizzato che resta fisso
 * al viola-grigio di base di Material3 a prescindere dalla palette scelta.
 */
@Composable
private fun calmSwitchColors(): SwitchColors = SwitchDefaults.colors(
    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
    checkedTrackColor = MaterialTheme.colorScheme.primary,
    checkedBorderColor = MaterialTheme.colorScheme.primary,
    uncheckedThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
    uncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
    uncheckedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
)
