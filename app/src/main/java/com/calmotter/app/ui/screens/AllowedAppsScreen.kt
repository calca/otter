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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
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
 * migrazione a Compose). Riproduce esattamente il comportamento della
 * precedente AllowedAppsActivity XML/View:
 * - spinner mentre [isLoading] è true, poi la lista (il caricamento vero e
 *   proprio resta nell'Activity, su Dispatchers.IO, per non bloccare la UI,
 *   dato che può richiedere qualche secondo su telefoni con molte app);
 * - filtro live per sottostringa del nome (case-insensitive) sulla lista
 *   già caricata, senza ri-interrogare il PackageManager a ogni carattere;
 * - salvataggio immediato ad ogni toggle tramite [onToggle], nessun
 *   pulsante Salva esplicito;
 * - tap su tutta la riga per attivare/disattivare: la Checkbox si limita a
 *   riflettere lo stato (onCheckedChange = null) esattamente come
 *   clickable="false"/focusable="false" nell'item XML originale — è la riga
 *   nel suo complesso a gestire il click, non la Checkbox.
 */
@Composable
fun AllowedAppsScreen(
    isLoading: Boolean,
    apps: List<AppItem>,
    onToggle: (AppItem) -> Unit,
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

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                CircularProgressIndicator()
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
            Checkbox(
                checked = item.isAllowed,
                // Nessun onCheckedChange: la Checkbox non gestisce il click da
                // sola (equivalente Compose di clickable="false"/focusable="false"
                // nell'item XML originale) — riflette soltanto lo stato, il tap
                // è gestito dall'intera riga tramite Card(onClick = ...) sopra.
                onCheckedChange = null,
            )
        }
    }
}
