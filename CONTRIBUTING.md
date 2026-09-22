# Contributing to Calm Otter

Thank you for your interest in Calm Otter! This document explains how to contribute,
with a focus on translations since that's the most accessible way to help.

---

## Adding a translation

All UI strings live in `app/src/main/res/values/strings.xml` (Italian, the base locale)
and `app/src/main/res/values-en/strings.xml` (English).

To add a new language:

1. **Create the folder** following Android's locale naming convention:

   | Language       | Folder              |
   |----------------|---------------------|
   | French         | `values-fr/`        |
   | German         | `values-de/`        |
   | Spanish        | `values-es/`        |
   | Portuguese (BR)| `values-pt-rBR/`    |
   | Japanese       | `values-ja/`        |
   | …              | `values-<BCP47>/`   |

   ```
   app/src/main/res/values-fr/strings.xml
   ```

2. **Copy `values-en/strings.xml`** as your starting point — it is the reference file
   for all translations (the Italian original is the source of truth for meaning, but
   English is easier to read for most contributors).

3. **Translate every `<string>` and every `<item>` inside `<string-array>` and `<plurals>`.**
   For a `<plurals>` block, `values-en/strings.xml` only defines the quantities
   English needs (`one`, `other`) — your language may need more (Polish and
   Russian, for example, also use `few` and `many`; Arabic uses all six). Add
   whichever quantities your language's plural rules require, following
   [Android's plural rules reference](https://developer.android.com/guide/topics/resources/string-resource.html#Plurals)
   or [CLDR's table](https://cldr.unicode.org/index/cldr-spec/plural-rules) for
   your locale — don't just copy the two English already has.

4. **Do not translate:**
   - The `name` attributes (e.g. `name="app_name"`) — these are code identifiers.
   - The app name `Calm Otter` itself — keep it as-is.
   - Emoji — keep them as-is.
   - Format placeholders: `%1$d`, `%2$d`, `\n` — keep them in the translated string,
     in the same order or reordered with `%1$d` → `%2$d` if the grammar requires it.

5. **Escape apostrophes** with a backslash: `it\'s` not `it's`.

6. **Open a Pull Request** with only your new `values-XX/strings.xml` file.
   Title: `i18n: add [Language] translation`.

### Example

```xml
<!-- values-fr/strings.xml -->
<resources>
    <string name="app_name">Calm Otter</string>
    <string name="block_title">Pause en cours</string>
    <string name="block_message">Toutes les applications sont bloquées, sauf le téléphone.
        Seule la personne qui connaît le mot de passe peut terminer la pause.</string>
    <!-- … -->
</resources>
```

### Checking your translation

Open the project in Android Studio, change your emulator/device language to your target
locale, and run the app. Android will automatically pick up your `values-XX/strings.xml`.
If a string is missing, Android falls back to English (`values-en/`), then to Italian
(`values/`).

---

## Reporting bugs

Open a GitHub Issue with:
- Android version and device model
- Steps to reproduce
- Expected vs actual behaviour

Please **do not** report security vulnerabilities as public issues — see below.

---

## Security vulnerabilities

Calm Otter is a wellbeing app, not a security product. Its lock is intentionally
described as "soft" (see README). That said, if you find a way to bypass the
password or access encrypted preferences, please report it privately by emailing
the maintainer before opening a public issue.

---

## Code contributions

Before opening a PR for a new feature:

1. Open an Issue first to discuss the idea — especially for anything that changes
   the lock model, the permission set, or the data stored on device.
2. Keep PRs focused: one feature or fix per PR.
3. Follow the existing code style (Kotlin idioms, no external libraries unless
   strictly necessary, no network calls — all data stays on device).
4. Update `strings.xml` (Italian) and `values-en/strings.xml` (English) for any
   new UI strings you add.
5. Document `soft lock` limitations honestly in comments and README if your
   change affects the security model.

---

## Project structure (quick reference)

```
app/src/main/
├── java/com/calmotter/app/
│   ├── MainActivity.kt                   # main screen
│   ├── OnboardingActivity.kt             # first-run guided setup
│   ├── BlockOverlayActivity.kt           # full-screen lock during pause
│   ├── HomeActivity.kt                   # Home launcher intercept
│   ├── HistoryActivity.kt                # session history
│   ├── AllowedAppsActivity.kt            # whitelist editor (password-gated)
│   ├── ChangePasswordActivity.kt         # change password screen
│   ├── AppBlockerAccessibilityService.kt # foreground app detection
│   ├── SessionForegroundService.kt       # persistent notification
│   ├── PauseWidgetProvider.kt            # 1×1 home screen widget
│   ├── BootReceiver.kt                   # restore session after reboot
│   ├── SessionExpiryReceiver.kt          # alarm-based auto-expiry
│   ├── SessionManager.kt                 # session state + DND
│   ├── SessionHistoryManager.kt          # local JSON history
│   ├── PasswordManager.kt                # PBKDF2 hashing + encrypted storage
│   ├── AllowedAppsManager.kt             # per-session app whitelist
│   ├── PhraseManager.kt                  # relaxing phrases toggle
│   ├── LauncherManager.kt                # original launcher detection
│   └── CalmCountdown.kt                  # calm time-remaining formatter
└── res/
    ├── values/strings.xml                # 🇮🇹 Italian (base locale)
    ├── values-en/strings.xml             # 🇬🇧 English
    ├── values-XX/strings.xml             # your language here
    ├── values/colors.xml                 # day palette (sage green)
    └── values-night/colors.xml           # night palette (slate blue)
```

---

*Calm Otter is built with the belief that less screen time is a personal choice
worth supporting with honest, non-manipulative tools. Contributions that align
with this spirit are welcome.*
