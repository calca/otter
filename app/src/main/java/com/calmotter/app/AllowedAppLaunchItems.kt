package com.calmotter.app

import android.content.Context
import android.telecom.TelecomManager
import com.calmotter.app.ui.screens.AllowedAppLaunchItem

/**
 * Condiviso fra tutti i punti che mostrano [com.calmotter.app.ui.screens.BlockScreen]
 * con la riga delle app consentite (MainActivity, BlockOverlayActivity —
 * vedi entrambe): prima era solo in MainActivity, duplicato qui perché ora
 * tutte e tre le schermate di blocco (icona del launcher, tasto Home,
 * overlay dell'AccessibilityService) sono identiche, riga delle app
 * consentite inclusa — vedi app-blocking-and-home-lock/design.md.
 *
 * Il telefono (dialer di default) è sempre incluso per primo, a parte dal
 * tetto di [AllowedAppsManager.MAX_ALLOWED_APPS] app configurabili — è
 * sempre stato implicitamente consentito lato
 * AppBlockerAccessibilityService, ma senza questa lista non compariva mai in
 * un punto da cui poterlo effettivamente *avviare* se non già aperto. Il
 * resto risolve solo i pacchetti già in whitelist (non l'intero elenco app
 * installate come fa AllowedAppsActivity) — pochi elementi, quindi va bene
 * farlo in modo sincrono sul thread main invece di un dispatch IO.
 * `.take(MAX_ALLOWED_APPS)` è una rete di sicurezza (il tetto vero è imposto
 * all'aggiunta in AllowedAppsActivity.toggleApp, questo non dovrebbe mai
 * tagliare nulla in pratica). Un pacchetto disinstallato dopo essere stato
 * reso consentito viene scartato silenziosamente (getApplicationInfo lancia).
 */
fun loadAllowedAppLaunchItems(context: Context): List<AllowedAppLaunchItem> {
    val phoneItem = resolveAppLaunchItem(context, dialerPackageName(context))
    val allowed = AllowedAppsManager.getInstance(context).getAllowedPackages()
    val allowedItems = allowed
        .mapNotNull { resolveAppLaunchItem(context, it) }
        .sortedBy { it.label.lowercase() }
        .take(AllowedAppsManager.MAX_ALLOWED_APPS)

    return listOfNotNull(phoneItem) + allowedItems.filter { it.packageName != phoneItem?.packageName }
}

private fun dialerPackageName(context: Context): String? =
    (context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager)?.defaultDialerPackage

private fun resolveAppLaunchItem(context: Context, pkg: String?): AllowedAppLaunchItem? {
    if (pkg == null) return null
    return try {
        val label = context.packageManager.getApplicationInfo(pkg, 0).loadLabel(context.packageManager).toString()
        AllowedAppLaunchItem(label = label, packageName = pkg)
    } catch (e: Exception) {
        null
    }
}

/** Avvia un'app consentita dal badge nella riga di [com.calmotter.app.ui.screens.BlockScreen]. */
fun launchAllowedApp(context: Context, packageName: String) {
    val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        // Pacchetto diventato non avviabile (disinstallato, disabilitato)
        // tra il caricamento della lista e il tap — nessuna azione, resta
        // sulla schermata di blocco.
    }
}
