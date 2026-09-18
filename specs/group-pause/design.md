# Group Pause — Design

## Naming: "Tempo Insieme" / "Time Together"

A later UI/UX refinement pass renamed everything the user sees — the Home
entry button, the Create/Join dialog, screen titles, `BlockScreen`'s
indicator — from "Pausa di gruppo"/"Group pause" to **"Tempo
Insieme"/"Time Together"**, on request, for a warmer tone consistent with
the rest of the app's copy. **Nothing internal was renamed**: the
`specs/group-pause/` directory, `GroupPauseRecipe`/`GroupPauseHostScreen`/
etc. class and file names, `isGroupSession`, and every other code
identifier are unchanged — only `strings.xml`/`strings-en.xml` values
changed. This file keeps using the original technical names throughout,
since they still match the code; treat "Group Pause" here as the
implementation's name and "Tempo Insieme" as its current display name.

## Architecture: handshake-then-autonomy

The QR/text code is a **one-time, self-contained recipe**, not a live
pairing session. Once a device (host or joiner) has decoded a
`GroupPauseRecipe`, it needs nothing further from the other device — it
counts down locally and starts a normal pause session on its own. This is
what lets Phase 1 stay fully compliant with the project's hard "no
backend, no network calls" constraint (root `CLAUDE.md`): there is no
connection to keep alive, so there is nothing to build or depend on beyond
the code exchange itself.

```kotlin
data class GroupPauseRecipe(
    val durationMinutes: Int,
    val startAtEpochMillis: Long,
    val groupTag: Int,       // cosmetic random tag, no security role
)
```

## Key files

| File | Role |
|---|---|
| `GroupPauseRecipe.kt` | `GroupPauseRecipe` data class + `encode()`/`decodeGroupPauseRecipe()`. Pure Kotlin, no `Context` — directly unit-testable (`GroupPauseRecipeTest.kt`), same "pure state machine" shape as `LockoutPolicy.kt`. |
| `QrCodeGenerator.kt` | `generateQrCodeBitmap(content, sizePx, darkColor, lightColor)` — wraps `com.google.zxing.qrcode.QRCodeWriter`, paints the result into a `Bitmap.Config.ARGB_8888` manually. Colours are parameters (see "QR colours" below); `ARGB_8888` rather than `RGB_565` because `lightColor` may be `TRANSPARENT`. |
| `ui/screens/GroupPauseHostScreen.kt` | Goes **directly** into the live Bluetooth/NFC lobby (`GroupPauseBluetoothLobbyHostScreen`), which now also carries the duration picker (there is no separate duration-picker step any more — see "Two more steps folded away" below); its "Prefer a code or a QR?" link opens a private `GroupPauseQrShareScreen` that shows the QR **immediately**, with the start-delay picker sitting above it (no separate delay-picker step either) → the shared countdown. |
| `ui/screens/GroupPauseJoinScreen.kt` | The live Bluetooth/NFC lobby by default (`GroupPauseBluetoothLobbyJoinScreen`), or (via its "Scan or enter a code" link — renamed, see "Two more steps folded away") a private `GroupPauseCodeEntryScreen`: Scan is now a **full-screen** camera view (CameraX + zxing) with the guide/instructions overlaid on the live preview, not a small box inside a padded column; manual-code entry is unchanged. Either path decodes a recipe → the shared countdown. |
| `ui/screens/GroupPauseCountdownScreen.kt` | Shared by both flows — ticks "starting in mm:ss", calls back once at zero. |
| `GroupPauseHostActivity.kt` / `GroupPauseJoinActivity.kt` | Thin `BaseActivity` wrappers: on the countdown reaching zero, call `sessionManager.startSession(durationMinutes, isGroupSession = true)` and `finish()`. |

## Recipe encoding

8 raw bytes, packed manually (not a generic serialization format — kept
this small on purpose so the QR stays low-density and the manual code
stays short enough to read aloud or retype):

| Bytes | Field |
|---|---|
| 0–3 | `startAtEpochMillis / 1000` as a big-endian Int (epoch **seconds**, not millis — halves the range needed, still good until year 2106) |
| 4 | `durationMinutes` (0–255) |
| 5–6 | `groupTag`, a `Random.nextInt(0, 65536)` cosmetic value with no security role — it exists only so two codes generated back-to-back don't encode to visibly identical bytes; nothing reads it back today |
| 7 | XOR checksum of bytes 0–6 — catches typos/corruption in manual entry, not a cryptographic guarantee |

Encoded with `java.util.Base64.getUrlEncoder().withoutPadding()` /
`getUrlDecoder()` — **deliberately not `android.util.Base64`**, which is a
`RuntimeException("Stub!")` outside a real Android runtime or Robolectric,
which would have made `GroupPauseRecipeTest` impossible to run as a plain
JVM unit test. `java.util.Base64` has been available since API 26, which
is this project's exact `minSdk`, so there's no compatibility gap.
Produces an ~11-character URL-safe string, used identically as both the QR
payload and the manual-entry code — one format, not two, so there's only
one decode path to validate.

## Decode validation (`decodeGroupPauseRecipe`)

Returns `null` uniformly on any of the following (the UI shows one generic
"invalid or expired" message regardless of which — see requirements.md
User Story 2, criterion 4):

- Wrong decoded length (not exactly 8 bytes).
- Checksum mismatch.
- `durationMinutes <= 0`.
- `startAtEpochMillis <= now` — already started.
- `startAtEpochMillis - now > MAX_FUTURE_START_MILLIS` (30 minutes) — a
  sanity bound, not a real product limit: the host UI only offers 1/2/5
  minute delays, so anything further out than that can only be corruption,
  not a legitimately-generated code.

## Required core-app changes

### `MainActivity.onResume()` gap (the one load-bearing fix)

`onResume()` previously only refreshed theme state (`resumeSignal`) — it
never checked `sessionManager.isSessionActive()`. The existing "tap the
otter to start" path never needed it to, because `MainScreen`'s own
`onSessionStarted` callback fires synchronously, inside the same
Activity/Composition, the instant a session starts.

Group Pause's Host/Join Activities are different: they start the session
and then `finish()` themselves, returning to `MainActivity` from *outside*
its Composition — nothing inside `MainScreen` ever gets a callback for
that. Without an explicit check, the countdown would finish, the session
would genuinely start (DND, block service, notification — all correct),
and the user would simply land back on an unblocked-looking Home screen
with no visible sign anything happened, until the next unrelated
recomposition. Fixed with one addition at the end of `onResume()`:

```kotlin
if (sessionManager.isSessionActive() && !showBlockScreen) {
    enterBlockScreen()
}
```

### `SessionManager` / `SessionRecord` / `CalmOtterDatabase`

- `startSession(durationMinutes: Int, isGroupSession: Boolean = false)` —
  the added parameter is persisted as a new pref key and read back by a new
  `isGroupSession(): Boolean` getter (used by `BlockScreen`) and by
  `endSession()` when building the `SessionRecord` (used by History).
- `SessionRecord` gains `val isGroupSession: Boolean = false`.
- `CalmOtterDatabase` bumps `version` 1 → 2 with
  `Migration(1, 2) { db.execSQL("ALTER TABLE sessions ADD COLUMN isGroupSession INTEGER NOT NULL DEFAULT 0") }`,
  registered via `.addMigrations(MIGRATION_1_2)`. Existing rows default to
  `false` — no data loss, verified on-device against a previously-populated
  install (reinstall over existing app data, no crash, correct pre-existing
  History state shown).

### UI touch points

- `MainScreen.kt` — one new text button (only visible when no session is
  active, placed near the weekly summary text so it doesn't compete with
  the primary tap-the-otter gesture) opens a Create/Join chooser
  `AlertDialog`, the same shape already used for `PermissionExplainerDialog`.
- `BlockScreen.kt` — when `sessionManager.isGroupSession()` is true, shows
  one extra line under "Paused": `block_group_indicator` ("You're having
  time together"). Deliberately generic — see the "no participant count"
  limitation in requirements.md.
- `SessionForegroundService.kt`'s persistent notification — the subtext
  otherwise rotates randomly through `notification_encouragement_phrases`
  (see `specs/pause-session-core/`); when `isGroupSession()` is true it's
  pinned to the same `block_group_indicator` string BlockScreen uses
  instead, so a shared pause reads as such wherever it's visible, not just
  on the block screen. Reuses the existing string rather than adding a
  second, differently-worded way of saying "this is a group pause."
- `HistoryScreen.kt`'s `SessionRow` — originally a small `history_group_tag`
  text line ("Group pause") under the outcome text; replaced in the later
  UI/UX refinement pass by `OtterHistoryIcon` inside a tinted chip — see
  that section below, no text label at all any more.
- `CalmBackground.kt` — its doc comment previously scoped `calmBackground()`
  to Home/Onboarding/BlockScreen only (deliberately not Settings/History).
  Extended to include the three new group-pause screens, since they're the
  same "opening/ritual" category as Home/Onboarding, not administration
  like Settings/History.

## Camera scanning: CameraX + zxing, not ML Kit

`GroupPauseJoinScreen.kt`'s scan tab uses `androidx.camera:camera-*` for
the preview/frame pipeline and `com.google.zxing:core` for decoding —
deliberately not ML Kit or `zxing-android-embedded`, to avoid a Google Play
Services dependency and to keep the scanning screen a custom themed
Compose UI rather than a vendored Activity, consistent with this app's
general no-heavy-dependency posture (the same reasoning that led to
hand-authored vector path data instead of a third-party icon library
elsewhere in this app).

`QrCodeAnalyzer` (an `ImageAnalysis.Analyzer`) decodes each frame's Y-plane
directly: `PlanarYUVLuminanceSource(bytes, plane.rowStride, image.height, 0,
0, image.width, image.height, false)` → `BinaryBitmap(HybridBinarizer(source))`
→ `MultiFormatReader().decode(bitmap)`. `NotFoundException` is caught
silently — expected on nearly every frame with no QR in view, not an error
— any other `Exception` is caught defensively, and `image.close()` always
runs in `finally` regardless of outcome. The camera is bound inside a
`DisposableEffect(Unit)` (`ProcessCameraProvider`, bound on entry,
`unbindAll()` in `onDispose`) so it never leaks across navigation.

New permission: `android.permission.CAMERA`, requested via the same
`ActivityResultContracts.RequestPermission()` idiom `MainActivity.kt`
already uses for `POST_NOTIFICATIONS`. `<uses-feature
android:name="android.hardware.camera" android:required="false" />` — the
manual-code tab works with no camera at all, so the feature isn't required
to install the app.

New dependencies: `com.google.zxing:core:3.5.3`,
`androidx.camera:camera-core/camera2/lifecycle/view:1.4.1`.

## Phase 2: live Bluetooth lobby + NFC tap-to-connect

Built as a second pairing mode alongside Phase 1's QR/manual code, which
is unchanged and still available as a fallback. **As first shipped**,
Phase 2 was a "QR/Codice" vs "Bluetooth" pill on the setup screen, both
options at equal visual weight; **a later UI/UX refinement pass (see its
own section below) flattened this** so the live lobby is the default
screen for both creating and joining, with QR/manual-code reachable one
tap away via a secondary link — the pill selector described in the rest
of this section's original text no longer exists in the code, only the
underlying Bluetooth/NFC transport it describes does. Solves Phase 1's
core limitation: with a live channel, the host can now see who has joined
before starting, and gates "Start" on at least one participant being
present.

**Transport: classic Bluetooth (`android.bluetooth.*`), not Nearby
Connections.** Same reasoning Phase 1 already applied when it picked
CameraX + `zxing:core` over ML Kit specifically to avoid a Google Play
Services dependency — Nearby Connections is a GMS API; classic Bluetooth
is plain platform framework. Zero new Gradle dependencies for all of
Phase 2 (Bluetooth *and* NFC are both framework APIs).

**Key continuity with Phase 1**: the live channel is used *only* for the
lobby (discovery, name exchange, host deciding when to start). Once the
host taps "Start," it builds the exact same `GroupPauseRecipe` Phase 1
defines, broadcasts the encoded string over the open sockets, and every
device — host included — hands off to the *unmodified*
`GroupPauseCountdownScreen` → `SessionManager.startSession(isGroupSession
= true)`. This is exactly the seam this file's earlier "Deferred" section
(now superseded by this one) said Phase 1 was written to leave open.

### Protocol

`bluetooth/GroupPauseBluetoothProtocol.kt` — a fixed, hardcoded service
`UUID` (host and joiner must agree on it statically, same role the fixed
recipe byte layout plays for QR/manual codes) plus a tiny line-based
message format over the RFCOMM socket's raw streams: `HELLO:<name>` (sent
once by the joiner right after connecting) and `RECIPE:<code>` (sent once
by the host, reusing `GroupPauseRecipe.encode()` verbatim — Phase 2 never
invents a second recipe format). Framing logic (`formatHello`/
`parseGroupPauseBtMessage`/`readLine`/`writeLine`) is deliberately plain
Kotlin over `InputStream`/`OutputStream`, no Android class involved, so
`GroupPauseBluetoothProtocolTest.kt` runs as an ordinary JVM unit test —
same "pure logic, no Context" shape as `GroupPauseRecipe.kt`.

`bluetooth/GroupPauseBluetoothHost.kt` / `GroupPauseBluetoothJoin.kt` wrap
the actual `BluetoothServerSocket`/`BluetoothSocket` I/O. Neither is a
`getInstance()` app-wide singleton (unlike `SessionManager` and friends) —
this state is ephemeral, alive only while a lobby screen is on screen,
instantiated with `remember { }` and torn down via `DisposableEffect`'s
`onDispose`, the same lifecycle discipline `GroupPauseJoinScreen.kt`'s
`QrScannerView` already uses for its CameraX binding.

### Why NFC exchanges a name marker, not a MAC address

The intuitive design — "read the host's Bluetooth address over NFC, then
connect directly" — does not actually work: since Android 6.0,
`BluetoothAdapter.getAddress()` returns a fixed dummy value
(`02:00:00:00:00:00`) to any app reading its *own* local adapter address;
there is no public API to get it for real. The actual design instead has
the host set its Bluetooth adapter's *name* to a short marker
(`CalmOtter-<groupTag>`, via `BluetoothAdapter.name =`) before becoming
discoverable, and conveys that same marker string over NFC. The joiner
still runs ordinary Bluetooth discovery — which *does* return real
addresses for devices it finds, since the MAC restriction is specifically
about reading your own local adapter's address, not other discovered
devices' addresses — but auto-connects to the first discovered device
whose name matches the marker, instead of showing a manual list to tap
through. **NFC's actual job is to skip the "browse a list of nearby
Bluetooth devices" UI step, not to skip Bluetooth discovery itself** —
Android classic discovery only surfaces devices that are paired or
currently in discoverable mode, so the host still has to request
discoverability (`ACTION_REQUEST_DISCOVERABLE`) either way.

This means the manual-Bluetooth and NFC-assisted join paths run through
the exact same `GroupPauseBluetoothJoin.connectTo()` code — only how the
target device is *selected* differs (`GroupPauseBluetoothJoin.discovered`
list + a tap, vs. `startDiscovery(autoConnectToNameMarker = ...)` picking
the first match automatically).

### NFC implementation

Host Card Emulation (HCE), not classic Android Beam/NFC P2P (`NdefPush`) —
deprecated and unreliable since Android 10, not a sound foundation here.

- `nfc/GroupPauseHceService.kt` — a `HostApduService`. As first shipped,
  the host had to flip an explicit "Avvicina i telefoni" toggle to arm it;
  the later UI/UX refinement pass removed that toggle — the host's lobby
  screen now sets the `@Volatile` companion field (`pendingMarker`) to the
  marker string automatically whenever `NfcAdapter.getDefaultAdapter()` is
  non-null, no user action needed (see "UI/UX refinement pass" below).
  `processCommandApdu()` responds to any incoming command with that
  marker's UTF-8 bytes plus the success status word (`90 00`) — a
  deliberate simplification, since this protocol only ever has one
  command/response pair, unlike a real payment HCE service that parses
  the command APDU itself.
- `nfc/GroupPauseNfcReader.kt` — the joiner's side, `NfcAdapter
  .enableReaderMode()` (not `NdefPush`). On tag discovery, opens `IsoDep`,
  sends a SELECT-AID APDU, and hands the response payload back as the
  marker string. Runs its callback on a Binder thread, not the main
  thread — callers must post back to the main thread before touching
  Compose state (`GroupPauseBluetoothLobbyJoinScreen` does this via a
  plain `Handler(Looper.getMainLooper())`).
- `res/xml/apduservice.xml` declares one custom AID
  (`F0010203040506`, invented in the proprietary `0xF0`–`0xFE` range, not
  a real registered AID).
- Manifest: the new `<service>` for `GroupPauseHceService` is
  `android:exported="true"` — the one deliberate exception to this app's
  usual `exported="false"` convention, required because the OS itself has
  to bind to it to route a tap to this app; locked down with
  `android:permission="android.permission.BIND_NFC_SERVICE"`, which only
  the system holds.

### Permissions

`BLUETOOTH_CONNECT`/`BLUETOOTH_ADVERTISE`/`BLUETOOTH_SCAN` (Android 12+,
runtime/dangerous — requested together via
`ActivityResultContracts.RequestMultiplePermissions()`, a new pattern for
this codebase; every existing single-permission request, `CAMERA` and
`POST_NOTIFICATIONS`, uses `RequestPermission()` instead) plus legacy
`BLUETOOTH`/`BLUETOOTH_ADMIN` (`maxSdkVersion="30"`) for this project's
`minSdk 26` floor. `BLUETOOTH_SCAN` carries
`android:usesPermissionFlags="neverForLocation"` — truthful here, since
discovery results are only ever used to find a device by name, never to
infer physical location, which is what lets this skip requiring
`ACCESS_FINE_LOCATION` too. Both `android.hardware.bluetooth` and
`android.hardware.nfc.hce` are declared `required="false"`: Phase 1's
QR/manual-code path still works with neither radio present.
`groupPauseBluetoothRuntimePermissions()`/`hasGroupPauseBluetoothPermissions()`
live in `PermissionChecks.kt`, alongside this app's other shared
permission-check helpers (`isAccessibilityServiceEnabled`,
`isDndAccessGranted`).

### What Phase 2 still doesn't do

- **Named participants elsewhere in the app.** The live lobby shows real
  connected names, but `BlockScreen`'s `block_group_indicator` is
  unchanged — still generic ("you're having time together"), not "with
  Marco and Giulia." Nothing in `SessionRecord`/`GroupPauseRecipe` carries
  participant names forward past the lobby; wiring that through was
  judged out of scope for this pass and would be a natural next
  increment. (`HistoryScreen`'s old text tag is gone entirely, replaced by
  an icon — see "UI/UX refinement pass" below — so this limitation no
  longer applies there, only to `BlockScreen`.)
- **Verification is code-level only for the live paths.** See
  "Verification performed" below — an actual two-device Bluetooth
  handshake or NFC tap was not observed in this session, since only one
  adb-connected device was available.

## UI/UX refinement pass

After Phase 2 first shipped, the project owner reviewed it against
mockups (an HTML artifact iterated over two rounds) and asked for a
second pass — fewer steps, more use of the otter mascot, warmer copy, and
a couple of outright confusing spots fixed. Everything below was decided
via that mockup review, not guessed at directly.

**Naming** — see the dedicated section at the top of this file.

**Flattened navigation, no more pairing-mode pill.** The original setup
screen asked for pairing mode (QR/Codice vs Bluetooth) *and* duration *and*
(for QR) start-delay, all before doing anything — "too confusionaria," per
the request. Now:
- `GroupPauseHostScreen` goes straight into `GroupPauseBluetoothLobbyHostScreen`
  (the live lobby) — QR/manual code is a fallback reached via that lobby's
  "Prefer a code or a QR?" link, which opens `GroupPauseQrShareScreen`
  (generates the code immediately) before showing the countdown.
- `GroupPauseJoinScreen` mirrors this: `GroupPauseBluetoothLobbyJoinScreen`
  is the default screen (no more three-way Scan/Manual/Bluetooth pill up
  front), with a "Scan or enter a code" link opening the private
  `GroupPauseCodeEntryScreen` (Scan/Manual, reached differently — see
  below for what changed inside it).

**Two more steps folded away: duration into the lobby, start-delay into the
QR page.** Once the flattening above had made the live-Bluetooth path
Setup → Lobby → Countdown and the QR path Setup → Lobby → QrDelayPicker →
Countdown, both remaining "ask one thing, then move on" screens were
themselves reported as unnecessary friction — "velocizziamo," per the
request — and removed:
- `GroupPauseSetupScreen` (the standalone duration picker) is gone.
  `GroupPauseBluetoothLobbyHostScreen` now takes `initialDurationMinutes`
  (Home's `selectedDurationIndex`, passed via `MainActivity` →
  `GroupPauseHostActivity.EXTRA_DURATION_MINUTES`) and renders the same
  `MinutePillRow` **inside** the lobby, as local `mutableIntStateOf` state
  — adjustable for as long as the lobby is on screen, not locked at entry.
  Changing it calls `GroupPauseBluetoothHost.updateDuration()` so the next
  participant to connect sees the current value in their `LobbyInfo`
  preview (participants already connected keep whatever they saw at
  connect time — the *actual* duration is always whatever the final
  recipe says at "Let's go", so this is a preview-only staleness, not a
  correctness issue).
- `GroupPauseQrDelayScreen` (the standalone start-delay picker) is gone
  too. `GroupPauseQrShareScreen` shows the QR **immediately** on entry
  (default delay 2 minutes), with the same `MinutePillRow` sitting above
  the QR image. Changing the delay rebuilds the `GroupPauseRecipe` (new
  `startAtEpochMillis`, same `groupTag`), which regenerates the QR
  (`GroupPauseShareHeader` already keys its bitmap off `code`) and
  restarts the countdown (`GroupPauseCountdownScreen` already keys its
  `LaunchedEffect` off `startAtEpochMillis`) — no new plumbing needed in
  either, both were already reactive by construction. Accepted tradeoff:
  if someone scans the code and the host then changes the delay, that
  joiner counts down to the old target with no channel to correct them —
  the same class of risk the "handshake then autonomy" architecture (see
  above) already accepts elsewhere in this flow, and the window here is
  the same few-minutes scale.
- The host's own duration-picker options were widened from the original
  fixed `[15, 30, 60, 90, 120]` to the same 8 values Home's
  `DurationChipRow` offers (`30..240` step 30), specifically so the value
  handed in from Home always lands on an exact option instead of needing
  to be normalized to the nearest one.

**No more dedicated permission/Bluetooth-off/discoverable screens.** The
original Phase 2 had the host and joiner lobby screens gate on three
sequential full-screen steps (`HostLobbyStep`/`JoinLobbyStep`:
`PERMISSIONS` → `ENABLE_BLUETOOTH` → `DISCOVERABLE`) before showing
anything else — "la pagina intera è pessima," per the request. Both
`GroupPauseBluetoothLobbyHostScreen` and
`GroupPauseBluetoothLobbyJoinScreen` now show their real content (the
otter, the participant ring or NFC/search hero) immediately regardless of
readiness, with a single non-blocking `GentleBanner`/
`GentleReadinessBanner` shown inline above the action buttons whenever
`!hasPermissions || !bluetoothEnabled` — its text and tap action switch
between "grant permissions" and "turn on Bluetooth" depending on which is
missing. The primary action button (`Iniziamo`/"Let's go" on the host,
implicitly gated the same way on the joiner) stays enabled/disabled by the
real underlying state rather than by which "step" is showing. Discoverable
mode (`ACTION_REQUEST_DISCOVERABLE`) is requested automatically, once, via
a `DisposableEffect` as soon as permissions+Bluetooth are both ready — no
banner or screen for that step at all, since it's a single one-shot system
dialog rather than a persistent state to nudge about.

**NFC-first on the join side.** `GroupPauseBluetoothLobbyJoinScreen` used
to present NFC and manual Bluetooth search as two equal buttons
(`JoinLobbyState.ChoosingMode`). It's now state-driven by
`nfcAvailable`: if the device has NFC, the screen lands directly on the
NFC hero state (`OtterTapMark` illustration, reader mode started
automatically via `DisposableEffect`, no toggle or extra tap needed) with
"Cerca un amico nelle vicinanze"/"Look for a nearby friend" and "Scansiona
o inserisci un codice"/"Scan or enter a code" (`group_pause_join_code_link`
— renamed from "Ho un codice o un QR"/"I have a code or a QR", reported as
not reading like an actionable link) as secondary text links below; if
the device has no NFC, it lands on the search-hero state instead
(`group_pause_join_search_hero_title`), same secondary code/QR link, no
NFC option shown. The host's NFC advertising (`GroupPauseHceService
.pendingMarker`) is likewise now automatic — set whenever
`NfcAdapter.getDefaultAdapter()` is non-null and the lobby is ready, no
user-facing toggle any more.

**The Scan tab is now full-screen.** `GroupPauseCodeEntryScreen`'s camera
view used to sit in a fixed 260dp box inside the same padded
`CalmScreenColumn` as everything else — unchallenged since Phase 1, then
reported as worth fixing once the rest of this flow had been tightened up.
`ScanFullScreen` now renders `QrScannerView` at `Modifier.fillMaxSize()`
as the base layer of a `Box`, with a purely decorative guide frame
centered over it (zxing decodes the whole frame regardless, so this
doesn't constrain what can be scanned — it just tells the eye where to
point), an instruction string plus the "enter a code manually" fallback
link at the bottom, and Cancel at the top — all on top of the live
preview, over a dark vertical-gradient scrim rather than the app's usual
theme-colored text, since theme colors give no legibility guarantee over
an arbitrary real-world camera feed. The pre-permission state (nothing to
show full-screen yet) keeps the app's ordinary `calmBackground()` instead
of that scrim treatment. The old always-visible Scan/Manual pill row is
gone with it: Scan is reached by default (from the lobby's link) or via
the "scan instead" text link on the Manual screen, and Manual is reached
via the new "enter a code manually" link on the Scan screen or its own
pill-turned-link on the way back — one explicit link each way instead of
two pills always both visible.

**Otter mascot throughout, not just Home.** `ui/mascot/OtterMarks.kt`
gained three new marks for this feature specifically:
- `OtterSatelliteMark` — the same 3-circle silhouette construction as
  `OtterFloatMark`, flattened to one flat fill with no face detail (same
  "more reduced than the color version" precedent the monochrome app icon
  already established). `GroupPauseBluetoothLobbyHostScreen`'s
  `ParticipantRing` places one per connected participant (up to 6 shown
  explicitly) around the central `OtterFloatMark`, at even angles computed
  with `cos`/`sin` over `Dp` radius — the ring itself is a dashed stroke
  while empty, a solid one once someone's joined.
- `OtterTapMark` — two of that same silhouette, tilted toward each other
  (same rotation technique as `PactPawsMark`, applied to whole otters
  instead of paw shapes) with a small arc between them where they'd touch —
  the join screen's NFC hero illustration, explaining "tap phones
  together" visually rather than through text alone.
- `OtterHistoryIcon` — see "History icon system" below.
- `TogetherMark` — `PactPawsMark`'s two paws without its circular badge
  background, sized for inline use next to text; `MainScreen.kt`'s
  "Tempo insieme" entry button now leads with this icon (the button was
  "a bit anonymous," per the request) instead of being bare text.

**History icon system, replacing the colored dot + text tag.** The old
`SessionRow` drew a plain green/red `Box` circle (outcome) plus, for group
sessions, a separate `history_group_tag` text line ("Group pause") — two
different signaling mechanisms stacked in one row. Replaced by a single
`SessionOutcomeIcon`: a rounded chip (`Surface`, tint `primary` if
completed naturally, `error`/amber-family if ended early — the same
tinted-chip language `HistoryScreen`/`AllowedAppsScreen` already use
elsewhere) containing `OtterHistoryIcon`, which draws the plain otter
silhouette for a solo session or the silhouette plus two concentric
"signal" arcs above the head (same arc shapes as `OtterTapMark`'s) for a
`isGroupSession` one. Two independent signals (chip color, icon shape), no
text label needed for either — `history_group_tag` was deleted from both
locale files once nothing referenced it any more.

**Gentler copy throughout**, matching the mockup review — a sample of the
renames (full list in each string's own key, `values/strings.xml`):
"Crea"/"Create" (host primary button) → "Crea il codice"/"Create the
code" (QR path) or removed entirely (live path, no separate "open lobby"
tap any more); "Avvia"/"Start" → "Iniziamo"/"Let's go" (reusing the same
word onboarding's last step already uses, `onb_finish`, for consistency —
not the same string resource, a separate key with identical text, kept
independent since the two contexts are unrelated); "Ricerca dispositivi in
corso…"/"Searching for devices…" → "Alla ricerca di un amico…"/"Looking
for a friend…" (the join screen is looking for a *person*, not a
*device*, even though the underlying mechanism is a Bluetooth device
scan).

## QR colours: transparent in light themes, a tinted plate in dark ones

The QR was originally painted pure black on pure white. On the app's soft
palettes that white square read as pasted-on — reported directly ("il
bianco stona") with the question of whether the background could simply be
transparent.

It can, but only in light themes, and the reason is functional rather
than aesthetic:

- **The QR must stay dark-on-light.** The joiner decodes with
  `MultiFormatReader` + `HybridBinarizer` and **no** `DecodeHintType`
  (see `GroupPauseJoinScreen.kt`), so an inverted QR — light modules on a
  dark field — is simply not read. Most system camera apps don't read
  inverted codes either, and the share hint invites scanning from the
  other person's phone, so this isn't only about our own decoder.
- **With a transparent field, the "light" half is whatever is behind the
  QR.** In light themes that's `calmBackground`'s gradient, which is
  light — fine. In dark themes the background is `0xFF1A2B38` /
  `0xFF1E1A2E` / `0xFF2A1812`, and dark modules on it would be
  unreadable. `CalmOtterTheme` picks the dark scheme from
  `isSystemInDarkTheme()`, so this is reachable by any user with dark
  mode on, not a corner case.

So the treatment is split:

| | field (`lightColor`) | modules (`darkColor`) |
|---|---|---|
| Light themes | `Color.Transparent` — the gradient shows through | `onBackground` (near-black in all three light palettes) |
| Dark themes | `onBackground` (a soft light tint of the palette) on a 20dp rounded, 12dp-padded plate | `background` (the palette's own dark tone) |

Measured contrast (worst case = the gradient's darkest point, `primary`
at alpha 0.09 over `surface`): **14.1:1 Sage, 14.7:1 Lavender, 15.0:1
Terracotta** in light themes, and **11.3 / 12.3 / 13.0:1** on the dark
themes' plates. Using each palette's `primary` for the modules was tried
first and rejected: it drops to **3.9:1 on Lavender**, too low to rely on
for a code meant to be read by a camera.

The dark-theme plate is deliberately *not* also applied in light themes —
adding it there would reinstate exactly the rectangle this change
removes.

`QrCodeGeneratorTest.kt` guards all of this by re-decoding the generated
bitmap through the joiner's exact zxing pipeline, per palette, rather
than trusting the contrast arithmetic: the transparent case is
composited over the real gradient colour first (a transparent pixel reads
as RGB 0x000000 when sampled raw, so decoding the bitmap as-is would
falsely fail — the camera sees the composited result, and that's what the
test reproduces). A fourth test asserts that an *inverted* QR is **not**
decodable by that pipeline, pinning the reason the dark theme needs a
plate; if zxing ever starts handling inversion, that test fails and the
decision can be revisited.

## Honest lobby state: the screen stops claiming to wait

Both lobby screens chose their hero title and subtitle purely from how
many participants had joined, ignoring readiness. With Bluetooth
permissions missing or Bluetooth off, the host lobby still announced
"In attesa di qualcuno…" / "Tap phones together or share via Bluetooth" —
an act of waiting that was not happening (the `DisposableEffect` only
calls `host.start()` once `allReady`), and an instruction the user could
not carry out. The gentle banner immediately below said the opposite.
That is the kind of copy this project explicitly refuses elsewhere, so it
was fixed on a request to improve the flow generally.

The structure is unchanged — no new screens, no return of the
`PERMISSIONS`/`ENABLE_BLUETOOTH` step pages removed in the refinement pass
above. Only what the screen *says* about itself changed:

- `!allReady` now selects `group_pause_notready_title` ("Quasi pronti" /
  "Almost ready") and a subtitle admitting something is still missing. The
  banner already names *which* thing and offers the action, so the hero
  deliberately does not repeat it.
- The joiner gets its own subtitle, `group_pause_notready_subtitle_join`
  ("…prima di poterti unire" / "…before you can join"): the host's wording
  is about inviting, which is not what a joiner is doing. Reusing one
  string here would have traded one inaccuracy for another.
- **The illustrations stop miming activity.** The host's
  `ParticipantRing` drew its dashed "waiting" circle regardless of
  readiness; it now draws a plain, fainter ring until the lobby is really
  listening (the dashes mean "waiting for someone", which has to be true
  to be shown). The joiner's `SearchingIllustration` is the stronger case:
  it *animates* expanding radar rings, and was doing so while no discovery
  was running at all — a fake scan. It now takes `searching` and animates
  only when discovery is actually live.

### Verification

The emulator's Bluetooth stack cannot be brought up
("Reach maximum retry to restart Bluetooth!" in logcat, adapter stuck at
`state: OFF` even after `svc bluetooth enable`), so the ready branch is
not reachable there normally. The not-ready states were checked directly
by revoking the three runtime permissions and walking the flow; the ready
branch was then checked by temporarily forcing `allReady = true`,
confirming the dashed ring and "Waiting for someone…" come back and the
banner disappears, and reverting that immediately afterwards.

## Duration chips: FlowRow, not a flat Row

`MinutePillRow` laid its options out in a plain `Row`. With the five
durations (15m / 30m / 1h / 1h30 / 2h) that row didn't fit the screen
width, so the last chip was squeezed until its own label wrapped: "2h"
rendered as "2" above "h", making that chip visibly taller than the rest
and breaking the line.

Now a `FlowRow` (stable in this Compose version — checked against the
resolved `foundation-layout` artifact, not assumed), centred, with the
chip `Text` also pinned to `maxLines = 1`. Options that don't fit move to
a second centred line intact.

Wrapping rather than horizontal scrolling, deliberately: the user is
choosing *between* these values, so all of them should stay visible at
once — scrolling would hide some behind an edge. It also survives longer
labels in other locales, which is the real reason not to just shrink the
padding until it happens to fit on this one device.

## The Create/Join chooser: two peers, not confirm/dismiss

The Home chooser dialog wired **Create as `confirmButton` and Join as
`dismissButton`**. They are two equally valid paths, so putting them in the
affirmative and negative slots was arbitrary at best; worse, Material's
conventions and TalkBack treat the dismiss slot as "cancel", so the Join
action was announced as the way out. And there was no declared way out at
all — only system back or a tap outside.

Both choices now live in the dialog body as two tappable rows
(`GroupPauseChoiceRow`), each with a one-line description, and the single
trailing action is Cancel, which is genuinely what it does. The
descriptions are not decoration: "Crea" and "Unisciti" alone left the
difference between the two journeys to be guessed. The intro string lost
its second half accordingly, which now would only repeat them.

Verified on-device that each row still reaches its Activity
(`GroupPauseHostActivity` / `GroupPauseJoinActivity`) and that Cancel
dismisses.

### …and then the dialog became a page

`GroupPauseChooserScreen` / `GroupPauseChooserActivity` (mockup: "Time
Together Chooser Page"). The reasoning that kept it a dialog — "a fork in a
flow, not a place" — reads well but did not survive using it: every step
*after* this fork (host lobby, join lobby, QR/code, countdown) is already a
full screen, so the flow opened in one register and continued in another.
A dialog also has nowhere to say what each path involves beyond one line,
which is exactly what a first step has to do.

What the page adds over the dialog body:

- A **role badge** next to each title — "chi ospita" / "chi si unisce".
  "Crea" and "Unisciti" say what the tap does; the badge says what you
  *are* afterwards, which is the thing two people deciding between
  themselves actually need.
- A `TandemPawsMedallion` hero, the pond treatment of Home reduced to
  140dp with `TogetherMark` inside. It does not reuse `PondStill`: that one
  is full-page and anchored to a centre shared with `BlockScreen`, an
  invariant with its own file (`OtterAnchoredScreen.kt`) that has no reason
  to be dragged in here.
- The closing line, which says why the screen exists rather than asking for
  anything.

Two departures from the mockup:

- **No bottom navigation bar**, as everywhere else in the redesign.
  Consequence worth stating: the mockup's lower half was that bar, so with
  it gone the content would sit high with half a screen of nothing below.
  The body is therefore centred in what remains of the page, scrolling
  instead of clipping when the system font makes it too tall.
- **No title in the top bar.** The mockup prints "Time Together" both there
  and under the medallion — the same words a finger's width apart. Same
  reason Home dropped its "Home" heading: the bar says where you go back
  to, not where you are.

`GroupPauseChooserActivity` holds no state of its own. It forwards Home's
chosen duration to `GroupPauseHostActivity` and `finish()`es itself on both
paths, so back from a lobby returns to Home rather than to the fork —
once the road is chosen there is nothing to come back to. Verified
on-device: back → Home, Create → host lobby, Join → join, back from the
lobby → Home.

The Cancel action went with the dialog; the requirement it satisfied (an
explicit way out, not just system back) is met by the top bar's back
arrow.

## Closing the asymmetry: `LOBBY:` tells the joiner what they're agreeing to

The Bluetooth protocol had exactly two messages: `HELLO:<name>`
(joiner → host) and `RECIPE:<code>` (host → joiner, sent **only when the
host presses Start**). Read together with the join screen, that meant the
joiner agreed to have their phone blocked:

- **for an unknown length of time** — the duration arrives inside the
  recipe, by which point the pause has already begun; and
- **by an unknown person** — `GroupPauseBluetoothHost.start()` does
  `adapter.name = lobbyMarkerName`, so in the discovery list the host
  appears as `CalmOtter-<tag>`, not as anybody. Two friends hosting in the
  same room are indistinguishable.

For a feature whose whole premise is a consented pact, that is a consent
gap rather than a polish issue, which is why it was fixed first.

A third message closes it: `LOBBY:<hostName>|<durationMinutes>`, sent by
the host immediately after receiving `HELLO`, long before Start. The
joiner's waiting state now shows the host's name and the duration.

Details worth keeping:

- **`|` is sanitised out of the name**, for the same reason newlines
  already were: it would break parsing exactly as a newline breaks the
  line framing.
- **A missing or non-numeric duration makes the whole message invalid**
  (parsed as `null`) rather than yielding "0 minutes" to someone deciding
  whether to join.
- **Backwards compatible by construction.** `parseGroupPauseBtMessage`
  already returned `null` for unrecognised lines and callers skip those,
  so an older build at either end ignores `LOBBY:` and keeps working; the
  joiner's two fields stay `null` and the screen falls back to its
  previous "waiting for the host" text. There is a test pinning this.
- **The host cannot read its own name from `adapter.name`** while hosting —
  that is the `CalmOtter-<tag>` marker. `localBluetoothDisplayName()` moved
  from a private function in the join screen into the `bluetooth` package
  so both sides derive the name the same way.
- A write failure when sending `LOBBY:` does not drop the participant:
  they stay in the list and the recipe is still attempted at Start. Losing
  a nicety is not a reason to reject someone already connected.

The name is *not* yet carried past the lobby into the session or History —
that remains the open gap already noted in this file and in README.

## Names survive the lobby, and a shortcut back to the same person

Until now the pairing work left no trace past the lobby: once started, a
Tempo Insieme session was indistinguishable from a solo one except for one
generic line, and History marked it with an icon but never said with whom.
This was the open gap noted in README and here; it is what made the flow
feel thin.

**`SessionRecord.companions`** (Room v2 → v3, `DEFAULT ''`) stores the
names, newline-separated. One text column rather than a join table: it
holds two or three names per row and is never queried by name. The default
is the correct value for old rows, not a placeholder — no earlier row
could have known the names, because they did not travel past the lobby.

The names flow lobby → screen → Activity → `SessionManager.startSession`
→ prefs → `SessionRecord`. The host copies `participantNames` *before*
`broadcastRecipeAndClose()`, which closes the connections; the joiner uses
the host name learned from `LOBBY:`. The QR/code path carries no names and
stays generic, which is why the old wording is kept as the fallback rather
than removed.

`BlockScreen` shows "Together with Marco", or a **plurals** resource for
more — "and 1 others" is ungrammatical in both languages, and one extra
person is the commonest case after none. History rows gain "with Marco".

**"Again with <name>"** on Home reads the most recent group session that
has names and opens the Create flow directly, preselecting that session's
duration (only if it is still one of the offered options — a duration that
no longer exists would select no chip at all). It saves taps, not pairing:
the Bluetooth connection still has to be made again, because it is closed
at start by design.

### The Home ran out of vertical room

Adding that one row made it vanish rather than overflow visibly: the inner
column is `weight(1f)` with no scrolling, so the extra button was composed
but clipped away, and did not even appear in the semantics tree. Found by
seeding a companion row and seeing nothing while History showed "with
Marco" correctly — which located the problem in the layout rather than the
data.

`OtterSlotHeight` dropped 320dp → 272dp to make room. Both screens read
that same constant, so the otter stays aligned (re-measured: Home 676,
block screen 681 — inside the float animation's own ±14px).

Worth knowing: the Home is now close to its vertical limit, and it fails
*silently* when exceeded. A larger font scale, or another permanent row,
would clip content with no visible sign. Making that column scrollable
would turn the failure into something recoverable; not done here, as it
changes the screen's single-page character and was outside this request.

## Social unlock: the host can release the others, in person

Requested as "qualcosa di social oltre al locale": whoever convened the
shared pause can end it for the others by holding the phones together,
instead of them typing a password they are not supposed to know.

**This deliberately bypasses the other person's local password, and that
is the decision, not a side effect.** Today the password is the only way
out and it belongs to an accountability partner. With this, someone can
leave a pause by arranging it with a willing host and four centimetres.
The chosen reading is the other one: *a pause convened together can be
ended together, in person* — the authorisation is physical contact plus a
deliberate gesture by the host, not a secret. It has to be stated, not
smuggled in, which is why it is written here, in the code, and in the
user-facing copy.

Scope, kept narrow on purpose:

- **Only the host releases**, and only people who were in *that* pause.
  One keeper per shared pause. A joiner's unlock screen offers it; a
  host's does not, having nobody to be released by.
- **The token is `UNLOCK:<groupTag>`**, the tag both devices already share
  from the recipe. It is *not* a secret — it travels in the Phase 1 QR —
  and is not the authorisation. It only prevents releasing someone who was
  in a different pause.
- **The offer is bounded by a screen, not by time or a background
  service**: after the host's own pause ends (either exit path), the block
  screen is replaced by a release step that advertises the token over HCE
  only while it is on screen. Dismissing it stops advertising.

**No chooser.** The unlock dialog already existed; it now also runs NFC
reader mode while open, and says so in its message line. Typing the
password or touching the other phone satisfies the same affordance,
whichever happens first — rather than adding a "password or NFC?" step to
an action that already has two.

This reuses the existing NFC plumbing on both sides (`GroupPauseHceService`
with its mutable payload, and `GroupPauseNfcReader`); the only new state is
`groupTag`/`isHost` on the session, which previously stopped at the lobby.

### What is and isn't verified

The token format and its rejection cases are unit-tested. The screens were
checked on-device by seeding session state directly: the joiner's dialog
shows the second route, the host's does not, and the host's release step
appears after unlocking and returns to Home when dismissed. Because the
emulator reports **no NFC feature at all**, both paths correctly disable
themselves there, so the UI had to be seen by temporarily bypassing that
one availability check, which was then reverted.

**The actual tap has never been executed.** It needs two NFC devices, which
this environment does not have — the same honest limit that applies to
every Bluetooth and NFC path in this feature.

## Secondary CTAs: tonal, one per screen

The code/QR fallback was a bare `TextButton` on all three screens that
offer it, and read as a caption rather than a way out. It now uses the
shared `CalmSecondaryButton` (`primary` at 14%, explicit colours — see
specs/home-and-settings/design.md for why the defaults can't be used):

- `GroupPauseBluetoothLobbyHostScreen` — "Preferisci un codice o un QR?"
- `GroupPauseBluetoothLobbyJoinScreen` — "Scansiona o inserisci un codice",
  in **both** hero states (NFC and search): one screen in two states, not
  two screens.
- `GroupPauseJoinScreen`'s manual-entry card — "Scansiona", below the
  filled "Unisciti" button.

Deliberately left as plain text links: the NFC↔search switch in the join
lobby (it only changes how this same screen looks for a host, and making
both links tonal would put the screen back where it started), and the
full-screen scanner's own links, which are white-on-scrim over the camera
feed and have no theme colour to borrow.

**Dead end found while verifying this.** With the camera permission
denied, `ScanFullScreen`'s pre-permission branch offered only "Concedi"
and "Annulla" — the "enter a code manually" link lived in the *granted*
branch, overlaid on the live preview. Anyone declining the camera was
therefore locked out of a code they could perfectly well type. That
branch now carries the same `group_pause_manual_entry_link` `TextButton`
(a text link, not a tonal one: "Concedi" is already a filled `Button`
there).

## Verification performed (single device)

**Phase 1:**
- Unit tests (`GroupPauseRecipeTest.kt`, no Robolectric): encode→decode
  round-trip, checksum rejects corruption, malformed input rejected,
  already-started recipe rejected, too-far-in-the-future recipe rejected.
- On-device, single adb-connected device: created a code as host (30 min
  duration, 5 min delay), cancelled the host screen to confirm the code is
  self-contained and stateless, fed that exact code into the Join screen's
  manual-entry path, watched the shared countdown tick down, confirmed
  `MainActivity` transitioned to `BlockScreen` with the group indicator
  exactly at zero, unlocked with the test password, and confirmed History
  showed the "Group pause" tag on the resulting row. Also verified the
  Room v1→v2 migration against a previously-populated install.
- Camera scanning was verified for permission request + live preview
  binding only — an actual successful decode via camera could not be
  verified end-to-end without a second physical device holding a real QR
  up to the camera, and is flagged here as code-reviewed rather than
  empirically scan-verified.

**Phase 2:**
- Unit tests (`GroupPauseBluetoothProtocolTest.kt`, no Robolectric):
  `HELLO`/`RECIPE` message format/parse round-trip, unknown-line rejection,
  display-name newline sanitization, and `readLine`/`writeLine` framing
  over `ByteArrayInputStream`/`ByteArrayOutputStream`.
- `./gradlew lintDebug testDebugUnitTest assembleDebug` clean (0 lint
  errors) — Lint's `MissingPermission` check is a real forcing function
  given how many new permission-gated Bluetooth/NFC calls this phase adds.
- **Not verified end-to-end**: an actual two-device Bluetooth
  pairing/lobby, or an actual NFC tap exchange — both need two physical
  devices with working radios, unavailable in this session (only one
  adb-connected device). What *was* checked on that one device, for both
  the host and joiner Bluetooth screens: the pairing-mode pill correctly
  hides the QR-only "starts in" picker and swaps the button label, the
  `RequestMultiplePermissions` dialog appears (bundling all three
  Bluetooth permissions into one system prompt) and is handled correctly,
  and the "enable Bluetooth" system prompt appears and is handled. The
  emulator's own virtual Bluetooth daemon (`com.android.bluetooth`)
  crashed with a native `SIGABRT` while actually turning the radio on — an
  emulator/virtual-radio limitation, not this app's — and both lobby
  screens degraded gracefully: `adapter?.isEnabled` correctly read back
  `false` afterward and the app fell back to its own "Bluetooth is off"
  prompt again, with no crash anywhere in `com.calmotter.app`. NFC reader
  mode / HCE and the live lobby's participant list itself were not
  reachable given the emulator's Bluetooth radio never came up, so those
  remain code-reviewed only, not exercised. Beyond that, this code is
  reviewed, not empirically connection-verified — the same honesty bar
  Phase 1's camera-scan path was already held to.

**UI/UX refinement pass:**
- `./gradlew lintDebug testDebugUnitTest assembleDebug` clean after the
  restructuring (0 lint errors, all unit tests green, including the
  existing `GroupPauseRecipeTest`/`GroupPauseBluetoothProtocolTest` suites
  untouched by this pass).
- On-device, full re-walk of both flattened flows: Home's new
  icon+"Tempo insieme" button → Create/Join dialog (new copy) → duration-
  only setup (with `OtterFloatMark`) → live lobby (dashed ring, gentle
  banner, disabled "Let's go") → "Prefer a code or a QR?" → delay picker
  → generated code + countdown, all screenshotted with no crashes. Then
  the reverse for Join: NFC-unavailable fallback confirmed landing
  directly on the search-hero state (this emulator has no NFC hardware,
  exercising exactly the fallback path the code is meant to handle) →
  "I have a code or a QR" → Scan/Manual pill screen (unchanged) →
  manual-code entry.
- A genuine round-trip re-verification after the rewrite: generated a
  fresh code as host, cancelled out, pasted that exact code into the
  Join screen's manual-entry field, confirmed the shared countdown showed
  the correct duration and ticked down correctly — confirms the
  restructuring didn't silently break the underlying recipe/countdown
  machinery it now reaches by a different navigation path.
- History icon system verified against three seeded rows (solo/completed,
  group/interrupted, group/completed, inserted directly via
  `sqlite3`/cleaned up after) — confirmed the tinted chip color and the
  presence/absence of the signal arcs both render distinctly and
  correctly for all three combinations actually reachable in practice.
- Still not verified: an actual live Bluetooth/NFC connection between two
  devices — same limitation as the initial Phase 2 pass, unchanged by
  this refinement (it touched navigation, copy, and static illustrations,
  not the transport code).


## Redesign pass: the shared dress, not the flow

The Time Together screens keep the flow settled earlier (straight into the
lobby, delay picker on the QR page, full-screen scanner); what changed is the
dress, and all of it is shared with the rest of the app rather than invented
here:

- `SetupLabel` is now the same spaced small-caps used for the section
  headings in Settings — in both places it names the group below it, it is
  not content.
- `MinutePillRow`'s selected pill is filled with `primary`, exactly like the
  duration pills on Home. Same gesture, same shape, in both.
- The Create/Join rows in the chooser gained an icon pastille and a "›". The
  two options have similarly long descriptions and were told apart only by
  reading them; the chevron says that neither one concludes anything. Both
  carried over to the page that replaced the dialog.
- The Zen otter replaces `OtterFloatMark` on every hero here (both lobbies,
  the release step) and in History's empty state, so one face runs through
  the whole app.

The chooser was kept as a **dialog** in this pass, where the mockup makes it
a full page, on the grounds that it is a fork in a flow rather than a place.
That did not hold up and was reversed straight after — see "…and then the
dialog became a page" above.

Not taken: the mockup's "Both otters drift together — timer pauses
simultaneously if either screen…" note, which describes a live sync this
feature deliberately does not have (see "handshake then autonomy" above). A
note that says the opposite of what the code does is worse than no note.
