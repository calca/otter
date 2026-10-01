# TODO — voci rimaste

Review del 2026-10-01. Le altre voci di quella lista sono state fatte (blocco
che restava a video dopo la fine, test di `BlockPolicy`, README e screenshot,
lint a zero, `core-ktx`, spacchettamento di `HistoryScreen`/`SettingsScreen`).
Restano due cose scelte di non fare ora, più un avviso di lint.

Convenzioni: **Verificato** = letto o riprodotto; **Sospetto** = non eseguito.
Sforzo: **S** (meno di un'ora), **M** (un pomeriggio).

## 1. Rilascio degli altri allo sblocco anticipato, non provato su device — **S**

*Sospetto (nessun bug noto, solo non verificato).* Dopo `fbabbec` il passo
di rilascio compare solo quando l'host sblocca con la password prima della
fine. La scadenza naturale è stata verificata sull'emulatore, lo sblocco
anticipato no (serve impostare una password). Da fare: provarlo e, se
possibile, estrarre la condizione `canReleaseOthers` in una funzione pura
testabile, come per `BlockPolicy`.

## 2. Screenshot test delle schermate principali — **M**

Molti commit recenti sono regressioni visive scoperte a occhio: CTA troppo
brillanti in dark mode, bottone Sblocca invisibile, pillola di durata
invisibile. Con Roborazzi (o `captureToImage` con Robolectric
`GraphicsMode.NATIVE`) si avrebbe uno screenshot di riferimento per Home,
Blocco, Rilascio, lobby e Cronologia, in tutte le palette e in chiaro e
scuro, con un diff in CI. Vincolo da verificare: Robolectric è fissato a SDK
35 per Java 17 (vedi CLAUDE.md) e il rendering nativo deve funzionare con
quella combinazione.

## 3. Lint: aggiornamento di Gradle — **S**

*Verificato:* l'unico avviso rimasto è `AndroidGradlePluginVersion`, cioè
Gradle 9.8.0 disponibile (il wrapper è alla 9.7.1; CLAUDE.md dice 9.7.0, da
allineare). Va aggiornato con il wrapper, non a mano, e riprovato con CI.
