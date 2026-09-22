# Calm Otter

Put the phone down, on purpose. Calm Otter blocks every app but Phone and
silences notifications for as long as you choose — and only the person who
holds the password can end it early. No sign-up, no backend, no tracking:
just you, your phone, and a bit of quiet.

<p align="center">
  <img src="docs/screenshots/home.png" width="200" alt="Home screen with weekly streak" />
  <img src="docs/screenshots/paused.png" width="200" alt="Active pause screen" />
  <img src="docs/screenshots/history.png" width="200" alt="Session history" />
  <img src="docs/screenshots/tempo_insieme.png" width="200" alt="Tempo Insieme setup screen" />
</p>

## How it works

1. **Set a password** — you, or someone you trust to hold you accountable,
   opens the app and sets it once. It's kept as a PBKDF2-HMAC-SHA256 hash
   with a random salt inside `EncryptedSharedPreferences`, encrypted via
   Android Keystore — the actual password is never stored anywhere.
2. **Grant two permissions** — Accessibility and Do Not Disturb access.
   That's the whole setup, one time only.
3. **Pick a duration and tap the otter.** Every app but Phone gets covered
   by a calm full-screen countdown, notifications go quiet — calls, alarms
   and whatever you're listening to still get through — and the pause is
   backed by a system alarm, so it ends on time even if you never touch
   the phone again.
4. **The pause ends itself** when time's up, or earlier if the right
   password is entered on the block screen.

## Extra Allowed Apps

Not everything needs to wait. Keep a short list of apps that stay
reachable during a pause — maps, a family chat, whatever is
non-negotiable. Only the password holder can change that list, so it
stays a deliberate exception, not a loophole.

## Home App

For an even quieter phone, set Calm Otter as your Home app. Now the Home
button itself is part of the pause instead of a side door back to your
app icons. It's optional, and it doesn't replace Accessibility — see
"Known Limits" below for why the two work together rather than either
one being enough alone.

## Tempo Insieme (Group Pause)

Some pauses are better together. "Time together" lets two or more people
start the *same* pause at the *same* moment, each on their own phone —
made for a couple, a family, or a team that wants to disconnect as one:

- **QR / manual code** — fully offline. The host shares a code (or a QR)
  with the duration and an agreed start time baked in; everyone else
  scans or types it in. The phones never actually talk to each other —
  they just agree in advance and start together.
- **Live lobby (Bluetooth, with NFC as a shortcut)** — the host opens a
  lobby, everyone else joins with a tap (NFC) or a quick search, and the
  host starts once people are in. The phones connect just long enough to
  agree on a start time, then disconnect the moment the pause begins.

That's why Calm Otter asks for a couple of extra permissions here —
**Camera** to scan a QR, **Bluetooth + NFC** for the live lobby — and
why it's worth saying plainly: none of it goes anywhere. No network
calls, no data collection. It stays between the phones in the room, or
never leaves the device at all in the QR/code case.

## Known Limits of this version ("soft" block)

On Android, without being the *Device Owner* (MDM-style provisioning),
there is no truly user-proof block, and Calm Otter doesn't pretend
otherwise:

- The Accessibility service can be turned off from Settings at any time,
  without a password.
- Booting in Safe Mode prevents third-party accessibility services from
  loading at all.
- Setting Calm Otter as the Home app can always be undone from
  Settings > Apps > Default apps > Home — same mechanism, same limit as
  Accessibility above.
- While idle and set as Home, Calm Otter forwards to your previous
  launcher without handing back the Home role, which can make that
  launcher show its own "set as default" nag or restrict some features.
  This is an unavoidable side effect of how Android's `RoleManager` works
  and is left as-is rather than "fixed."

For a genuinely hard block, the app would need Device Owner provisioning
and Lock Task Mode with `DISALLOW_CONFIGURE_ACCESSIBILITY`,
`DISALLOW_SAFE_BOOT`, and `DISALLOW_UNINSTALL_APPS` — a significant jump
in invasiveness and complexity, left for a possible v2 if the soft block
proves insufficient in practice.

## Possible Evolutions

- Device Admin (not Device Owner) to make uninstallation harder during an
  active session.
- Remote unlock (would require a small backend) instead of only local.
- Named participants in a group pause's block screen/history — currently
  generic, since the QR/code pairing mode has no live channel to learn
  names from, and the live-lobby mode doesn't carry them forward past the
  lobby yet.

## License

MIT — see [LICENSE](LICENSE).

---

## For developers

### Project Structure

```
otter.git/
├── app/
│   ├── build.gradle.kts        # beta/stable product flavors, see CLAUDE.md
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/calmotter/app/
│       │   └── res/
│       ├── beta/                # app_name override for the beta flavor
│       └── test/                # Robolectric unit tests
├── specs/                        # as-built requirements.md + design.md per feature
├── docs/screenshots/
├── build.gradle.kts
└── settings.gradle.kts
```

See `CLAUDE.md` for the full architecture rundown (Compose UI, the
`SessionManager`/`PasswordManager`/etc. singleton pattern, DND/alarm
flow) and `specs/README.md` for the index of feature specs.

### Building

```bash
./gradlew assembleDebug                                  # both flavors' debug APKs
./gradlew testStableDebugUnitTest testBetaDebugUnitTest  # unit tests (Robolectric)
./gradlew lintStableDebug lintBetaDebug                  # Android Lint (0 errors, CI-enforced)
```

Two flavors exist on a `channel` dimension so a beta build can sit
installed side by side with the "real" app on the same device:

- **`stable`** — `com.calmotter.app`, no suffix. The one that matters.
- **`beta`** — `com.calmotter.app.beta`, "Calm Otter Beta" in the
  launcher.

Open the project root with Android Studio (Hedgehog or later) and let it
sync; `gradlew` is generated on first sync if not already present.

CI (`.github/workflows/ci.yml`) runs lint + unit tests for both flavors
and `assembleDebug` on every branch/PR.
