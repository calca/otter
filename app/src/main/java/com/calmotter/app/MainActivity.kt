package com.calmotter.app

import android.widget.Toast
import android.nfc.NfcAdapter
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.calmotter.app.ui.screens.BlockScreen
import com.calmotter.app.ui.screens.HistoryTab
import com.calmotter.app.ui.screens.MainScreen
import com.calmotter.app.ui.screens.rememberOtterFloatOffset
import com.calmotter.app.ui.theme.CalmOtterTheme
import com.google.android.material.color.MaterialColors
import com.calmotter.app.ui.screens.otterCenterY
import com.calmotter.app.ui.screens.OtterHaloSize
import com.calmotter.app.ui.screens.PersistentOtter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box

/**
 * La Home dell'app (icona del launcher). Con una sessione attiva mostra
 * sempre `BlockScreen` invece di `MainScreen`, così le schermate di blocco
 * possibili (questa e `BlockOverlayActivity` via AccessibilityService) sono
 * identiche: aperta dall'icona durante una pausa, prima mostrava una
 * `MainScreen` "in pausa" senza modo di sbloccare. Conseguenza accettata:
 * Impostazioni e Cronologia non sono raggiungibili durante una pausa,
 * bisogna prima sbloccare.
 *
 * Fino al 2026-10-16 era anche l'app Home del telefono (CATEGORY_HOME,
 * impostabile da Impostazioni), con l'inoltro al launcher vero fuori dalle
 * pause. Tolta: il tasto Home durante una pausa lo copre già
 * l'AccessibilityService (il launcher è un'app come le altre), e far
 * passare da qui ogni pressione di Home del telefono costava più di quanto
 * rendesse. Vedi specs/app-blocking-and-home-lock/.
 *
 * `launchMode="singleTask"` in AndroidManifest.xml: un nuovo Intent su
 * un'istanza già viva arriva a [onNewIntent], non a un nuovo [onCreate].
 */
class MainActivity : BaseActivity() {

    private lateinit var passwordManager: PasswordManager
    private lateinit var sessionManager: SessionManager
    private lateinit var sessionHistoryManager: SessionHistoryManager
    private lateinit var phraseManager: PhraseManager

    // Incrementato a ogni onResume(): passato come parametro a MainScreen così
    // che il suo LaunchedEffect(resumeSignal) ricalcoli lo stato che dipende
    // dal sistema operativo (accessibilità, DND, sessione attiva) —
    // setContent {} viene chiamato una sola volta in onCreate, quindi Compose
    // non ha altrimenti modo di accorgersi di questi cambi di stato esterni.
    private var resumeSignal by mutableIntStateOf(0)

    // Letto una sola volta da ThemeManager in onCreate() non basterebbe:
    // MainActivity non viene mai ricreata al ritorno da Settings (a
    // differenza di SettingsActivity, che chiama recreate() su se stessa
    // dopo un cambio tema), quindi senza questo stato la Home continuerebbe
    // a mostrare la palette vecchia finché il processo non viene killato.
    // Aggiornato in onResume() insieme a resumeSignal: essendo letto qui,
    // proprio nel punto in cui CalmOtterTheme lo usa, il suo cambiamento
    // ricompone subito l'intero albero con lo schema colore aggiornato.
    private var currentTheme by mutableStateOf(AppTheme.SAGE)

    // true quando c'è una sessione attiva: il contenuto mostrato è
    // BlockScreen invece di MainScreen — vedi il commento di classe.
    private var showBlockScreen by mutableStateOf(false)

    // La durata scelta in Home, in minuti: l'ultima usata (vedi
    // [LastDuration]). Campo dell'Activity e non stato locale della
    // composizione, perché va riletta a ogni rientro: può essere cambiata
    // altrove nel frattempo (una pausa di gruppo con un'altra durata).
    private var selectedDurationMinutes by mutableIntStateOf(DEFAULT_SESSION_DURATION_MINUTES)
    private var blockPhraseText by mutableStateOf<String?>(null)

    // Blocca il tasto back mentre è mostrato BlockScreen (stesso
    // comportamento di BlockOverlayActivity, per restare coerenti) — non
    // quando è mostrato MainScreen, dove il back deve continuare a
    // funzionare normalmente. isEnabled viene tenuto sincronizzato con
    // showBlockScreen ovunque quest'ultimo cambi.
    private val blockBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            // Intenzionalmente vuoto per bloccare il tasto back
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* refreshUi non serve: il permesso non cambia il layout */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ricreata: vale lo stato salvato, non l'Intent, che chiederebbe di
        // nuovo il dialogo anche dopo che è stato chiuso.
        showPermissionDialog = savedInstanceState?.getBoolean(STATE_PERMISSION_DIALOG)
            ?: intent.getBooleanExtra(EXTRA_SHOW_PERMISSIONS, false)

        passwordManager = PasswordManager.getInstance(applicationContext)
        sessionManager = SessionManager.getInstance(applicationContext)
        sessionHistoryManager = SessionHistoryManager.getInstance(applicationContext)
        phraseManager = PhraseManager.getInstance(applicationContext)
        currentTheme = ThemeManager.getTheme(this)
        onBackPressedDispatcher.addCallback(this, blockBackPressedCallback)

        // Su Android 13+ chiediamo il permesso per le notifiche al primo avvio
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        handleIntent(intent)

        setContent {
            CalmOtterTheme(appTheme = currentTheme) {
                // Home e schermata di pausa disegnano lo stesso otter
                // (OtterZenMark a 124.dp in entrambe): invece di sostituire
                // un albero con l'altro in un frame solo — che faceva
                // "teletrasportare" l'otter da centrato verticalmente a in
                // cima alla colonna — l'otter è dichiarato elemento condiviso
                // e scivola alla sua nuova posizione, mentre tutto il resto
                // (increspature e chip di durata da una parte, anello di
                // avanzamento e frase dall'altra) si dissolve attorno.
                // Il galleggiamento dell'otter vive *fuori* da AnimatedContent
                // e viene passato a entrambe le schermate. Prima ognuna
                // avviava la propria, per giunta con periodi diversi (3200 in
                // Home, 5200 in pausa): due oscillatori indipendenti, quindi
                // allo scambio quello entrante partiva dal proprio valore
                // iniziale mentre l'uscente si trovava a una fase qualsiasi.
                // La differenza può arrivare all'intera ampiezza, 10dp.
                //
                // Non si vede come uno scatto — l'elemento condiviso quella
                // differenza la *anima*, lungo il suo boundsTransform — ma è
                // esattamente questo che la fa leggere come un otter che
                // scivola, ed è ciò che era stato segnalato. Allineare le due
                // posizioni di layout (vedi OtterSlotHeight) toglie la causa
                // principale; questa istanza unica toglie quella residua, che
                // altrimenti resterebbe a sorte a seconda della fase.
                //
                // Conseguenza accettata: sparisce il periodo più lento durante
                // la pausa, perché cambiarlo a metà corsa riavvierebbe
                // l'animazione, reintroducendo il salto che qui si toglie.
                val otterFloatOffset = rememberOtterFloatOffset(periodMillis = 3200)

                // **La dissolvenza riguarda tutto tranne l'otter.** Le due
                // schermate si scambiano sotto; l'otter sta sopra, ed è un
                // nodo solo che non esce mai di scena — vedi
                // [PersistentOtter] per perché non è più un elemento
                // condiviso.

                // Condivisa fra il tap sull'otter e il fallback "senza
                // permessi" del bottone Tempo Insieme (vedi il commento su
                // MainScreen.kt:onGroupPause) — prima esisteva solo dentro
                // PersistentOtter.onStart, e MainScreen.onOtterTap non
                // veniva passato affatto da qui: restava il default `{}` a
                // vuoto, quindi su un'installazione pulita senza permessi
                // toccare "Tempo insieme" non faceva letteralmente nulla.
                val startOrPromptPermissions: () -> Unit = {
                    val ok = BuildConfig.DEBUG || (
                        isAccessibilityServiceEnabled(this@MainActivity) &&
                            isDndAccessGranted(this@MainActivity)
                        )
                    if (ok) {
                        sessionManager.startSession(selectedDurationMinutes)
                        enterBlockScreen()
                    } else {
                        showPermissionDialog = true
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    Crossfade(
                        targetState = showBlockScreen,
                        // Come prima l'uscita era più corta dell'entrata, per
                        // non lasciare i due contenuti sovrapposti a mezza
                        // opacità. Una `Crossfade` ha una durata sola, quindi
                        // il compromesso è breve: l'otter resta comunque
                        // l'unica cosa nitida durante lo scambio, ed è lui a
                        // portare l'occhio.
                        animationSpec = tween(320, easing = FastOutSlowInEasing),
                        label = "homeToBlock",
                    ) { blocking ->
                        if (blocking) {
                            BlockScreen(
                                sessionManager = sessionManager,
                                passwordManager = passwordManager,
                                phraseText = blockPhraseText,
                                onExpiredImmediately = { onBlockScreenExit() },
                                onExpiredNaturally = {
                                    onBlockScreenExit()
                                    // Pausa arrivata in fondo: "com'è andata?"
                                    // (specs/closing-moment/), sopra la Home.
                                    ClosingMomentActivity.startIfPending(this@MainActivity)
                                },
                                onUnlocked = { onBlockScreenExit() },
                                allowedApps = loadAllowedAppLaunchItems(applicationContext),
                                onLaunchApp = { pkg -> launchAllowedApp(applicationContext, pkg) },
                                drawOtter = false,
                                otterFloatOffset = otterFloatOffset,
                            )
                        } else {
                            MainScreen(
                                resumeSignal = resumeSignal,
                                sessionManager = sessionManager,
                                sessionHistoryManager = sessionHistoryManager,
                                isAccessibilityServiceEnabled = { isAccessibilityServiceEnabled(this@MainActivity) },
                                isDndAccessGranted = { isDndAccessGranted(this@MainActivity) },
                                onGrantAccessibility = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                                onGrantDnd = { startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) },
                                onHistory = { startActivity(Intent(this@MainActivity, HistoryActivity::class.java)) },
                                onHistorySessions = { startActivity(HistoryActivity.intent(this@MainActivity, HistoryTab.Sessions)) },
                                onSettings = { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) },
                                onQuickTogether = if (NfcAdapter.getDefaultAdapter(this@MainActivity) == null) null else { durationMinutes ->
                                    // NFC spento: le impostazioni dell'NFC, con due parole sul perché.
                                    if (NfcAdapter.getDefaultAdapter(this@MainActivity)?.isEnabled == true) {
                                        startActivity(QuickTogetherActivity.readerIntent(this@MainActivity, durationMinutes))
                                    } else {
                                        Toast.makeText(this@MainActivity, R.string.quick_together_nfc_off, Toast.LENGTH_LONG).show()
                                        runCatching { startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }
                                    }
                                },
                                onGroupPause = { durationMinutes ->
                                    startActivity(
                                        Intent(this@MainActivity, GroupPauseChooserActivity::class.java)
                                            .putExtra(GroupPauseChooserActivity.EXTRA_DURATION_MINUTES, durationMinutes)
                                    )
                                },
                                drawOtter = false,
                                otterFloatOffset = otterFloatOffset,
                                selectedDurationMinutes = selectedDurationMinutes,
                                onSelectDuration = {
                                    selectedDurationMinutes = it
                                    LastDuration.save(this@MainActivity, it)
                                },
                                showPermissionDialog = showPermissionDialog,
                                onDismissPermissionDialog = { showPermissionDialog = false },
                                onOtterTap = startOrPromptPermissions,
                            )
                        }
                    }

                    // L'otter, sopra la dissolvenza. Posizionato con la
                    // stessa [otterCenterY] con cui [OtterAnchoredScreen]
                    // allinea lo slot vuoto che le due schermate gli
                    // riservano: una formula sola, due chiamanti, nessun
                    // numero da tenere allineato a mano.
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        val centerY = otterCenterY(maxHeight)
                        PersistentOtter(
                            floatOffset = otterFloatOffset,
                            enabled = !showBlockScreen,
                            // In debug (incluso quello prodotto in CI) i
                            // permessi Accessibilità/DND non bloccano
                            // l'avvio, per poter provare il resto del
                            // flusso senza concederli davvero a ogni
                            // installazione pulita — vedi
                            // SessionManager.setPauseDnd(), che già ignora
                            // il DND se non concesso. In release il
                            // controllo resta invariato. Vedi
                            // startOrPromptPermissions qui sopra: stessa
                            // lambda usata dal fallback del bottone Tempo
                            // Insieme.
                            onStart = startOrPromptPermissions,
                            modifier = Modifier.padding(top = centerY - OtterHaloSize / 2),
                        )
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_PERMISSION_DIALOG, showPermissionDialog)
    }

    // launchMode="singleTask" (AndroidManifest.xml): un nuovo Intent (il
    // widget, una notifica) su un'istanza già viva arriva qui, non a un
    // nuovo onCreate(). handleIntent() muta lo stato Compose, che basta a
    // ricomporre il contenuto giusto.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
        if (intent.getBooleanExtra(EXTRA_SHOW_PERMISSIONS, false)) showPermissionDialog = true
    }

    /**
     * La spiegazione dei permessi mancanti (stessa del tocco sull'otter).
     * Un campo dell'Activity e non uno stato Compose locale, così la può
     * aprire anche chi arriva da fuori: il widget, se i permessi mancano.
     */
    private var showPermissionDialog by mutableStateOf(false)

    /**
     * Con una sessione attiva questa Activity mostra BlockScreen invece di
     * MainScreen, comunque sia stata aperta (icona, widget, notifica).
     */
    private fun handleIntent(@Suppress("UNUSED_PARAMETER") intent: Intent?) {
        if (sessionManager.isSessionActive()) enterBlockScreen() else exitBlockScreen()
    }

    /**
     * Mostra BlockScreen: usata sia da [handleIntent] (sessione già attiva
     * all'ingresso) sia da [MainScreen]'s `onSessionStarted` (sessione
     * avviata dall'utente mentre questa stessa istanza era già in primo
     * piano su MainScreen, es. toccando l'otter) — nel secondo caso non
     * arriva nessun nuovo Intent/onNewIntent, quindi serve un modo per
     * reagire al cambio di stato dall'interno della Composition stessa.
     */
    private fun enterBlockScreen() {
        showBlockScreen = true
        blockBackPressedCallback.isEnabled = true
        val phrase = phraseManager.randomPhrase()
        blockPhraseText = phrase?.let { getString(R.string.phrase_format, it) }
    }

    private fun exitBlockScreen() {
        showBlockScreen = false
        blockBackPressedCallback.isEnabled = false
    }

    /** Uscita da BlockScreen (sblocco, scadenza naturale o immediata): si torna a MainScreen. */
    private fun onBlockScreenExit() {
        exitBlockScreen()
    }

    override fun onResume() {
        super.onResume()

        // Se la password non è ancora impostata, l'onboarding non è stato
        // completato: reindirizza. Non dovrebbe mai succedere in uso normale
        // (OnboardingActivity fa il redirect qui solo dopo il completamento),
        // ma copre il caso in cui l'utente torni indietro o reinstalli.
        if (!passwordManager.isPasswordSet()) {
            startActivity(Intent(this, OnboardingActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            return
        }

        // Rete di sicurezza a ogni apertura (idempotente): allarmi riarmati se
        // il sistema li ha persi, e il servizio in primo piano di una pausa
        // partita da un allarme che non ha potuto avviarlo.
        AppScheduler.reconcile(this)

        currentTheme = ThemeManager.getTheme(this)
        // Il colore di sistema della barra di stato (impostato dal tema XML
        // una sola volta, quando super.onCreate() crea la finestra) non
        // segue currentTheme da solo come fa Compose: va riapplicato qui a
        // mano. setTheme() (dentro applyTheme) va richiamato per prima cosa:
        // il tema risolto dell'Activity resta quello di onCreate() finché
        // non viene detto altrimenti, quindi senza questa chiamata
        // MaterialColors.getColor() sotto continuerebbe a leggere
        // "colorPrimary" della palette precedente anche dopo un cambio.
        ThemeManager.applyTheme(this, themeVariant)
        // MaterialColors.getColor legge l'attributo "colorPrimary" già
        // risolto dal tema (appena riapplicato sopra) — tiene conto da solo
        // sia della palette sia della modalità chiaro/scuro (l'overlay della
        // palette punta a colors.xml, che ha il suo gemello values-night/).
        // Nessun effetto sotto Android
        // 15+ (edge-to-edge imposto da targetSdk 37 rende la barra di stato
        // trasparente, vedi CLAUDE.md), ma resta visibile sulle versioni
        // precedenti.
        // TODO.md "6.2": com.google.android.material.R.attr.colorPrimary
        // ha smesso di risolvere con material:material aggiornato a
        // 1.14.0 — Material non ridichiara più questo attr come proprio
        // duplicato (esisteva solo come compatibilità pre-Lollipop).
        // android.R.attr.colorPrimary è l'attributo di piattaforma vero,
        // presente dall'API 21: con minSdk 26 di questo progetto è sempre
        // stato quello davvero risolto a runtime comunque.
        window.statusBarColor = MaterialColors.getColor(
            this,
            android.R.attr.colorPrimary,
            android.graphics.Color.BLACK,
        )
        selectedDurationMinutes = LastDuration.get(this)
        resumeSignal++

        // Pausa di gruppo (GroupPauseHostActivity/GroupPauseJoinActivity,
        // vedi specs/group-pause/): quella schermata avvia la sessione poi
        // chiama finish() su se stessa, tornando qui — un ritorno da
        // un'altra Activity, non un nuovo Intent, quindi handleIntent() (che
        // gira solo da onCreate()/onNewIntent()) non se ne accorgerebbe da
        // solo. Il tap sull'otter in MainScreen resta gestito com'era
        // (onSessionStarted chiama enterBlockScreen() nella stessa
        // Composition, prima ancora che onResume() rientri in gioco) — questo
        // controllo serve solo per il caso "sessione avviata altrove".
        if (sessionManager.isSessionActive() && !showBlockScreen) {
            enterBlockScreen()
        }
    }

    companion object {
        /** Dal widget, con i permessi mancanti: apre la Home sulla spiegazione. */
        const val EXTRA_SHOW_PERMISSIONS = "com.calmotter.app.extra.SHOW_PERMISSIONS"
        private const val STATE_PERMISSION_DIALOG = "permission_dialog"
    }
}
