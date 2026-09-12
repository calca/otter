package com.calmotter.app

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.provider.Settings

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
