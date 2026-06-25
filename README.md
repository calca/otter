# Calm Otter

App Android nativa (Kotlin) per aiutare a usare meno il telefono: quando l'utente
avvia una "sessione di pausa", tutte le app vengono bloccate tranne il telefono
(chiamate), e le notifiche vengono silenziate (tranne le chiamate). La sessione
può terminare SOLO inserendo una password impostata in precedenza da un'altra
persona ("accountability partner").

## Come funziona

1. **Primo avvio**: l'altra persona apre l'app e imposta la password (salvata
   come hash PBKDF2-HMAC-SHA256 con salt casuale, dentro `EncryptedSharedPreferences`
   cifrato via Android Keystore — la password in chiaro non viene mai salvata).
2. **Concessione permessi** (una tantum): Accessibilità + Accesso alla politica
   di notifica (Non disturbare).
3. **Inizio pausa**: l'utente sceglie la durata (passi di 30 minuti, da 30 min
   a 4 ore) e tocca "Inizia pausa". Da quel momento:
   - `AppBlockerAccessibilityService` rileva ogni cambio di app in primo piano
     e, se non è l'app Telefono di default (o questa stessa app), rilancia la
     schermata di blocco a tutto schermo, con countdown del tempo rimanente.
   - Viene attivata la modalità Non disturbare con solo le chiamate consentite.
   - Viene programmato un allarme di sistema (`AlarmManager`) che termina la
     sessione automaticamente allo scadere del tempo, anche se l'utente non
     sta usando il telefono in quel momento.
4. **Fine pausa**: la sessione termina automaticamente allo scadere del tempo
   scelto, oppure prima se viene inserita la password corretta nella
   schermata di blocco.

## Struttura del progetto

```
Calm Otter/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/example/pauselock/
│       │   ├── MainActivity.kt                  # setup password + avvio pausa
│       │   ├── BlockOverlayActivity.kt           # schermata di blocco
│       │   ├── AppBlockerAccessibilityService.kt # rilevamento app in foreground
│       │   ├── HomeActivity.kt                   # app Home: blocco o inoltro al launcher originale
│       │   ├── LauncherManager.kt                # memorizza il launcher originale del telefono
│       │   ├── AllowedAppsManager.kt             # elenco app extra consentite
│       │   ├── AllowedAppsActivity.kt            # UI per modificare l'elenco (gated da password)
│       │   ├── SessionExpiryReceiver.kt          # scadenza automatica via AlarmManager
│       │   ├── SessionManager.kt                 # stato sessione, durata, Non disturbare
│       │   └── PasswordManager.kt                # hash/verifica password
│       └── res/
│           ├── layout/
│           ├── values/
│           └── xml/accessibility_service_config.xml
├── build.gradle.kts
└── settings.gradle.kts
```

## Come aprirlo

Apri la cartella `Calm Otter/` con Android Studio (Hedgehog o successivo):
verrà generato automaticamente il Gradle Wrapper e sincronizzate le
dipendenze. Non è incluso `gradlew` perché generato da Android Studio al
primo sync.

## Lista app extra consentite

Oltre al telefono, è possibile scegliere altre app che restano utilizzabili
durante una pausa (es. mappe, messaggi con la famiglia). L'elenco si gestisce
da "Gestisci app consentite" in `MainActivity`, ma per aprirlo viene
richiesta la password — la stessa impostata al primo avvio. Solo chi la
conosce può quindi aggiungere o togliere app dalla lista; l'elenco è salvato
in `SharedPreferences` locali (dato non sensibile, nessuna cifratura
necessaria) e letto in tempo reale da `AppBlockerAccessibilityService`.

## App Home

Calm Otter può essere impostata come app Home del telefono ("Imposta come Home
(consigliato)" in `MainActivity`). Da quel momento, ogni pressione del tasto
Home passa da `HomeActivity`:

- **sessione attiva**: mostra la stessa schermata di blocco (countdown +
  password) usata per le altre app, eliminando la via di fuga più comoda
  (le icone sulla home normale);
- **nessuna sessione attiva**: inoltra immediatamente al launcher originale
  del telefono — rilevato e salvato una sola volta da `LauncherManager` — e
  si chiude, così l'uso quotidiano resta invariato.

Questo non sostituisce l'`AccessibilityService` (resta necessario per
intercettare i passaggi via "App recenti") e non chiude del tutto il blocco
"soft": l'app Home può sempre essere ricambiata da Impostazioni > App > App
predefinite > Home, con lo stesso meccanismo (e lo stesso limite) di
Accessibilità. Aggiunge però attrito reale contro le ricadute d'abitudine.

## Limiti noti di questa versione (blocco "soft")

Su Android, senza che l'app sia *Device Owner* (provisioning tipo MDM), non
esiste un blocco davvero a prova di utente:

- L'utente può disattivare il servizio di Accessibilità da
  Impostazioni > Accessibilità, in qualsiasi momento, senza password.
- Avviando il telefono in Safe Mode, i servizi di accessibilità di terze
  parti non vengono caricati.
- Il tasto Home non è intercettabile: porta alla home, ma la schermata di
  blocco ricompare non appena si prova ad aprire un'altra app.

Per un blocco "hard" servirebbe trasformare l'app in Device Owner (richiede
provisioning al setup del dispositivo o reset di fabbrica) e usare
`DevicePolicyManager` con Lock Task Mode + `addUserRestriction` su
`DISALLOW_CONFIGURE_ACCESSIBILITY`, `DISALLOW_SAFE_BOOT`,
`DISALLOW_UNINSTALL_APPS`. È un passo significativo in termini di invasività
e complessità: lo lascerei come eventuale v2, solo se il blocco soft si rivela
insufficiente nell'uso reale.

## Possibili evoluzioni

- Device Admin (non Device Owner) per rendere più scomoda la disinstallazione
  durante una sessione attiva.
- Cronologia sessioni / statistiche d'uso.
- Sblocco remoto (richiede un piccolo backend) invece che solo locale.
- Whitelist configurabile invece del solo telefono (es. mappe, messaggi famiglia).
