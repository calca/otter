# F-Droid

`com.calmotter.app.yml` è la bozza della ricetta per
[fdroiddata](https://gitlab.com/fdroid/fdroiddata). Non è ancora stata proposta.

- **Testi e immagini**: F-Droid li legge da `fastlane/metadata/android/<lingua>/`
  (copiati da `docs/store`; se cambiano lì vanno rigenerati anche qui).
  Il changelog è `changelogs/<versionCode>.txt`.
- **Versione**: canale a parte da Play, con `versionCode` derivato dalla
  versione (`major*10000 + minor*100 + patch`, 0.6.0 → 600). `release.yml`
  scrive `version.properties` e i changelog in `fastlane/` con un commit
  prima del tag, quindi F-Droid li trova sul tag e la ricetta si aggiorna da
  sola (`AutoUpdateMode: Version`). La build passa `-PotterBuildNumber` e
  `-PotterVersionName` (o le variabili `OTTER_*`).
- **Firma**: senza `KEYSTORE_PATH` la release esce non firmata e F-Droid la
  firma con la propria chiave (build non riproducibile, scelta voluta: due
  canali separati). Dalla versione Play non si aggiorna a quella F-Droid e
  viceversa.
- **Prova locale**:
  `./gradlew assembleStableRelease -PotterBuildNumber=600 -PotterVersionName=0.6.0`
- Da verificare con `fdroid build` / `fdroid lint` prima della merge request:
  SDK 37.1 sul server di build, nome del pacchetto JDK, categorie.
