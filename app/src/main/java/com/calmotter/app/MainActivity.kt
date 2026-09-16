package com.calmotter.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.calmotter.app.ui.screens.BlockScreen
import com.calmotter.app.ui.screens.MainScreen
import com.calmotter.app.ui.screens.rememberOtterFloatOffset
import com.calmotter.app.ui.theme.CalmOtterTheme
import com.google.android.material.color.MaterialColors

/**
 * Un'unica Activity per due ruoli: icona del launcher (CATEGORY_LAUNCHER) e
 * app Home (CATEGORY_HOME, quando l'utente la imposta come tale da Settings
 * — vedi SettingsActivity.promptSetAsHome()). Prima erano due classi
 * separate (questa e la ormai rimossa HomeActivity), poi unificate; in un
 * secondo momento è stata anche allineata la condizione che decide cosa
 * mostrare: con una sessione attiva, questa Activity mostra sempre
 * `BlockScreen` — non solo quando invocata come Home, ma anche dall'icona
 * del launcher — così le tre schermate di blocco possibili (icona,
 * `BlockOverlayActivity` via AccessibilityService, e questa via Home) sono
 * visivamente e comportamentalmente identiche, su richiesta esplicita:
 * prima, aperta dall'icona durante una sessione, mostrava `MainScreen` in
 * una vista "in pausa" senza alcun modo di sbloccare da lì (serviva
 * premere Home o aprire un'altra app bloccata) — un vero buco, non solo
 * un'incoerenza visiva. Conseguenza accettata: Impostazioni e Cronologia,
 * raggiungibili dall'header di `MainScreen`, non sono più raggiungibili
 * durante una sessione attiva nemmeno dall'icona del launcher — bisogna
 * prima sbloccare.
 *
 * `launchMode="singleTask"` in AndroidManifest.xml (vedi lì) evita che
 * pressioni ripetute del tasto Home impilino istanze duplicate — ogni nuovo
 * Intent su un'istanza già viva arriva a [onNewIntent], non a un nuovo
 * [onCreate]. `themeVariant` resta [ThemeVariant.BASE] sempre: BLOCK e BASE
 * risolvono agli stessi identici stili XML (vedi values/themes.xml, gli
 * alias `.Block` non aggiungono nulla), quindi non serve calcolarlo in modo
 * dinamico in base a come l'Activity è stata invocata.
 */
class MainActivity : BaseActivity() {

    private lateinit var passwordManager: PasswordManager
    private lateinit var sessionManager: SessionManager
    private lateinit var sessionHistoryManager: SessionHistoryManager
    private lateinit var launcherManager: LauncherManager
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

    // true quando c'è una sessione attiva: in quel caso, indipendentemente da
    // come questa istanza è stata invocata (icona o Home), il contenuto
    // mostrato è BlockScreen invece di MainScreen — vedi il commento di
    // classe sul perché questo ora vale per entrambi i punti d'ingresso.
    private var showBlockScreen by mutableStateOf(false)
    private var blockPhraseText by mutableStateOf<String?>(null)

    // true solo quando l'istanza corrente è stata invocata come Home
    // (CATEGORY_HOME): decide cosa fare quando si esce da BlockScreen (vedi
    // onBlockScreenExit()) — inoltrare al launcher originale se si è
    // arrivati qui per il tasto Home (dove non c'è nessun altro launcher
    // raggiungibile), oppure limitarsi a "chiudere" BlockScreen e tornare a
    // MainScreen se si è arrivati qui dall'icona del launcher (dove non ha
    // senso lasciare l'app: l'utente l'ha aperta lei stessa).
    private var forwardOnBlockScreenExit by mutableStateOf(false)

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

    // SharedTransitionLayout/sharedElement sono ancora dietro opt-in
    // (@ExperimentalSharedTransitionApi) in questa versione di Compose:
    // l'annotazione sta qui e non più in alto perché è l'unico punto dell'app
    // che li usa.
    @OptIn(ExperimentalSharedTransitionApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        passwordManager = PasswordManager.getInstance(applicationContext)
        sessionManager = SessionManager.getInstance(applicationContext)
        sessionHistoryManager = SessionHistoryManager.getInstance(applicationContext)
        launcherManager = LauncherManager.getInstance(applicationContext)
        phraseManager = PhraseManager.getInstance(applicationContext)
        launcherManager.refreshOriginalLauncherPackage()
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
        if (isFinishing) return // handleIntent ha già inoltrato al launcher originale e chiuso

        setContent {
            CalmOtterTheme(appTheme = currentTheme) {
                // Home e schermata di pausa disegnano lo stesso otter
                // (OtterFloatMark a 124.dp in entrambe): invece di sostituire
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

                SharedTransitionLayout {
                    AnimatedContent(
                        targetState = showBlockScreen,
                        transitionSpec = {
                            // Uscita più corta dell'entrata, così i due
                            // contenuti non restano sovrapposti a mezza
                            // opacità per mezzo secondo: la Home sfuma via
                            // mentre la pausa è ancora quasi trasparente, e
                            // l'otter condiviso resta l'unica cosa nitida
                            // durante lo scambio — è lui a portare l'occhio.
                            fadeIn(tween(340, delayMillis = 120, easing = FastOutSlowInEasing)) togetherWith
                                fadeOut(tween(220, easing = FastOutSlowInEasing))
                        },
                        label = "homeToBlock",
                    ) { blocking ->
                        // Il boundsTransform vive qui (non nei due Composable)
                        // perché sharedElement va costruito dentro entrambi
                        // gli scope: SharedTransitionLayout per lo stato
                        // condiviso, AnimatedContent per sapere quale dei due
                        // lati sta entrando. Le schermate ricevono solo un
                        // Modifier già pronto, e restano usabili senza (vedi
                        // BlockOverlayActivity, che non ha alcuna transizione
                        // da cui arrivare).
                        val otterModifier = Modifier.sharedElement(
                            rememberSharedContentState(key = "otter"),
                            animatedVisibilityScope = this@AnimatedContent,
                            boundsTransform = { _, _ ->
                                tween(460, easing = FastOutSlowInEasing)
                            },
                        )

                        if (blocking) {
                            BlockScreen(
                                sessionManager = sessionManager,
                                passwordManager = passwordManager,
                                phraseText = blockPhraseText,
                                onExpiredImmediately = { onBlockScreenExit() },
                                onExpiredNaturally = {
                                    Toast.makeText(this@MainActivity, getString(R.string.session_ended), Toast.LENGTH_SHORT).show()
                                    onBlockScreenExit()
                                },
                                onUnlocked = { onBlockScreenExit() },
                                allowedApps = loadAllowedAppLaunchItems(applicationContext),
                                onLaunchApp = { pkg -> launchAllowedApp(applicationContext, pkg) },
                                otterModifier = otterModifier,
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
                                onSettings = { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) },
                                onSessionStarted = { enterBlockScreen() },
                                onGroupPauseHost = { startActivity(Intent(this@MainActivity, GroupPauseHostActivity::class.java)) },
                                onGroupPauseJoin = { startActivity(Intent(this@MainActivity, GroupPauseJoinActivity::class.java)) },
                                otterModifier = otterModifier,
                                otterFloatOffset = otterFloatOffset,
                            )
                        }
                    }
                }
            }
        }
    }

    // launchMode="singleTask" (AndroidManifest.xml): pressioni ripetute del
    // tasto Home riconsegnano l'Intent a un'istanza già viva tramite questo
    // metodo, non tramite un nuovo onCreate() — senza questo override
    // continuerebbe a mostrare il contenuto con cui era stata creata
    // l'ultima volta, ignorando il nuovo Intent. Non serve recreate(): la
    // Composition creata in onCreate() è ancora viva, e handleIntent() già
    // muta showBlockScreen/blockPhraseText (mutableStateOf), che basta a
    // far ricomporre il contenuto giusto.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /**
     * Decide se questa istanza deve mostrare BlockScreen (sessione attiva,
     * a prescindere da come è stata invocata — vedi il commento di classe)
     * invece di MainScreen, oppure se deve semplicemente sparire inoltrando
     * al launcher originale (Home + nessuna sessione).
     *
     * Il forward è condizionato anche a `passwordManager.isPasswordSet()`:
     * caso limite possibile se CalmOtter viene impostata come app Home dalle
     * Impostazioni di sistema prima di essere mai stata aperta una volta —
     * in quel caso questo sarebbe il primissimo onCreate() in assoluto, con
     * onboarding mai completato. Senza questo controllo, il forward
     * chiuderebbe subito l'istanza (vedi onCreate) prima che onResume()
     * abbia mai la possibilità di reindirizzare a OnboardingActivity, e
     * l'utente finirebbe su un altro launcher senza aver mai impostato una
     * password. Quando l'onboarding non è completo si salta semplicemente
     * il forward: il normale flusso di onCreate() prosegue (mostra
     * MainScreen), e la ridirezione già esistente in onResume() se ne
     * occupa comunque, subito dopo.
     */
    private fun handleIntent(intent: Intent?) {
        val cameViaHome = intent?.categories?.contains(Intent.CATEGORY_HOME) == true
        val sessionActive = sessionManager.isSessionActive()

        if (cameViaHome && !sessionActive && passwordManager.isPasswordSet()) {
            forwardToOriginalLauncher()
            return
        }

        forwardOnBlockScreenExit = cameViaHome
        if (sessionActive) enterBlockScreen() else exitBlockScreen()
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

    /**
     * Uscita da BlockScreen (sblocco, scadenza naturale o immediata).
     * Arrivati qui per il tasto Home ([forwardOnBlockScreenExit]), l'unico
     * modo per l'utente di raggiungere un altro launcher è il forward
     * esplicito. Arrivati qui invece dall'icona del launcher, forwardare
     * altrove non avrebbe senso — è l'utente stessa ad aver aperto CalmOtter
     * — quindi ci si limita a tornare a MainScreen nella stessa istanza,
     * senza chiudere nulla.
     */
    private fun onBlockScreenExit() {
        if (forwardOnBlockScreenExit) {
            forwardToOriginalLauncher()
        } else {
            exitBlockScreen()
        }
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
        // sia della palette sia della modalità chiaro/scuro
        // (values-night/themes.xml ha i suoi colorPrimary scuri distinti,
        // non semplici varianti di colors.xml, vedi
        // specs/multi-theme-system/design.md) — a differenza di un colore
        // preso da colors.xml (che non ha varianti -night), che sarebbe
        // risultato sbagliato in dark mode. Nessun effetto sotto Android
        // 15+ (edge-to-edge imposto da targetSdk 37 rende la barra di stato
        // trasparente, vedi CLAUDE.md), ma resta visibile sulle versioni
        // precedenti.
        window.statusBarColor = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorPrimary,
            android.graphics.Color.BLACK,
        )
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

    /**
     * Non deve MAI poter risolvere di nuovo su questa stessa app: se
     * capitasse (valore salvato assente/corrotto, o il pacchetto salvato
     * non più avviabile), un intent Home generico risolverebbe di nuovo su
     * MainActivity stessa — dato che CalmOtter è ancora l'app Home impostata
     * in quel momento — e ogni istanza ripeterebbe lo stesso forward,
     * creando un loop di istanze che si rilanciano a vicenda (bug reale
     * osservato con un valore corrotto in LauncherManager). Per questo ogni
     * pacchetto bersaglio, incluso ciascuno di [findOtherHomePackages], viene
     * sempre passato esplicitamente via `setPackage(...)` — mai un intent
     * Home senza filtro pacchetto.
     *
     * Se il valore salvato non è utilizzabile e la ricerca fresca trova più
     * di un candidato (più launcher installati, nessun default già scelto:
     * [PackageManager.resolveActivity] non può dire in modo affidabile
     * "quale sia quello giusto"), si mostra un chooser invece di sceglierne
     * uno arbitrariamente — vedi [launchHomeChooser].
     */
    private fun forwardToOriginalLauncher() {
        val originalPackage = launcherManager.getOriginalLauncherPackage()
            ?.takeIf { it != packageName && it !in LauncherManager.EXCLUDED_PACKAGES }
        val candidates = if (originalPackage != null) listOf(originalPackage) else findOtherHomePackages()

        when (candidates.size) {
            0 -> { /* nessun candidato utilizzabile: nessun intent, si chiude soltanto */ }
            1 -> launchHomePackage(candidates[0])
            else -> launchHomeChooser(candidates)
        }
        finish()
    }

    private fun launchHomePackage(pkg: String) {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            setPackage(pkg)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            // Il pacchetto risolto un attimo fa non è più avviabile
            // (disinstallato, disabilitato) — nessun secondo tentativo
            // con un intent senza filtro pacchetto: si rischierebbe di
            // tornare su questa stessa app. Ci si ferma qui.
        }
    }

    /**
     * Chooser limitato esplicitamente ai [candidates] già filtrati, tramite
     * [Intent.EXTRA_INITIAL_INTENTS] — MAI `Intent.createChooser(intent
     * Home generico, ...)`: un intent Home generico come bersaglio primario
     * verrebbe ri-risolto da zero dal sistema, e CalmOtter (che dichiara
     * anch'essa CATEGORY_HOME) ricomparirebbe come opzione nel proprio
     * stesso chooser. L'intent bersaglio passato a createChooser qui è
     * volutamente "vuoto" (nessuna action): non risolve nulla di suo, quindi
     * le uniche opzioni mostrate sono i candidati elencati esplicitamente.
     */
    private fun launchHomeChooser(candidates: List<String>) {
        val initialIntents = candidates.map { pkg ->
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                setPackage(pkg)
            }
        }.toTypedArray()

        val chooser = Intent.createChooser(Intent(), getString(R.string.home_launcher_chooser_title)).apply {
            putExtra(Intent.EXTRA_INITIAL_INTENTS, initialIntents)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            startActivity(chooser)
        } catch (e: Exception) {
            // Nessuna azione: si preferisce non lanciare nulla piuttosto
            // che rischiare un fallback senza filtro pacchetto.
        }
    }

    /**
     * Ricerca "fresca" (non fidata dal valore salvato in LauncherManager,
     * che potrebbe essere assente o corrotto) di TUTTI gli altri pacchetti
     * in grado di gestire l'Home — sempre escludendo sé stessa e i fallback
     * di sistema noti (vedi LauncherManager.EXCLUDED_PACKAGES). L'elenco
     * completo (non solo il primo trovato) serve a [forwardToOriginalLauncher]
     * per decidere se lanciare direttamente l'unico candidato o mostrare un
     * chooser quando ce n'è più di uno, invece di sceglierne uno a caso in
     * base al solo ordine di enumerazione — che non riflette affatto quale
     * sia "quello giusto".
     */
    private fun findOtherHomePackages(): List<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName }
            .filter { it != packageName && it !in LauncherManager.EXCLUDED_PACKAGES }
            .distinct()
    }
}
