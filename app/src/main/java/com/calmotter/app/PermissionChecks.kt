package com.calmotter.app

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Vero se il servizio di accessibilità di Calm Otter è tra quelli abilitati
 * dall'utente in Impostazioni > Accessibilità. Condivisa tra MainActivity
 * (decide se avviare la sessione o spiegare cosa manca), OnboardingActivity
 * (step permessi) e SettingsActivity (riga di stato) — prima triplicata
 * identica in ciascuna, estratta qui per evitare tre copie della stessa
 * logica di detection.
 */
fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expected = ComponentName(context, AppBlockerAccessibilityService::class.java)
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabled.split(":").any { ComponentName.unflattenFromString(it) == expected }
}

/** Vero se Calm Otter ha l'accesso alla policy delle notifiche (Non disturbare). */
fun isDndAccessGranted(context: Context): Boolean =
    (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
        .isNotificationPolicyAccessGranted

/**
 * Permessi runtime richiesti dalla lobby dal vivo di Pausa di gruppo
 * (Fase 2, vedi specs/group-pause/design.md) — solo da Android 12 (API 31)
 * in su: sotto quella versione bastano `BLUETOOTH`/`BLUETOOTH_ADMIN` in
 * AndroidManifest.xml, permessi "normal" concessi all'installazione, nessuna
 * richiesta a runtime necessaria. Condivisa da host e joiner (stesso
 * insieme per entrambi i ruoli, anche se in teoria il joiner non
 * userebbe mai ADVERTISE) per avere una sola lista da mantenere invece di
 * due liste quasi identiche che potrebbero divergere per errore.
 */
fun groupPauseBluetoothRuntimePermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_SCAN,
        )
    } else {
        emptyArray()
    }

fun hasGroupPauseBluetoothPermissions(context: Context): Boolean =
    groupPauseBluetoothRuntimePermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
