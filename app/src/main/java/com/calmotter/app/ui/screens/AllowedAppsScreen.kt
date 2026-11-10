package com.calmotter.app.ui.screens

import android.graphics.Bitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calmotter.app.AllowedAppsManager
import com.calmotter.app.AllowedAppsMode
import com.calmotter.app.AllowedAppsProfile
import com.calmotter.app.sound.RelaxSound
import com.calmotter.app.R
import com.calmotter.app.ui.theme.muted
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width

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
    // Profili (specs/allowed-app-profiles/): quale si sta modificando e le
    // azioni su di essi. Tutta la schermata è già dietro la password.
    profiles: List<AllowedAppsProfile> = emptyList(),
    editingProfileId: Int = AllowedAppsManager.DEFAULT_PROFILE_ID,
    canCreateProfile: Boolean = false,
    onSelectProfile: (Int) -> Unit = {},
    onCreateProfile: (String) -> Unit = {},
    onRenameProfile: (String) -> Unit = {},
    onDeleteProfile: () -> Unit = {},
    onChangeMode: (AllowedAppsMode) -> Unit = {},
    onChangeSound: (RelaxSound?) -> Unit = {},
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val filteredApps = remember(apps, searchQuery) {
        if (searchQuery.isEmpty()) apps
        else apps.filter { it.label.contains(searchQuery, ignoreCase = true) }
    }
    val editing = profiles.firstOrNull { it.id == editingProfileId }
    val editingMode = editing?.mode ?: AllowedAppsMode.ALLOWLIST
    val pickedCount = apps.count { it.isAllowed }

    // Il meno possibile sopra la lista (segnalato: "troppe pillole e troppo
    // testo, le app troppo in basso"): profilo e suono sono due righe che
    // aprono un menu e un dialogo, la regola un solo controllo a due
    // segmenti; cosa significa una spunta lo dice la riga del conteggio
    // subito sopra le app. Tutto scorre insieme alla lista.
    PullToRefreshBox(
        isRefreshing = isLoading,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp),
        ) {
            if (profiles.isNotEmpty() && editing != null) {
                item(key = "profile") {
                    ProfileTabs(
                        profiles = profiles,
                        editing = editing,
                        canCreateProfile = canCreateProfile,
                        onSelectProfile = onSelectProfile,
                        onCreateProfile = onCreateProfile,
                        onRenameProfile = onRenameProfile,
                        onDeleteProfile = onDeleteProfile,
                    )
                }
                item(key = "sound") {
                    SoundRow(selected = editing.sound, onChange = onChangeSound)
                }
                item(key = "mode") {
                    ModePicker(mode = editingMode, onChange = onChangeMode)
                }
            }
            item(key = "search") {
                CalmTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = stringResource(R.string.allowed_apps_search),
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
                        .padding(horizontal = PageMargin, vertical = 12.dp)
                )
            }
            item(key = "count") {
                Text(
                    text = if (editingMode == AllowedAppsMode.BLOCKLIST) {
                        stringResource(R.string.allowed_apps_count_blocklist, pickedCount)
                    } else {
                        stringResource(R.string.allowed_apps_count_allowlist, pickedCount, AllowedAppsManager.MAX_ALLOWED_APPS)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PageMargin)
                        .padding(bottom = 6.dp)
                        .semantics { heading() },
                )
            }
            if (!isLoading && filteredApps.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.allowed_apps_empty_filtered),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        fontSize = 15.sp,
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                    )
                }
            } else {
                items(filteredApps, key = { it.packageName }) { item ->
                    AppRow(item = item, onToggle = onToggle)
                }
            }
        }
    }
}

/**
 * Una riga "etichetta … valore", alta almeno 48dp, tutta toccabile:
 * profilo e suono stanno ciascuno su una riga invece che in una fila di
 * pillole. TalkBack la legge una volta, "Profilo, Standard".
 */
@Composable
private fun ChoiceRow(label: String, value: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = PageMargin, vertical = 6.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.muted(0.7f),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp).size(20.dp),
        )
    }
}

/** Il suono del profilo: una riga, e le scelte in un dialogo (come in Impostazioni). */
@Composable
private fun SoundRow(selected: RelaxSound?, onChange: (RelaxSound?) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    ChoiceRow(
        label = stringResource(R.string.relax_sound_label),
        value = stringResource(selected?.labelRes ?: R.string.relax_sound_default),
        icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        onClick = { open = true },
    )
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.relax_sound_label)) },
            text = {
                SoundPicker(
                    selected = selected,
                    allowDefault = true,
                    showLabel = false,
                    onChange = { onChange(it); open = false },
                )
            },
            confirmButton = {
                TextButton(onClick = { open = false }) { Text(stringResource(android.R.string.ok)) }
            },
        )
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
            .padding(horizontal = PageMargin, vertical = 4.dp)
            // La card è l'interruttore (lo Switch sotto solo lo mostra):
            // senza questo TalkBack diceva "YouTube" senza dire se resta.
            .semantics { role = Role.Switch; toggleableState = ToggleableState(item.isAllowed) },
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

/** Lunghezza massima del nome di un profilo: deve stare in una pillola. */
private const val PROFILE_NAME_MAX = 20

/**
 * I profili come schede in cima alla pagina: tutti visibili, uno tocco per
 * passare dall'uno all'altro, "+" in fondo finché c'è posto e la matita
 * (nome, e da lì elimina) per un profilo che non è lo Standard. Prima era
 * una fila di pillole, poi una riga con un menu a tendina ("confusionaria").
 */
@Composable
private fun ProfileTabs(
    profiles: List<AllowedAppsProfile>,
    editing: AllowedAppsProfile,
    canCreateProfile: Boolean,
    onSelectProfile: (Int) -> Unit,
    onCreateProfile: (String) -> Unit,
    onRenameProfile: (String) -> Unit,
    onDeleteProfile: () -> Unit,
) {
    var naming by rememberSaveable { mutableStateOf<String?>(null) } // null = nessun dialogo; "" = nuovo
    var editingName by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }

    val primary = MaterialTheme.colorScheme.primary
    val selectedIndex = profiles.indexOfFirst { it.id == editing.id }.coerceAtLeast(0)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        // Colori espliciti: i default usano ruoli che le palette non
        // personalizzano (vedi CLAUDE.md, "Theming"). edgePadding + il
        // padding interno della Tab (16dp) = PageMargin.
        PrimaryScrollableTabRow(
            selectedTabIndex = selectedIndex,
            modifier = Modifier.weight(1f),
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = primary,
            edgePadding = PageMargin - 16.dp,
            minTabWidth = 48.dp,
            indicator = {
                TabRowDefaults.PrimaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(selectedIndex, matchContentSize = true),
                    width = Dp.Unspecified,
                    color = primary,
                )
            },
            // La linea sta sotto tutta la riga (matita compresa), non solo
            // sotto le schede.
            divider = {},
        ) {
            profiles.forEach { profile ->
                Tab(
                    selected = profile.id == editing.id,
                    onClick = { onSelectProfile(profile.id) },
                    selectedContentColor = primary,
                    unselectedContentColor = MaterialTheme.colorScheme.muted(0.7f),
                    text = {
                        Text(
                            profile.name,
                            fontWeight = if (profile.id == editing.id) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
            if (canCreateProfile) {
                Tab(
                    selected = false,
                    onClick = { naming = "" },
                    unselectedContentColor = primary,
                    icon = {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.allowed_profile_new_menu))
                    },
                )
            }
        }
        // Lo Standard non si rinomina né si elimina: la matita c'è solo
        // sugli altri profili.
        if (!editing.isDefault) {
            IconButton(onClick = { editingName = true }, modifier = Modifier.padding(end = PageMargin - 12.dp)) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.allowed_profile_edit_title),
                    tint = primary,
                )
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

    naming?.let { initial ->
        ProfileNameDialog(
            initial = initial,
            onDismiss = { naming = null },
            onConfirm = { onCreateProfile(it); naming = null },
        )
    }
    if (editingName) {
        ProfileNameDialog(
            initial = editing.name,
            title = stringResource(R.string.allowed_profile_edit_title),
            onDismiss = { editingName = false },
            onConfirm = { onRenameProfile(it); editingName = false },
            onDelete = { editingName = false; confirmingDelete = true },
        )
    }
    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.allowed_profile_delete_title, editing.name)) },
            text = { Text(stringResource(R.string.allowed_profile_delete_body)) },
            confirmButton = {
                TextButton(onClick = { onDeleteProfile(); confirmingDelete = false }) {
                    Text(stringResource(R.string.allowed_profile_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

/**
 * La regola del profilo in un solo controllo a due segmenti: "Blocca tutto,
 * tranne" (allowlist) o "Blocca solo" (blocklist). Niente etichetta né
 * paragrafo: la riga del conteggio sopra la lista dice cosa vuol dire una
 * spunta. Cambiare regola non svuota [AllowedAppsProfile.packages]: le
 * stesse app cambiano significato.
 */
@Composable
private fun ModePicker(mode: AllowedAppsMode, onChange: (AllowedAppsMode) -> Unit) {
    val options = listOf(
        AllowedAppsMode.ALLOWLIST to R.string.allowed_apps_mode_allowlist,
        AllowedAppsMode.BLOCKLIST to R.string.allowed_apps_mode_blocklist,
    )
    // Colori espliciti: i default usano secondaryContainer, che le palette
    // non personalizzano (vedi CLAUDE.md, "Theming").
    val colors = SegmentedButtonDefaults.colors(
        activeContainerColor = MaterialTheme.colorScheme.primary,
        activeContentColor = MaterialTheme.colorScheme.onPrimary,
        activeBorderColor = MaterialTheme.colorScheme.primary,
        inactiveContainerColor = MaterialTheme.colorScheme.background,
        inactiveContentColor = MaterialTheme.colorScheme.onSurface,
        inactiveBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
    )
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = PageMargin, vertical = 8.dp),
    ) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = mode == value,
                onClick = { onChange(value) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                colors = colors,
                icon = {},
                label = { Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}

@Composable
private fun ProfileNameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    title: String = stringResource(R.string.allowed_profile_name_title),
    onDelete: (() -> Unit)? = null,
) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                CalmTextField(
                    value = name,
                    onValueChange = { name = it.take(PROFILE_NAME_MAX) },
                    label = stringResource(R.string.allowed_profile_name_hint),
                )
                // Separata dal nome e in rosso: l'unica azione distruttiva
                // del dialogo, che comunque chiede conferma.
                if (onDelete != null) {
                    Spacer(Modifier.height(16.dp))
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.allowed_profile_delete_action))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.allowed_profile_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}
