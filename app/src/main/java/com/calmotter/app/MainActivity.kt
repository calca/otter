package com.calmotter.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telecom.TelecomManager
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.calmotter.app.ui.screens.AllowedAppLaunchItem
import com.calmotter.app.ui.screens.BlockScreen
import com.calmotter.app.ui.screens.MainScreen
import com.calmotter.app.ui.theme.CalmOtterTheme
import com.google.android.material.color.MaterialColors

/**
 * Un'unica Activity per due ruoli: icona del launcher (CATEGORY_LAUNCHER,
 * comportamento invariato — mostra sempre MainScreen, la Living Pond) e app
 * Home (CATEGORY_HOME, quando l'utente la imposta come tale da Settings —
 * vedi SettingsActivity.promptSetAsHome()). Prima erano due classi separate
 * (questa e la ormai rimossa HomeActivity): unificate su richiesta esplicita
 * — comportamento identico a prima, non un redesign — perché avere due
 * Activity per quello che concettualmente è un solo "schermo Home" era
 * percepito come inutilmente complicato.
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

    // true quando questa istanza è stata invocata come app Home (pressione
    // del tasto Home, CATEGORY_HOME) CON una sessione attiva: in quel caso,
    // e solo in quel caso, il contenuto mostrato è BlockScreen invece di
    // MainScreen. Aperta dall'icona del launcher, anche a sessione attiva,
    // mostra sempre MainScreen (nella sua vista "in pausa") — comportamento
    // invariato rispetto a prima della fusione delle due Activity.
    private var showBlockForHome by mutableStateOf(false)
    private var blockPhraseText by mutableStateOf<String?>(null)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* refreshUi non serve: il permesso non cambia il layout */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        passwordManager = PasswordManager.getInstance(applicationContext)
        sessionManager = SessionManager.getInstance(applicationContext)
        sessionHistoryManager = SessionHistoryManager.getInstance(applicationContext)
        launcherManager = LauncherManager.getInstance(applicationContext)
        phraseManager = PhraseManager.getInstance(applicationContext)
        launcherManager.refreshOriginalLauncherPackage()
        currentTheme = ThemeManager.getTheme(this)

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
                if (showBlockForHome) {
                    BlockScreen(
                        sessionManager = sessionManager,
                        passwordManager = passwordManager,
                        phraseText = blockPhraseText,
                        onExpiredImmediately = { forwardToOriginalLauncher() },
                        onExpiredNaturally = { forwardToOriginalLauncher() },
                        onUnlocked = { forwardToOriginalLauncher() },
                        // Solo quando invocata come Home: se non lo è, il
                        // launcher originale resta comunque raggiungibile,
                        // vedi AppBlockerAccessibilityService — qui invece
                        // premere Home durante una sessione non porta più a
                        // nessun launcher, quindi è l'unico caso senza questa
                        // lista in cui un'app consentita non sarebbe
                        // altrimenti avviabile.
                        allowedApps = loadAllowedAppLaunchItems(),
                        onLaunchApp = ::launchAllowedApp,
                    )
                } else {
                    MainScreen(
                        resumeSignal = resumeSignal,
                        sessionManager = sessionManager,
                        sessionHistoryManager = sessionHistoryManager,
                        isAccessibilityServiceEnabled = { isAccessibilityServiceEnabled(this) },
                        isDndAccessGranted = { isDndAccessGranted(this) },
                        onGrantAccessibility = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                        onGrantDnd = { startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) },
                        onHistory = { startActivity(Intent(this, HistoryActivity::class.java)) },
                        onSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                    )
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
    // muta showBlockForHome/blockPhraseText (mutableStateOf), che basta a
    // far ricomporre il contenuto giusto.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /**
     * Decide se questa istanza deve mostrare BlockScreen (Home + sessione
     * attiva) invece di MainScreen, oppure se deve semplicemente sparire
     * inoltrando al launcher originale (Home + nessuna sessione) — l'unico
     * comportamento che HomeActivity aveva e MainActivity no.
     */
    private fun handleIntent(intent: Intent?) {
        val cameViaHome = intent?.categories?.contains(Intent.CATEGORY_HOME) == true
        if (cameViaHome && !sessionManager.isSessionActive()) {
            forwardToOriginalLauncher()
            return
        }
        showBlockForHome = cameViaHome && sessionManager.isSessionActive()
        if (showBlockForHome) {
            val phrase = phraseManager.randomPhrase()
            blockPhraseText = phrase?.let { "“$it”" }
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
    }

    /**
     * Il telefono (dialer di default) è sempre incluso per primo, a parte
     * dal tetto di [AllowedAppsManager.MAX_ALLOWED_APPS] app configurabili —
     * è sempre stato implicitamente consentito lato
     * AppBlockerAccessibilityService, ma prima di questa lista non compariva
     * mai in un punto da cui poterlo effettivamente *avviare* se non già
     * aperto. Il resto risolve solo i pacchetti già in whitelist (non
     * l'intero elenco app installate come fa AllowedAppsActivity) — pochi
     * elementi, quindi va bene farlo in modo sincrono sul thread main invece
     * di un dispatch IO. `.take(MAX_ALLOWED_APPS)` è una rete di sicurezza
     * (il tetto vero è imposto all'aggiunta in AllowedAppsActivity.toggleApp,
     * questo non dovrebbe mai tagliare nulla in pratica). Un pacchetto
     * disinstallato dopo essere stato reso consentito viene scartato
     * silenziosamente (getApplicationInfo lancia).
     */
    private fun loadAllowedAppLaunchItems(): List<AllowedAppLaunchItem> {
        val phoneItem = resolveAppLaunchItem(dialerPackageName())
        val allowed = AllowedAppsManager.getInstance(applicationContext).getAllowedPackages()
        val allowedItems = allowed
            .mapNotNull { resolveAppLaunchItem(it) }
            .sortedBy { it.label.lowercase() }
            .take(AllowedAppsManager.MAX_ALLOWED_APPS)

        return listOfNotNull(phoneItem) + allowedItems.filter { it.packageName != phoneItem?.packageName }
    }

    private fun dialerPackageName(): String? =
        (getSystemService(Context.TELECOM_SERVICE) as? TelecomManager)?.defaultDialerPackage

    private fun resolveAppLaunchItem(pkg: String?): AllowedAppLaunchItem? {
        if (pkg == null) return null
        return try {
            val label = packageManager.getApplicationInfo(pkg, 0).loadLabel(packageManager).toString()
            AllowedAppLaunchItem(label = label, packageName = pkg)
        } catch (e: Exception) {
            null
        }
    }

    private fun launchAllowedApp(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return
        try {
            startActivity(intent)
        } catch (e: Exception) {
            // Pacchetto diventato non avviabile (disinstallato, disabilitato)
            // tra il caricamento della lista e il tap — nessuna azione, resta
            // sulla schermata di blocco.
        }
    }

    /**
     * Non deve MAI poter risolvere di nuovo su questa stessa app: se
     * capitasse (valore salvato assente/corrotto, o il pacchetto salvato
     * non più avviabile), il ramo di fallback lancerebbe un intent Home
     * generico che — dato che CalmOtter è ancora l'app Home impostata in
     * quel momento — tornerebbe a risolvere su MainActivity stessa, e ogni
     * istanza ripeterebbe lo stesso forward, creando un loop di istanze che
     * si rilanciano a vicenda (bug reale osservato con un valore corrotto
     * in LauncherManager). Per questo ogni pacchetto bersaglio, incluso
     * quello di [findAnyOtherHomePackage], viene sempre passato esplicitamente
     * via `setPackage(...)` — mai un intent Home senza filtro pacchetto.
     */
    private fun forwardToOriginalLauncher() {
        val originalPackage = launcherManager.getOriginalLauncherPackage()
            ?.takeIf { it != packageName && it !in LauncherManager.EXCLUDED_PACKAGES }
        val targetPackage = originalPackage ?: findAnyOtherHomePackage()

        if (targetPackage != null) {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                setPackage(targetPackage)
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
        finish()
    }

    /**
     * Ricerca "fresca" (non fidata dal valore salvato in LauncherManager,
     * che potrebbe essere assente o corrotto) di un altro pacchetto in
     * grado di gestire l'Home — sempre escludendo sé stessa e i fallback
     * di sistema noti (vedi LauncherManager.EXCLUDED_PACKAGES). Se non
     * trova nulla, restituisce null: meglio non lanciare nessun intent
     * piuttosto che rischiare un intent Home senza filtro pacchetto.
     */
    private fun findAnyOtherHomePackage(): String? {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName }
            .firstOrNull { it != packageName && it !in LauncherManager.EXCLUDED_PACKAGES }
    }
}
