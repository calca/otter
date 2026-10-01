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

## 2. Screenshot test delle schermate principali — **M**

Molti commit recenti sono regressioni visive scoperte a occhio: CTA troppo
brillanti in dark mode, bottone Sblocca invisibile, pillola di durata
invisibile. Con Roborazzi (o `captureToImage` con Robolectric
`GraphicsMode.NATIVE`) si avrebbe uno screenshot di riferimento per Home,
Blocco, Rilascio, lobby e Cronologia, in tutte le palette e in chiaro e
scuro, con un diff in CI. Vincolo da verificare: Robolectric è fissato a SDK
35 per Java 17 (vedi CLAUDE.md) e il rendering nativo deve funzionare con
quella combinazione.

## 3. ~~Lint: aggiornamento di Gradle~~ — fatto

Wrapper a Gradle 9.8.0 (`./gradlew wrapper --gradle-version 9.8.0`); lint
ora senza alcun avviso.
