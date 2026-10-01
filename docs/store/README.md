# Publishing Calm Otter on Google Play

What's ready in the repository, and what still needs a person in front of
Play Console.

## Ready

- **Signed build**: `.github/workflows/android.yml` builds a signed
  `app-stable-release.aab` on every push to `main` (artifact
  `app-stable-release-aab`), with `versionCode` = the run number, so every
  upload is higher than the previous one. Needs the `KEYSTORE_*` secrets
  already described in that workflow.
- **Store listing texts** (IT/EN): [`listing.md`](listing.md).
- **Graphics**: icon 512 × 512, feature graphic 1024 × 500, five phone
  screenshots — in [`graphics/`](graphics) and [`screenshots/`](screenshots).
- **Privacy policy** (IT/EN): [`../privacy-policy.md`](../privacy-policy.md),
  public at <https://github.com/calca/otter/blob/main/docs/privacy-policy.md>.
- **Answers for the declarations** (data safety, Accessibility API,
  special-use foreground service, content rating, target audience):
  [`declarations.md`](declarations.md).
- **In-app Accessibility disclosure** with explicit consent, required for
  the Accessibility declaration.

## Still to do (needs you)

1. **Play Console developer account** (one-time fee), and the developer
   contact email to publish.
2. **Create the app** with package `com.calmotter.app`, default language
   Italian, free, app (not game).
3. **Play App Signing**: accept it at the first upload; the keystore used
   by CI becomes the *upload* key. Keep a backup of it outside GitHub.
4. **Fill in App content** with [`declarations.md`](declarations.md),
   including the **Accessibility video** (record it on a real phone:
   disclosure → enable → start a pause → open a blocked app).
5. **Upload the AAB to Internal testing** first, install it from Play on
   your own phone, and check the things the emulator couldn't
   (scheduled-pause notification, NFC between two phones, Quick Settings
   tile label).
6. **Closed testing**: new personal developer accounts must run a closed
   test with at least 12 testers for 14 consecutive days before they can
   apply for production. Friends who'd use Time together are the ideal
   testers.
7. **Production** after review. Expect the Accessibility declaration to be
   looked at closely: the honest wording already in the app and in the
   listing is the best defence.

## Before each release

- `./gradlew testStableDebugUnitTest testBetaDebugUnitTest lintStableDebug lintBetaDebug`
  green (CI does it on every push).
- If the UI changed on purpose: re-record screenshot tests, and consider
  refreshing `screenshots/` here and `docs/screenshots/` for the README.
- If what the app stores, sends or asks permission for changed: update
  `docs/privacy-policy.md` and `declarations.md` **before** uploading.
