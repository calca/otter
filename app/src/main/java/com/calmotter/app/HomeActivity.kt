package com.calmotter.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.telecom.TelecomManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.core.graphics.drawable.toBitmap
import com.calmotter.app.ui.screens.AllowedAppLaunchItem
import com.calmotter.app.ui.screens.BlockScreen
import com.calmotter.app.ui.theme.CalmOtterTheme

class HomeActivity : BaseActivity() {
    override val themeVariant = ThemeVariant.BLOCK

    private lateinit var launcherManager: LauncherManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Intenzionalmente vuoto per bloccare il tasto back
            }
        })

        val sessionManager = SessionManager.getInstance(applicationContext)
        val passwordManager = PasswordManager.getInstance(applicationContext)
        launcherManager = LauncherManager.getInstance(applicationContext)
        val phraseManager = PhraseManager.getInstance(applicationContext)

        if (!sessionManager.isSessionActive()) {
            forwardToOriginalLauncher()
            return
        }

        val phrase = phraseManager.randomPhrase()
        val phraseText = phrase?.let { "“$it”" }

        setContent {
            CalmOtterTheme(appTheme = ThemeManager.getTheme(this)) {
                BlockScreen(
                    sessionManager = sessionManager,
                    passwordManager = passwordManager,
                    phraseText = phraseText,
                    onExpiredImmediately = { forwardToOriginalLauncher() },
                    onExpiredNaturally = { forwardToOriginalLauncher() },
                    onUnlocked = { forwardToOriginalLauncher() },
                    // Solo qui (Calm Otter come app Home): se non lo è, il
                    // launcher originale resta comunque raggiungibile, vedi
                    // AppBlockerAccessibilityService/HomeActivity — qui invece
                    // premere Home durante una sessione non porta più a nessun
                    // launcher, quindi è l'unico punto senza questa lista in
                    // cui un'app consentita non sarebbe altrimenti avviabile.
                    allowedApps = loadAllowedAppLaunchItems(),
                    onLaunchApp = ::launchAllowedApp,
                )
            }
        }
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
            val info = packageManager.getApplicationInfo(pkg, 0)
            AllowedAppLaunchItem(
                label = info.loadLabel(packageManager).toString(),
                packageName = pkg,
                icon = info.loadIcon(packageManager).toBitmap(),
            )
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

    private fun forwardToOriginalLauncher() {
        val originalPackage = launcherManager.getOriginalLauncherPackage()
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            if (originalPackage != null) setPackage(originalPackage)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        finish()
    }
}
