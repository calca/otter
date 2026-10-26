# F-Droid

`com.calmotter.app.yml` è la bozza della ricetta per
[fdroiddata](https://gitlab.com/fdroid/fdroiddata). Non è ancora stata proposta.

- **Testi e immagini**: F-Droid li legge da `fastlane/metadata/android/<lingua>/`
  (copiati da `docs/store`; se cambiano lì vanno rigenerati anche qui).
  Il changelog è `changelogs/<versionCode>.txt`.
- **Versione**: la build passa `-PotterBuildNumber`/`-PotterVersionName`
  (o le variabili `OTTER_*`), così il `versionCode` coincide con quello di
  Play. Ogni release nuova richiede una riga in `Builds` e un file in `changelogs/`.
- **Firma**: senza `KEYSTORE_PATH` la release esce non firmata; F-Droid firma
  con la propria chiave, quindi dalla versione Play non si aggiorna a quella
  F-Droid e viceversa.
- **Prova locale**:
  `./gradlew assembleStableRelease -PotterBuildNumber=176 -PotterVersionName=0.6.0`
- Da verificare con `fdroid build` / `fdroid lint` prima della merge request:
  SDK 37.1 sul server di build, nome del pacchetto JDK, categorie.
