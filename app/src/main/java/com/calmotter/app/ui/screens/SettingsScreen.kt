package com.calmotter.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.em
import com.calmotter.app.ScheduleManager
import com.calmotter.app.SlowExitManager
import com.calmotter.app.WeeklySummary
import com.calmotter.app.ui.mascot.SprigMark
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import com.calmotter.app.BuildConfig
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.calmotter.app.AppTheme
import com.calmotter.app.PasswordManager
import com.calmotter.app.PhraseManager
import com.calmotter.app.R

/**
 * Configurazione, in quest'ordine — Tema, Password, Home, Pausa, Permessi,
 * Info (riordinato, vedi il commento sopra alle chiamate delle sezioni per
 * il perché): personalizzazione (palette, griglia 2×2); lo stato della
 * password (chi l'ha impostata, cambio password, elenco app consentite);
 * app Home (sezione propria, un solo toggle); le preferenze non critiche
 * (frasi riflessive, un toggle); stato di accessibilità/DND (spostati qui
 * dalla Home, vedi MainScreen.kt e home-and-settings/requirements.md — non
 * bloccano l'avvio di una pausa in sé, quindi controllarli a ogni apertura
 * dell'app era percepito come fastidioso); e una sezione "Info" con link
 * esterni (repo GitHub, licenza, sviluppatore). Ogni sezione è una card a
 * bassa opacità con righe divise da [HorizontalDivider] — stesso linguaggio
 * visivo in tutta la schermata, niente più pulsanti pieni o un Checkbox
 * isolato.
 *
 * Diversi valori (stato accessibilità/DND/Home) dipendono da stato esterno
 * che Compose non osserva automaticamente: vanno ricalcolati manualmente a
 * ogni onResume() dell'Activity tramite [resumeSignal] (vedi
 * SettingsActivity) — stesso pattern di MainScreen/OnboardingScreen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    currentTheme: AppTheme,
    partnerName: String?,
    passwordManager: PasswordManager,
    phraseManager: PhraseManager,
    resumeSignal: Int,
    isAccessibilityServiceEnabled: () -> Boolean,
    isDndAccessGranted: () -> Boolean,
    isDefaultHome: () -> Boolean,
    onGrantAccessibility: () -> Unit,
    onGrantDnd: () -> Unit,
    onSetHome: () -> Unit,
    onPickTheme: (AppTheme) -> Unit,
    onManageAppsVerified: () -> Unit,
    onScheduledPauses: () -> Unit = {},
    onChangePassword: () -> Unit,
    onOpenGitHub: () -> Unit,
    onOpenLicense: () -> Unit,
    onOpenDeveloper: () -> Unit,
) {
    var accessibilityOk by remember { mutableStateOf(false) }
    var dndOk by remember { mutableStateOf(false) }
    var homeOk by remember { mutableStateOf(false) }

    LaunchedEffect(resumeSignal) {
        accessibilityOk = isAccessibilityServiceEnabled()
        dndOk = isDndAccessGranted()
        homeOk = isDefaultHome()
    }

    var phrasesEnabled by remember { mutableStateOf(phraseManager.isEnabled()) }
    // Gestito qui (non più in SettingsActivity via un AlertDialog.Builder di
    // sistema con un EditText — non Material, stonava nel resto di un'app
    // 100% Compose) tramite lo stesso PasswordVerifyDialog usato da
    // BlockScreen per sbloccare una sessione — vedi la sua stessa doc.
    var showManageAppsDialog by remember { mutableStateOf(false) }

    // Uscita lenta: prima la password, poi la scelta (Spenta / 5 / 10 / 15).
    val context = LocalContext.current
    val slowExitManager = remember { SlowExitManager.getInstance(context) }
    var slowExitEnabled by remember { mutableStateOf(slowExitManager.isEnabled()) }
    var slowExitWait by remember { mutableIntStateOf(slowExitManager.waitMinutes()) }
    var showSlowExitPassword by remember { mutableStateOf(false) }
    var showSlowExitChoice by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        // Ordine: Tema, Password, Home, Pausa, Permessi, Info — segnalato
        // ("riordina le sezioni"). Tema e Password in cima perché sono le
        // due scelte che qualcuno arriva qui a *fare*; Permessi scende
        // verso il fondo perché è uno stato da consultare, non un'azione,
        // e non richiede più attenzione delle altre sezioni solo perché è
        // di sistema — se manca qualcosa di bloccante, l'app lo dice già
        // al tap sull'otter (vedi PermissionExplainerDialog in
        // MainScreen.kt), non serve ripeterlo qui in prima posizione.
        SectionLabel(stringResource(R.string.settings_theme_label), topPadding = 0.dp)
        ThemeListCard(currentTheme = currentTheme, onPickTheme = onPickTheme)

        SectionLabel(stringResource(R.string.settings_password_label))
        PasswordCard(
            partnerName = partnerName,
            onChangePassword = onChangePassword,
            onManageApps = { showManageAppsDialog = true },
            slowExitValue = if (slowExitEnabled) {
                stringResource(R.string.settings_slow_exit_value, slowExitWait)
            } else {
                stringResource(R.string.settings_slow_exit_off)
            },
            onSlowExit = { showSlowExitPassword = true },
        )

        SectionLabel(stringResource(R.string.settings_schedule_label))
        val activeSchedules = remember(resumeSignal) {
            ScheduleManager.getInstance(context).all().count { it.enabled }
        }
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                SettingsActionRow(
                    label = stringResource(R.string.settings_schedule_label),
                    value = if (activeSchedules == 0) {
                        stringResource(R.string.settings_schedule_none)
                    } else {
                        pluralStringResource(R.plurals.settings_schedule_count, activeSchedules, activeSchedules)
                    },
                    onClick = onScheduledPauses,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
            }
        }

        SectionLabel(stringResource(R.string.settings_home_label))
        HomeCard(homeOk = homeOk, onSetHome = onSetHome)

        SectionLabel(stringResource(R.string.settings_pause_experience_label))
        PhrasesCard(checked = phrasesEnabled, onCheckedChange = { checked ->
            phrasesEnabled = checked
            phraseManager.setEnabled(checked)
        })
        // La nota della domenica sera (specs/weekly-summary/): niente
        // password, è solo una notifica. Se le notifiche dell'app sono
        // spente lo dice, senza chiedere il permesso solo per questo.
        var weeklyNote by remember { mutableStateOf(WeeklySummary.isEnabled(context)) }
        val notificationsOn = remember(resumeSignal) { WeeklySummary.notificationsAllowed(context) }
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SettingsRowIcon {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.weekly_note_toggle_label), color = MaterialTheme.colorScheme.onSurface)
                    if (!notificationsOn) {
                        Text(
                            stringResource(R.string.weekly_note_notifications_off),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                }
                Switch(
                    checked = weeklyNote,
                    onCheckedChange = {
                        weeklyNote = it
                        WeeklySummary.setEnabled(context, it)
                    },
                    colors = settingsSwitchColors(),
                )
            }
        }

        SectionLabel(stringResource(R.string.settings_permissions_label))
        // Prima delle impostazioni di sistema, l'informativa sull'accessibilità.
        val grantAccessibility = rememberAccessibilityDisclosure(onGrantAccessibility)
        PermissionStatusCard(
            accessibilityOk = accessibilityOk,
            dndOk = dndOk,
            onGrantAccessibility = grantAccessibility,
            onGrantDnd = onGrantDnd,
        )

        SectionLabel(stringResource(R.string.settings_info_label))
        InfoCard(
            onOpenGitHub = onOpenGitHub,
            onOpenLicense = onOpenLicense,
            onOpenDeveloper = onOpenDeveloper,
        )

        // Versione in chiusura di pagina (redesign). Non è decorazione: da
        // quando la CI timbra versionCode e patch col numero di run (vedi
        // app/build.gradle.kts), questa riga è l'unico modo, telefono alla
        // mano, di sapere quale build si sta usando — durante il debug di un
        // bug su S22 si è persa mezz'ora proprio perché due APK diverse si
        // dichiaravano identiche.
        Text(
            text = stringResource(
                R.string.settings_version,
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE,
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 28.dp)
        )
    }

    if (showSlowExitPassword) {
        PasswordVerifyDialog(
            passwordManager = passwordManager,
            title = stringResource(R.string.settings_slow_exit_label),
            confirmLabel = stringResource(R.string.confirm),
            onDismiss = { showSlowExitPassword = false },
            onVerified = { showSlowExitChoice = true },
        )
    }
    if (showSlowExitChoice) {
        // null = spenta; altrimenti i minuti di attesa.
        val options = listOf<Int?>(null) + SlowExitManager.WAIT_OPTIONS
        val current: Int? = if (slowExitEnabled) slowExitWait else null
        AlertDialog(
            onDismissRequest = { showSlowExitChoice = false },
            title = { Text(stringResource(R.string.settings_slow_exit_label)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.settings_slow_exit_dialog_body),
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    // Pillole come le durate in Home e le scelte delle pause
                    // programmate: prima era una lista di radio, l'unica
                    // scelta fra pochi valori fatta così nell'app.
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        options.forEach { option ->
                            CalmPill(
                                label = if (option == null) {
                                    stringResource(R.string.settings_slow_exit_off)
                                } else {
                                    stringResource(R.string.settings_slow_exit_pill, option)
                                },
                                selected = option == current,
                                // Piccola, come le scelte dentro gli altri
                                // dialoghi: le quattro stanno su una riga.
                                size = CalmPillSize.Small,
                                onClick = {
                                    slowExitEnabled = option != null
                                    if (option != null) slowExitWait = option
                                    slowExitManager.update(enabled = option != null, waitMinutes = option ?: slowExitWait)
                                    showSlowExitChoice = false
                                },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSlowExitChoice = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    if (showManageAppsDialog) {
        PasswordVerifyDialog(
            passwordManager = passwordManager,
            title = stringResource(R.string.manage_allowed_apps),
            message = stringResource(R.string.manage_allowed_apps_password_prompt),
            confirmLabel = stringResource(R.string.confirm),
            onDismiss = { showManageAppsDialog = false },
            onVerified = onManageAppsVerified,
        )
    }
}

/** Etichetta di sezione uniforme — stesso stile per tutte le card sotto. */
@Composable
private fun SectionLabel(text: String, topPadding: Dp = 32.dp) {
    // Maiuscolo spaziato e tenue (redesign): con sei sezioni l'occhio deve
    // poterle saltare, e un'etichetta della stessa forza del contenuto
    // costringe a leggerle tutte per capire dove si è.
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        letterSpacing = 0.12.em,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topPadding, bottom = 8.dp)
    )
}

/**
 * Icona di riga, dentro la sua pastiglia tonda tinta: lo stesso contenitore
 * delle pastiglie di azione della pausa, così l'app ha una sola forma per
 * "cosa si tocca".
 */
@Composable
internal fun SettingsRowIcon(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
    Spacer(modifier = Modifier.width(12.dp))
}

/**
 * Colori del componente Switch di M3 ristretti agli 8 ruoli personalizzati
 * per palette in CalmOtterTheme.kt (primary/onPrimary/onSurface):
 * SwitchDefaults.colors() di default userebbe surfaceVariant per la track
 * non selezionata, un ruolo NON personalizzato che resta fisso sul
 * viola-grigio di base di Material3 a prescindere dalla palette scelta —
 * la stessa "trappola" già documentata per i mark dell'otter (vedi
 * CLAUDE.md e specs/mascot-marks/design.md).
 */
@Composable
internal fun settingsSwitchColors(): SwitchColors = SwitchDefaults.colors(
    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
    checkedTrackColor = MaterialTheme.colorScheme.primary,
    checkedBorderColor = MaterialTheme.colorScheme.primary,
    uncheckedThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
    uncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
    uncheckedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
)

@Composable
private fun PasswordCard(
    partnerName: String?,
    onChangePassword: () -> Unit,
    onManageApps: () -> Unit,
    slowExitValue: String,
    onSlowExit: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            if (!partnerName.isNullOrEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_password_set_by, partnerName),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            }
            SettingsActionRow(
                label = stringResource(R.string.change_password),
                onClick = onChangePassword,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            SettingsActionRow(
                label = stringResource(R.string.manage_allowed_apps),
                onClick = onManageApps,
                icon = {
                    Icon(
                        imageVector = Icons.Default.List,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            // Uscita lenta (specs/slow-exit/): sta qui perché fa parte del
            // patto della password, e come le altre righe si cambia solo
            // dopo averla inserita.
            SettingsActionRow(
                label = stringResource(R.string.settings_slow_exit_label),
                value = slowExitValue,
                onClick = onSlowExit,
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_hourglass),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
        }
    }
}

/** Riga d'azione interna all'app (non un link esterno, vedi [InfoLinkRow]): stesso layout, freccia semplice invece della freccia diagonale. */
@Composable
internal fun SettingsActionRow(
    label: String,
    onClick: () -> Unit,
    value: String? = null,
    icon: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsRowIcon { icon() }
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            Text(
                text = value,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(end = 10.dp),
            )
        }
        Text(
            text = "→",
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Frasi riflessive durante la pausa: un solo toggle, stessa card delle altre sezioni invece del vecchio Checkbox isolato. */
@Composable
private fun PhrasesCard(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsRowIcon { SprigMark(markSize = 16.dp) }
            Text(
                text = stringResource(R.string.phrases_toggle_label),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = settingsSwitchColors(),
            )
        }
    }
}

/**
 * Link esterni (repo GitHub, licenza, sviluppatore) — l'unica sezione di
 * Settings che lascia l'app. Stessa card neutra a bassa opacità delle altre
 * sezioni.
 */
@Composable
private fun InfoCard(
    onOpenGitHub: () -> Unit,
    onOpenLicense: () -> Unit,
    onOpenDeveloper: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            InfoLinkRow(label = stringResource(R.string.settings_info_github), onClick = onOpenGitHub)
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            InfoLinkRow(label = stringResource(R.string.settings_info_license), onClick = onOpenLicense)
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            InfoLinkRow(label = stringResource(R.string.settings_info_developer), onClick = onOpenDeveloper)
        }
    }
}

@Composable
private fun InfoLinkRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "↗",
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
