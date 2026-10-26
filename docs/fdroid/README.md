# F-Droid

`com.calmotter.app.yml` è la bozza della ricetta per
[fdroiddata](https://gitlab.com/fdroid/fdroiddata). Non è ancora stata proposta.

- **Testi e immagini**: F-Droid li legge da `fastlane/metadata/android/<lingua>/`
  (copiati da `docs/store`; se cambiano lì vanno rigenerati anche qui).
  Il changelog è `changelogs/<versionCode>.txt`.
- **Versione**: canale a parte da Play, con `versionCode` derivato dalla
  versione (`major*10000 + minor*100 + patch`, 0.6.0 → 600) e scritto fisso,
  insieme a `versionName`, in `app/build.gradle.kts` (F-Droid lo legge dal
  sorgente, richiesta dei revisori). `release.yml` riscrive le due righe e
  aggiunge i changelog in `fastlane/` con un commit prima del tag, e la
  ricetta si aggiorna da sola (`AutoUpdateMode: Version`). La CI di Play le
  sostituisce con `OTTER_BUILD_NUMBER` / `OTTER_VERSION_NAME`.
- **Firma**: senza `KEYSTORE_PATH` la release esce non firmata e F-Droid la
  firma con la propria chiave (build non riproducibile, scelta voluta: due
  canali separati). Dalla versione Play non si aggiorna a quella F-Droid e
  viceversa.
- **Prova locale**:
  `./gradlew assembleStableRelease`
- Da verificare con `fdroid build` / `fdroid lint` prima della merge request:
  SDK 37.1 sul server di build, nome del pacchetto JDK, categorie.
