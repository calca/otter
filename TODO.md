# TODO — voci rimaste

Review del 2026-10-01. Le altre voci di quella lista sono state fatte (blocco
che restava a video dopo la fine, test di `BlockPolicy`, README e screenshot,
lint a zero, `core-ktx`, spacchettamento di `HistoryScreen`/`SettingsScreen`).
Restano due cose scelte di non fare ora, più un avviso di lint.

Convenzioni: **Verificato** = letto o riprodotto; **Sospetto** = non eseguito.
Sforzo: **S** (meno di un'ora), **M** (un pomeriggio).

## 1. ~~Rilascio degli altri allo sblocco anticipato~~ — fatto

La condizione e tutti i modi di finire la pausa sono ora in
`BlockSessionController`, coperti da `BlockSessionControllerTest` (host con
e senza compagni, ospite, token giusto e sbagliato, NFC assente, scadenza
naturale, uscita lenta). Resta non provato solo il gesto fisico fra due
telefoni, che l'emulatore non ha.

## 2. ~~Screenshot test delle schermate principali~~ — fatto

24 schermate di riferimento in `app/src/test/screenshots/` (Home e blocco
in tutte le palette e in chiaro/scuro; Cronologia, momento di chiusura,
pause programmate e Impostazioni in Salvia chiaro/scuro), confrontate a ogni
`testStableDebugUnitTest`, CI compresa. Nessuna libreria in più: vedi
`Screenshots.kt`. Da tenere d'occhio: i riferimenti sono registrati su macOS
e la CI gira su Linux; la tolleranza assorbe l'antialiasing, ma se la prima
esecuzione in CI fallisse per differenze di piattaforma, l'artefatto
`screenshot-diffs` lo mostra e i riferimenti vanno registrati dalla CI.

## 3. ~~Lint: aggiornamento di Gradle~~ — fatto

Wrapper a Gradle 9.8.0 (`./gradlew wrapper --gradle-version 9.8.0`); lint
ora senza alcun avviso.

## 4. Togliere `security-crypto` dopo la migrazione della password

Dal 2026-10-16 la password non usa più `EncryptedSharedPreferences`
(deprecata): `PasswordManager.migrateLegacy()` sposta il file vecchio al
primo avvio. La dipendenza resta solo per quella lettura. Quando nessuna
installazione può più avere il file vecchio (qualche release dopo, o con un
salto di versione minima), togliere `migrateLegacy()`, la dipendenza in
`app/build.gradle.kts`, i test di migrazione in `PasswordManagerTest` e
l'esclusione di `calm_otter_secure_prefs.xml` dai backup. **S**

