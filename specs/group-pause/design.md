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
| `ui/screens/GroupPauseCountdownScreen.kt` | Shared by both flows — ticks "starting in mm:ss", calls back once at zero, shows `OtterZenMark` above whatever `header` the caller supplies. |
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

### A tap opened Android's own "which app" dialog instead of connecting

Reported: "quando i due telefoni si tappano, esce un dialog di sistema
per indicare l'app da usare in nfc" — the host side, since it's the one
running `GroupPauseHceService`. `apduservice.xml` declares
`android:category="other"` (correctly — this is not a payment service),
but for that category Android only routes a detected AID straight to a
service without asking if that service has explicitly told the system
"route to me while I'm in the foreground" via
`CardEmulation.setPreferredService()`. Nothing in this codebase ever
called it — the host lobby just set `pendingMarker` and otherwise trusted
the manifest's `<intent-filter>` to be enough, which it is not for
`category="other"`. Without a registered preferred service, any tap with
more than one possible handler for the AID (this app plus, on some
OEMs/Android versions, a system or pre-installed handler) shows the
resolver dialog on every single tap instead of connecting silently.

Fixed in `GroupPauseBluetoothLobbyHostScreen.kt`'s same `DisposableEffect`
that already sets/clears `pendingMarker`:
`CardEmulation.getInstance(nfcAdapter).setPreferredService(activity, ComponentName(...))`
when the lobby becomes ready, `unsetPreferredService(activity)` on
dispose — both wrapped in `runCatching`, matching this file's existing
defensive style for NFC calls (`GroupPauseNfcReader.stop()`). Both calls
need the hosting `Activity`, not just any `Context` — same assumption
`GroupPauseBluetoothLobbyJoinScreen.kt`/`BlockScreen.kt` already make
about where these composables live.

Verified as far as a single physical device allows: on a Galaxy S22
(the device the report came from), reaching the lobby's "ready" state —
which is exactly when `setPreferredService` now runs — produced no crash
and no exception in `adb logcat`. The actual "no more resolver dialog on
tap" behavior needs a second NFC-capable phone to tap against to confirm
end-to-end, which wasn't available; flagged rather than claimed as fully
verified.

### After a tap, the session starts on its own — no manual "Iniziamo" needed

Requested: "l'nfc è pensato per quando la pausa è 2, quindi dopo il tap la
sessione deve partire subito" — NFC pairing only makes sense between two
phones physically touched together, so once that's happened there's no
scenario where the host still needs to tap "Iniziamo" by hand; the tap
itself is the intent to start.

The tricky part is that "the tap happened" and "it's safe to start" are two
different moments. `GroupPauseHceService.processCommandApdu()` fires the
instant the joining phone reads the marker — but
`GroupPauseBluetoothHost.broadcastRecipeAndClose()` only delivers the
recipe to sockets already present in its connection list; the Bluetooth
connection + HELLO handshake that populates `participantNames` completes a
few seconds *after* the NFC exchange, not before it. Starting on the tap
alone would broadcast to an empty connection list and close the host
before the joiner ever connects.

Fixed with two signals combined, not one:

- `GroupPauseHceService` gained a `@Volatile var onTapRead: (() -> Unit)?`
  companion callback, invoked from `processCommandApdu()` right next to the
  existing `pendingMarker` read — same lifecycle as `pendingMarker` itself
  (set when the lobby becomes ready, cleared on dispose). Runs on the
  system's NFC/Binder thread, so the registered callback in
  `GroupPauseBluetoothLobbyHostScreen.kt` posts to the main thread via a
  plain `Handler(Looper.getMainLooper())` before touching Compose state —
  the same pattern `GroupPauseBluetoothLobbyJoinScreen` already uses for
  `GroupPauseNfcReader`'s callback.
- The screen tracks `nfcTapDetected` as local state, and the "Iniziamo"
  logic was extracted out of the button's `onClick` into a plain
  `startSession()` function so both triggers — the manual button and the
  new automatic path — share the exact same code instead of drifting apart.
  A `LaunchedEffect(nfcTapDetected, participantNames.size)` starts the
  session only once *both* conditions hold: a tap was read *and*
  `participantNames` is non-empty (i.e., someone has actually connected),
  resetting `nfcTapDetected` back to `false` immediately after firing so a
  later, unrelated change to `participantNames` (a second tap, a third
  person joining) doesn't retrigger an auto-start.

This still doesn't skip the 5-second `startAtEpochMillis` grace period
already baked into `GroupPauseRecipe` — the actual pause begins the same
way it always did, just without the host needing to notice and tap a
button first.

Verified: `startSession()`'s extraction is a pure refactor (same recipe
construction, same `broadcastRecipeAndClose`/`onRecipeReady` calls,
unchanged), checked by reading the diff rather than re-testing the manual
"Iniziamo" path from scratch. The automatic trigger itself has the same
verification limit as the resolver-dialog fix above: no second NFC-capable
device was available to physically tap and confirm the session actually
starts end-to-end. What was checked was code-level: `onTapRead` is
registered/cleared in the same `DisposableEffect(allReady)` block, on the
same lifecycle, as `pendingMarker` and `setPreferredService`.

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

## The QR page, on its own report

Reported directly against the mockup ("Time Together - QR & Code",
Stitch project `3158702940609906617`), after the card-removal and
lobby-ring passes above had already covered the rest of this flow.
Comparing against the mockup line by line surfaced one real gap and three
things deliberately **not** taken, which matter more than the one thing
that was:

- **No mascot, anywhere on the page.** Every other screen in Tempo
  Insieme has an otter as its visual anchor — the chooser's
  `TandemPawsMedallion`, the lobby's `ParticipantRing`, the join lobby's
  `OtterTapMark`/`SearchingIllustration`. This was the one screen with
  none: just headline, QR, code, delay pills, countdown, Cancel. Fixed by
  adding `OtterZenMark(88dp)` — the same size the join lobby already uses
  for its own `Connecting`/`WaitingForHost` states — but **inside
  `GroupPauseCountdownScreen`, not inside the QR page's own `header`**.
  The same emptiness was there in the *other* path through this exact
  composable too (the countdown reached after a live Bluetooth lobby,
  `header = {}`, nothing above "Starting in…" at all) — a shared
  component is worth the same care everywhere it's used, not just where
  someone happened to look.
- **A copy action next to the room code.** The mockup has a
  `content_copy` icon; dictating a code aloud or retyping it by hand is
  the exact friction a copy button removes, and it's the one piece of
  this page that was a genuine, unambiguous gap rather than a stylistic
  difference. `GroupPauseShareHeader` gained a `TextButton("Copy")` next
  to the code, using `LocalClipboardManager` + a confirming `Toast` — the
  same feedback pattern `session_started` already uses elsewhere in this
  app.

  First shipped as text only, on the reasoning that a rarely-used action
  next to a label that already says what it does doesn't earn the weight
  of a drawn icon. Asked directly afterwards ("copia ha un'icona?"), and
  the honest answer was no — reasoning aside, the mockup's button *has*
  one and this one didn't. `Icons.Filled.ContentCopy` doesn't exist in
  `material-icons-core` (confirmed by trying it: the build fails,
  `Unresolved reference`), only in `material-icons-extended`, which this
  app deliberately doesn't depend on. So `CopyGlyph`: two overlapping
  rounded rectangles drawn with `drawRoundRect`, the same "draw it
  yourself rather than pull in the extended artifact" call already made
  for `EyeGlyph` — except simpler, since two rectangles don't need
  `PathParser`'s official path data the way an eye did.

Not taken, each for a specific reason rather than an oversight:

- **The mockup's "Start now" button.** In this architecture the host and
  joiner each count down locally to the *same* `startAtEpochMillis`
  baked into the shared code/QR at generation time — that shared instant
  is the entire mechanism that keeps two independent devices in sync
  (see "handshake then autonomy"). A button that skips the host's own
  wait would only shorten *the host's* countdown; a joiner who already
  has the original code would still be counting down to the original
  time, coming apart from a host who no longer is. The spec already
  accepts a narrower version of this same risk for someone who *changes*
  the delay after a joiner has scanned — but that is a rare edit,
  disclosed as a known edge; a permanent "start now" button would make
  the desync the common case instead of the edge one, every time it's
  used. Skipped, not forgotten.
- **A paw watermark in the middle of the QR.** Doable, but only safely
  with a higher error-correction level than the `M` this app generates
  today (`QrCodeGenerator.kt`) and real verification that a second
  camera can still read the result with a logo occluding its centre —
  exactly the kind of claim `QrCodeGeneratorTest.kt` exists to check
  mechanically rather than eyeball, and this session had no second
  device to confirm it against. Left alone rather than shipped unverified.
- **A live "N friends waiting" readout**, which the mockup shows under
  the code. This architecture has no channel back to the host once the
  code/QR is generated — no connection exists to report anyone waiting on
  (again, "handshake then autonomy"). Showing it would mean inventing
  state this screen cannot actually know.

Verified on-device: the otter appears above the QR share header, "Copy"
places the code on the clipboard (confirmed via the system's clipboard
preview chip) and a toast confirms it.

### The card came back, on request — and the two-line countdown was reversed too

A follow-up pass, all four points reported directly against the same
mockup after living with the first pass above for a day:

- **The QR is back inside a card.** Removing the white square in light
  theme (see "QR colours" above) was the point of an earlier change, and
  that reasoning — a pasted-on rectangle clashed with the app's soft
  palettes — still describes what was wrong with a *plain, undecorated*
  square. A card with rounded corners, sitting flush with the rest of
  this redesign's visual language, is a different thing, and was asked
  for on those terms rather than "put the rectangle back." `Surface(shape
  = RoundedCornerShape(20.dp), color = surfaceBright, shadowElevation =
  2.dp)` now wraps the `Image`. Nothing about `generateQrCodeBitmap`'s
  call — `moduleColor`, `fieldColor`, the light/dark branching that feeds
  it — changed; only what sits *behind* the bitmap did, from
  `calmBackground`'s gradient to `surfaceBright`, which is lighter in
  both themes than the gradient's darkest point that
  `QrCodeGeneratorTest.kt` already measures as its worst case. So the
  existing contrast numbers in that section remain a valid floor, and the
  test needed no changes — confirmed by running it, not assumed. Checked
  by hand in both themes on-device: the dark-theme QR still carries its
  own light backing (`fieldColor` inside the bitmap call, unchanged) which
  now sits *inside* the new outer card instead of being the only light
  surface around the code; the two don't visibly seam.
- **Code and "Copy" now share one pill**, `Surface(shape =
  RoundedCornerShape(50), color = primary @ 8%)`, instead of a bare `Row`
  with a button hanging off the end of some plain text. Same 8% used for
  unselected duration chips elsewhere in this exact screen — a container
  tint, not a choice being offered.
- **The countdown and duration are one line again**, reversed from the
  two-line layout `specs/group-pause/design.md`'s own "Home ran out of
  vertical room" precedent had favoured — asked directly this time
  ("come da design di Stitch, che dici?"), rather than inferred from the
  mockup the way the first QR-page pass tried and back out of. The two
  facts ("fra quanto" and "per quanto") now sit in one
  `group_pause_countdown_with_duration` pill (`RoundedCornerShape(16.dp)`,
  `primary` @ 8%) with a small dot in front.
- **The dot pulses** — the one perpetual animation in this file, and
  deliberately so where every other screen in this app has gone the
  opposite way (Home's `PondStill`, the chooser's `TandemPawsMedallion`,
  the lobby's `ParticipantRing`, all static on purpose, all documented
  with the same "no unbounded animation next to an unmade decision"
  reasoning). It's allowed here because neither half of that reasoning
  applies: this screen cannot stay open indefinitely — the countdown it's
  attached to closes it within minutes by construction — and nothing on
  it is a choice to be pulled away from; it's a passive wait, and the
  pulse says "this is live" about exactly that.

Two old strings, `group_pause_countdown_label` and
`group_pause_duration_reminder`, are gone rather than left beside the new
combined one — same reasoning as the `notready_*` strings above.

### "Starts in" and "Starting in" echoed each other

Reported as "un po' confusionaria": `group_pause_start_in_label` ("Starts
in", the label above the 1m/2m/5m delay picker) sat one line above
`group_pause_countdown_with_duration` ("Starting in 1:59 · Duration…", the
live pill), same words twice in a row — a static setting label and a
ticking readout that happened to open with the same phrase, read as one
duplicated countdown rather than "pick a delay" + "here's how much of it
is left". `group_pause_start_in_label` stays as-is (on its own, above a
1m/2m/5m picker, "Starts in" reads fine); only the live pill's wording
changed, English "Starting in" → "Begins in", Italian "Si inizia tra" →
"Parte tra" — the label keeps naming the setting, the pill stops
paraphrasing it.

**Follow-up, same "confusionaria" report, after Cancel was already fixed
to be the page's bottom CTA (see below):** the wording clash above wasn't
the whole story — the QR page still read as one flat list of four
same-weight blocks (mascot+hint, QR+code, delay picker, countdown pill),
with nothing separating "how to join" from "when it starts". Asked
directly rather than guessed at: more spacing, not a divider or a card.
`SetupLabel`'s `topPadding` above `group_pause_start_in_label` went from
20.dp (its usual value, still 20.dp everywhere else `SetupLabel` is used,
e.g. the lobby host's duration picker) to 40.dp, only in
`GroupPauseHostScreen`'s QR-share `header` block.

**Third follow-up, same report, this time reversing the earlier "one pill
below the picker" decision:** "non si può unire la frase 'have this' e
begins/duration sopra il qrcode? così semplifichiamo?" — merge the static
invite text and the live countdown into one sentence, above the QR instead
of the countdown living in a separate line below the delay picker.
Implemented rather than just discussed, since the reasoning was sound and
concrete: `group_pause_share_hint` ("Have this QR scanned, or share the
code below") and `group_pause_countdown_with_duration` ("Begins in
X:XX · Duration: Y min") became one string,
`group_pause_share_hint_with_countdown` ("...share the code below — begins
in %1$d:%2$02d, duration %3$d min"), rendered by `GroupPauseShareHeader`
above the QR card. `group_pause_share_hint` is gone (no other caller left
after this).

This needed `GroupPauseCountdownScreen`'s `header` slot to actually receive
the ticking `minutes`/`seconds` (it only took `durationMinutes` as a
sibling parameter before, not into the slot itself) — `header` is now
`@Composable (minutes: Int, seconds: Int) -> Unit`, and a new
`showCountdownLine: Boolean = true` parameter lets a caller whose `header`
already shows the merged text suppress the separate countdown line, so it
isn't shown twice. The QR-share caller sets `showCountdownLine = false`;
the other two callers (`GroupPauseHostScreen`'s post-live-lobby countdown
and `GroupPauseJoinScreen`'s, both with an empty `header`) don't pass
either parameter, so they keep the original separate pulsing-dot line
unchanged, by default rather than by any code of theirs having to know
about the split. The merged text has no pulsing dot of its own — one
reads fine inline in a short status line, awkward inline in a full
sentence — the ticking numbers already say "this is live" on their own
there.

**Second follow-up, same report, "per alleggerire":** the countdown pill
itself lost its `Surface` — asked directly ("forse togliereste il
box/pillow da Begins...?") rather than inferred. With the QR's white card,
the code+copy pill, and the 1m/2m/5m picker's own pills, the countdown
line was a fourth boxed element in a row; now it's a plain `Row` (pulsing
dot + text, no `RoundedCornerShape`/tinted `Surface` background) sitting
directly on the page background, same as the hint text above it. The dot
still does the "this is live" signalling on its own — nothing else in
this file used `Surface`/`RoundedCornerShape` after this, so both imports
came out too.

### The pulse was implemented wrong the first time, and it was not free

Reported directly the next day: "sulla schermata di qrcode e quella di
lock la cpu frulla" — the fan-spinning kind of report, the same shape as
Home's ripple investigation earlier in this project. The QR/countdown
screen was new that same session; the block screen ("lock") was not, and
measured clean (see below), which narrows this to one thing: the pulsing
dot just added, and its shared host `GroupPauseCountdownScreen` is what
both a QR-page visit and a countdown reached from a completed live lobby
have in common — "lock" most likely meant the second of those, the
screen right before the actual block screen, not the block screen itself.

Measured on the emulator with the same method already established for
this app's other animation work (`/proc/<pid>/stat` kernel-time deltas,
warm process, screen awake, 8s samples): **40-60% of one core**, steady,
not settling — and confirmed as *this specific animation's* cost, not
something else on the same screen, by disabling only the dot (`val pulse
= 1f`, nothing else touched) and re-measuring: **0%**. The block screen,
measured the same way both freshly entered (the otter bob's active 30s
window) and well after, read **0%** throughout — consistent with the bob
being nearly free at any step rate because it's read inside a
`graphicsLayer` transform rather than redrawn (see "How fast the scene
steps" in `specs/home-and-settings/design.md`), and confirming the block
screen itself was never the problem.

The cause: the pulse was built with `rememberInfiniteTransition().
animateFloat(...)`, which samples on **every display frame** — 60 to
120Hz depending on the device — with none of the step-rate throttling
this app applies everywhere else it animates something forever
(`steppedFraction` in `MainScreen.kt`; see "How fast the scene steps" for
the measurements that established *why* — cost is roughly linear in
redraws, independent of whether the read itself triggers recomposition).
Reading `pulse` inside `graphicsLayer { alpha = pulse }` was already
correct — a draw-phase read, not a composition-phase one, the other half
of that lesson — so the bug was purely the animation's *source*, not
where its value got consumed. Rewritten as `rememberPulseAlpha()`, a
`withInfiniteAnimationFrameMillis` + `delay` stepper local to this file
(same idiom as `steppedFraction`, not shared with it — a pulsing dot
doesn't need that function's generality, just its principle), at 10 steps
per second on a 1.8s triangle wave. Re-measured after the fix: **0%**,
six consecutive 8s samples.

The lesson this confirms rather than introduces: every *other* perpetual
animation in this app was written with the step-rate discipline already
in place, because each one followed the ripple investigation that
established it. This one was written after that investigation, in the
same session as the reasoning paragraph above defending why a perpetual
animation was fine here at all — and the reasoning about *whether* to
animate was sound, but writing the animation itself skipped the *how*.
Being allowed to animate forever was never permission to sample forever.

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

### …and then the title/subtitle swap was reversed

Reported directly against the redesigned host lobby ("le label non sono
corrette, il flow è cambiato"): once the gentle banner became the
permanent, always-available way to say *what's* missing and *how* to fix
it (this same section, above), the hero text swapping to "Quasi pronti" /
"Ancora un passaggio…" started saying the same thing a second time, in a
vaguer way, right above the banner that already said it precisely. That
duplication is exactly the pattern this app avoids everywhere else (the
Home CTA rewrite, the history empty state, the chooser page) — it had
just been reintroduced here in the act of fixing a different, real
problem (the illustrations "miming activity" that wasn't happening).

The fix this time: **the hero text stops depending on readiness at all.**
Title and subtitle always show the real waiting/participant state
(`lobbyTitleFor`, `group_pause_lobby_waiting_subtitle`/`_ready_subtitle`),
exactly as the Stitch mockup has them — its "Waiting for someone…" has no
missing-permissions variant, because the mockup doesn't model that state
in copy at all, only in whatever inline affordance a real app would add
(here, the banner). `group_pause_notready_title`,
`group_pause_notready_subtitle` and `group_pause_notready_subtitle_join`
are deleted rather than left orphaned, same convention as elsewhere in
this codebase.

What stays from the section above, unreversed: the illustrations still
only animate/mime activity that is really happening (`ParticipantRing`'s
dashed ring, `SearchingIllustration`'s radar) — that part of "honest lobby
state" was never about the text and holds regardless.

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

**This reasoning held while `MinutePillRow` only ever saw short lists**
(the host lobby's original fixed `[15, 30, 60, 90, 120]`, later the QR
share screen's 3-value delay picker). It stopped holding for the host
lobby once its own duration list was widened to Home's full 8 values
(`30..240` step 30, see "handshake-then-autonomy" above) — at that length
it wraps to two rows regardless, so "all values visible at once" was no
longer actually true, and the row now looked and behaved differently from
the *identical* list on Home one screen away. Reported directly ("la
duration, preferi pillole come quelle della home, scorrevoli"): the host
lobby's picker now uses `ScrollableMinutePillRow` (`GroupPauseHostScreen.kt`),
copying Home's own `DurationChipRow` treatment — a horizontally scrolling
`Row`, selected pill filled `primary`, others `tertiary` at 55%. `MinutePillRow`
itself is untouched and keeps wrapping for its one remaining caller, the
QR share screen's 3-option delay picker, where the original reasoning
still applies unmodified.

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

### Getting in requires the same permissions as any pause

Entering the flow is gated on Home, on the "Tempo insieme" button, with the
same accessibility+DND check as the otter tap — see
`specs/home-and-settings/design.md`. Nothing inside this flow checks them:
both Activities here call `startSession()` directly, and the only
permissions these screens know about are the Bluetooth ones. That is by
design now, but it means the Home gate is the *only* one, and anything that
later opens Tempo Insieme from somewhere else has to carry it too.

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
- A `TandemPawsMedallion` hero: Home's still pond, 224dp, with
  `TogetherMark` inside. Five layers taken from the mockup one by one
  (container 224, `inset-0/3/7/11`, then the 96dp centre disc) rather than
  approximated — an outline ring, a **dashed** ring just inside it, a
  veiled disc, a pale disc, and the bright centre the prints sit on.

  It does not reuse `PondStill`: that one is full-page and anchored to a
  centre shared with `BlockScreen`, an invariant with its own file
  (`OtterAnchoredScreen.kt`) that has no reason to be dragged in here. It
  does reuse its *colours* — `tertiary` for the discs, `surfaceBright` for
  the bright one — so that it reads as the same pond despite being drawn in
  a different file. The two outline rings are the exception: they take
  `primary` at low alpha, because the mockup's are a mid-tone while our
  `tertiary` is already pale (#d5e0d5 in the green palettes) and a hairline
  in it would simply not be visible on this background.

  `TogetherMark` is given **96dp, the disc's own diameter**. The mark
  carries internal margin — its drawing fills 57% of its box — so at 96 the
  ink covers about half the disc, which is the mockup's proportion. At 44
  and then 56 it covered a third and floated in the middle of the white.
  Measured off the screenshot rather than judged by eye: ink 143px on a
  286px disc, centred to within a pixel horizontally.

  **Still, not breathing.** The mockup pulses the outer ring on a 4s loop.
  Moving water is Home's, where it means "the pond is waiting, tap the
  otter"; this is a screen where you choose between two things, and a
  perpetual animation beside two options pulls the eye off them. The cost
  argument from `specs/home-and-settings/design.md` applies too, with the
  aggravation that here there would be no moment at which to stop it.
- The closing line, which says why the screen exists rather than asking for
  anything.

Two departures from the mockup:

- **No bottom navigation bar**, as everywhere else in the redesign.
  Consequence worth stating: the mockup's lower half was that bar, so with
  it gone the content would sit high with half a screen of nothing below.

  This took four passes. Centring the whole block in what remained bunched
  everything at mid-height with an empty band above and another below.
  Distributing all four blocks over the height fixed the bands but pushed
  apart things that belong together — the heading ended up far from the two
  paths it introduces. Distributing three, the first zero-height, centred
  the medallion but let the heading fall wherever it landed.

  What ships stops distributing space and **measures the page** instead,
  because the two requests are one measurement:

  ```
  BoxWithConstraints(Modifier.fillMaxSize())   // ← before safeDrawingPadding
      val halfPage = maxHeight / 2
      …
      Box(height = halfPage - topInset - TopBarHeight) { TandemPawsMedallion() }
      Column { heading; subtitle; the two cards; closing line }
  ```

  The box ends at the page's midline, so the heading starts exactly there;
  the medallion is centred inside that box, so it lands halfway between the
  top bar and the heading. Neither can drift when the other changes.

  **The measurement is taken outside `safeDrawingPadding`, deliberately.**
  Half the page is half the *screen*, not half of what is left under the
  bar: measured inside, the heading came out at 54% rather than 50% — read
  off the accessibility hierarchy (`uiautomator dump`, which gives exact
  bounds, after pixel-scanning screenshots had twice mistaken the paw mark
  for the title). Those four points are exactly the status inset plus
  `TopBarHeight`, which is why both are subtracted.

  The rest is one packed child with its own small rhythm: cards 20dp under
  the subtitle they belong to, closing line 20dp under the cards — near,
  but detached enough not to read as a third option.

  `coerceAtLeast(MedallionSize)` guards the short-screen case: where the
  midline would fall above the medallion's own height, the heading drops
  below the middle rather than the paws being clipped.

  The distributed space only exists while the content fits; when it stops
  fitting (enlarged system font) the blocks pack together and
  `verticalScroll` scrolls them rather than clipping.
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

- `GroupPauseBluetoothLobbyJoinScreen` — "Scansiona o inserisci un codice",
  in **both** hero states (NFC and search): one screen in two states, not
  two screens.
- `GroupPauseJoinScreen`'s manual-entry card — "Scansiona", below the
  filled "Unisciti" button.

`GroupPauseBluetoothLobbyHostScreen`'s "Preferisci un codice o un QR?"
was moved back to a bare link, reversing the original link → tonal
change documented above: the tonal container, meant as this screen's one
sanctioned exception ("l'unica via d'uscita dalla lobby... la sola CTA
secondaria di questa schermata a meritare il contenitore tinto"), ended
up reading as distracting rather than as a clear way out, on direct
feedback while looking at the screen. It now uses the same bare-link
treatment as `SessionsSummaryLink` in `HomeSummary.kt` (primary-coloured
text + "›", `clip(RoundedCornerShape(50))` + `clickable` +
`heightIn(min = 48.dp)`, no shared composable — the two link instances
don't share enough to be worth extracting one). Verified on-device
(Pixel 10 Pro AVD, stable flavour): the link now sits under the duration
picker and reads calmly against the "Cancel"/"Let's go" pair and the
Bluetooth-permission banner below it, without the tonal pill drawing the
eye away from "Let's go". If it goes back to getting lost among the
surrounding text — the original reason it became a tonal button — that's
the one open risk with this reversal; there's no on-device count of how
many other CTAs are visible above which it turns into the other
failure mode, so this is a judgment call revisited on user feedback, not
a proven threshold.

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

## The card comes off, the otter gets its pond

A further pass, reported against the host lobby directly (mockup: "Time
Together - Host Lobby", Stitch project `3158702940609906617`):

**`CalmCard` is gone from every Tempo Insieme screen** — both lobbies,
the countdown, and the QR/manual-code join screen. It was a tinted
`Surface(shape = RoundedCornerShape(20.dp), color = primary @ 6%)`
wrapping each screen's content, extracted specifically for this flow (see
its own doc comment in `CalmBackground.kt`) when the flow's screens still
needed a visual boundary of their own. By the time this pass landed, the
rest of the app — Home, the chooser page that starts this same flow — had
already settled on a flat background with no card, and the lobby's own
card now read as the odd one out one screen into the same journey rather
than as a deliberate boundary. `CalmScreenColumn` (the safe-area +
scroll + centring container) stays under every one of these screens
unchanged; only the tinted `Surface` and its extra 20dp of inner padding
are gone, so content now sits at `CalmScreenColumn`'s own 32dp margin
instead of 52dp.

`CalmCard` itself is **not** removed: `HistoryScreen.kt`'s empty state
still wraps its closing quoted phrase in one, and that use was never part
of this report or this flow. Caught by the compiler, not by reading first
— an initial pass deleted the composable on the assumption that Tempo
Insieme was its only caller, which a `grep` earlier in the same session
had actually already shown to be false (`HistoryScreen.kt:520` was right
there in the list); trust the build over a remembered grep. `CalmCard`'s
own doc comment now says explicitly that it has one caller left instead
of five, so the next person deciding whether it's safe to fold away
doesn't have to re-derive this.

**`ParticipantRing` gets a pond.** Reported plainly: the otter didn't
read as "circondato dai cerchi" the way the mockup's hero does — the
mockup layers an outline ring, a dashed rotating orbit, a blurred glow and
a bright core disc around its otter; this screen had exactly one thin
ring. The fix draws the same three filled discs `PondStill` (Home) and
`TandemPawsMedallion` (the chooser page) already draw — veiled, pale,
bright — in the same two colours, `tertiary` for the tinted discs and
`surfaceBright` for the bright one, so the otter reads as sitting in the
same pond in all three places despite being drawn by three different
composables. Radii are picked so the bright disc's diameter matches
`OtterZenMark`'s own 110dp, the same fit `TandemPawsMedallion` uses for
its own mark.

What the mockup's version does that this one deliberately doesn't:
animate. Its outer ring pulses (`animate-ping`) and its dashed ring spins
(`animate-spin`, 60s). Both `PondStill` and `TandemPawsMedallion` already
made the opposite call and documented why — a perpetual animation next to
something the person is about to decide on pulls the eye off the decision,
and Home's own ripple work
(`specs/home-and-settings/design.md`, "How fast the scene steps") measured
what animating an otter's surroundings actually costs. This screen can sit
open indefinitely waiting for someone to appear, which is the clearest
case yet for "no perpetual animation": there would be no `SCENE_QUIET_AFTER_MILLIS`
moment at which to ever stop it. The discs are therefore static; the
ring's own dashed/solid/faint states (which already existed, and already
carry the "is anyone listening" signal without motion) are untouched.

Verified on-device, host side: card gone (flat background, same as the
chooser page one screen back), the three ring/disc layers visible behind
the otter in both the ready and not-ready states, duration scrolling
through all eight values, and the permission banner sitting below
Cancel/"Let's go" rather than between the duration picker and the "prefer
a code" link. The join lobby got the same card removal and the same
title/subtitle fix (see above) for consistency, since it shares the exact
code pattern; its own hero illustrations (`OtterTapMark`,
`SearchingIllustration`) are a different visual language from
`ParticipantRing` and were not restyled — the report was specifically
about the host lobby's ring.


## The join flow: three reports, one shared bug among them

**The camera permission popup fires on entry, not behind a "Grant"
tap.** `ScanFullScreen` used to show a rationale screen with a "Grant"
button on first arrival — a screen to read and a button to press before
the actual system dialog appeared, even though the camera is the whole
point of this step. Reported directly ("preferisci il popup di
permesso"): a `LaunchedEffect(Unit)` now launches the permission request
the moment this screen is entered, without waiting for a tap. `Unit` as
the key rather than nothing, deliberately: it fires once per *entry* into
this composable, not once per recomposition, so a denial doesn't loop
into repeated prompts — a fresh ask only happens on a fresh visit, from
the live lobby's "Scansiona o inserisci un codice" link. The rationale
screen stays exactly as it was, now reached only after a denial, with its
"Grant" button and its manual-entry fallback link both still there.

**Cancel from the QR/code screen returns to the live lobby, not out of
the join flow.** `JoinFlowStep.QrOrCode` is a fallback reached from a
link inside the live lobby ("Scansiona o inserisci un codice"), not a
step of equal standing — but its `onCancel` was wired straight to
`GroupPauseJoinScreen`'s own top-level `onCancel`, the same one the live
lobby's own Cancel uses to leave the whole flow. Reported directly ("il
cancel di scan qr deve tornare alla pagina di look"): fixed by setting
`step = JoinFlowStep.Live` instead, so leaving the fallback returns to
where the fallback was reached from. This covers both of
`GroupPauseCodeEntryScreen`'s modes (`ScanFullScreen` and the manual-code
tab) since they share the one `onCancel` parameter — asked about the
scanner specifically, but the manual tab is reached from the exact same
link and deserves the same way back, not a different one depending on
which of the two sub-modes happens to be showing.

**"Looking for a friend…" was the pulse bug's second occurrence, not a
new one.** Same shape as the QR/countdown pulse fixed the day before (see
"The pulse was implemented wrong the first time" in this file):
`SearchingIllustration`'s three expanding rings were driven by
`rememberInfiniteTransition().animateFloat(...)`, sampled on every
display frame indefinitely while a live search runs — which, unlike the
countdown's bounded few minutes, can be as long as nobody is found.
Worse than the pulse in one respect: `val t by transition.animateFloat(
...)` was read directly in the composable's own body, not inside a
`graphicsLayer` draw-phase lambda, so every step recomposed the whole
`Canvas` that redraws three rings over a 140dp area — not one 8dp dot.
Measured the same way, proven by reverting to the broken version and
re-measuring rather than trusting the fix on inspection alone: **40–60%
of one core** with `rememberInfiniteTransition`, **0%** with
`rememberSearchPulse()`, the same `withInfiniteAnimationFrameMillis` +
`delay` stepper at 10 steps/second already established for this exact
failure mode. No settle-after-N-seconds here, unlike the countdown pulse
— the search genuinely can run for as long as the lobby is open — but the
per-step cost is what dropped, not the duration.

Verified on-device: the system permission dialog appears immediately on
tapping "Scansiona o inserisci un codice" (camera permission revoked via
`pm revoke` first, to force the ask); denying it lands on the rationale
fallback as before; the always-visible Cancel in the scan screen's top
bar returns to "Look for whoever's waiting for you", not Home; and the
searching animation's CPU cost confirmed both ways, broken and fixed, on
the same build.


## The host lobby's Cancel/"Iniziamo" is a `Column` now, not a `Row`

On request ("tutti i bottoni, posso essere a tutta larghezza ed uno sotto
l'altro?"): full-width `Cancel`/`Iniziamo` stacked instead of side by side.
Neither the enable condition (`participantNames.isNotEmpty()`) nor the
click handlers changed, only the layout axis. The three Material3-dialog
Cancel/Confirm pairs elsewhere in the app (password unlock, weekly goal,
clear-history confirm) were explicitly left as-is — not part of this
request, and stacking them needs a different approach (both buttons inside
the `confirmButton` slot, `dismissButton` left empty) since `AlertDialog`
lays its own action slots out horizontally.

Two follow-up requests refined this same change:

- **The order flipped.** First pass put `Iniziamo` on top ("positive above
  negative"); asked directly afterward, reversed to **`Cancel` on top,
  `Iniziamo` last** — the primary action is the bottom-most button, not the
  first.
- **Anchored to the page bottom**, not just stacked wherever they fell in
  the content flow. `CalmScreenColumn` (`CalmBackground.kt`) defaults to
  `Arrangement.Center` — everything passed to it, buttons included, was
  centred as one group within the whole scrollable page, which on a short
  lobby (no participants yet, `!allReady` banner hidden) left the buttons
  floating mid-screen rather than docked at the bottom. Fixed by wrapping
  everything *except* the button `Column` (and the `GentleBanner` that
  sits below it, unchanged) in its own inner `Column` with `weight(1f)`
  and that same `Arrangement.Center` — so the ring/title/duration/link
  group stays centred *within the space above the buttons*, while the
  buttons (and the banner beneath them) are the last, non-weighted
  siblings pinned to whatever's left at the bottom.
- **CTA height made explicit: `.height(48.dp)` on both buttons**, for the
  same reason and with the same fix detailed in
  `specs/onboarding-and-password/design.md` ("CTA height made explicit")
  — default M3 button height measured 40dp on the emulator, below the
  48dp minimum touch target; the gap between the two stacked buttons is
  `Arrangement.spacedBy(8.dp)` on their `Column`, not `.padding(top = 8.dp)`
  chained after `.height(48.dp)` on the second one, which silently ate
  into the fixed height instead of adding space above it.

## `GroupPauseCountdownScreen`'s Cancel is the page's bottom CTA now

Same fix as the host lobby above, one screen later: reported "la pagina
del qrcode è un po' confusionaria... il cancel deve essere la CTA a fondo
pagina". `GroupPauseCountdownScreen` — shared by both the QR-share header
(`header` slot full: mascot, QR, code pill, delay picker) and the plain
post-live-lobby countdown (`header` empty) — had the exact same
`CalmScreenColumn` default-`Arrangement.Center` problem: `Cancel` was a
small, centred `OutlinedButton` sitting wherever it fell after whatever
`header()` contributed, not anchored to the bottom, and not full-width.

Fixed with the identical restructuring: everything `header()` plus the
countdown pill contributes now lives in an inner `Column` with `weight(1f)`
and `Arrangement.Center`, `Cancel` is the last, non-weighted sibling —
`.fillMaxWidth().height(48.dp)`, same 48dp standard as every other CTA in
this pass (`specs/onboarding-and-password/design.md`, "CTA height made
explicit"). `.padding(top = 24.dp)` on `Cancel` had to go *before*
`.height(48.dp)` in the modifier chain, not after — the same ordering trap
caught twice already in the sibling screens, applied correctly here from
the start this time rather than re-discovered.

Because this composable is shared, the fix applies to both callers
(`GroupPauseQrShareScreen`'s full header and the bare post-lobby
countdown) without touching either caller — verified on the emulator via
the QR-share path, the one with the most content above the button and
therefore the one most likely to have exposed a regression if the
weighted-Column restructuring had been wrong.


## The whole join flow, audited: three more screens with the same bug

Requested: "in join il bottone deve essere in fono pagina. controlla
tutte le CTA button, grazie" — an audit, not a single fix. The three
already-fixed screens (host lobby, countdown/QR) shared one root cause;
the audit confirmed the join side of the same flow had it too, in three
places, plus one unrelated screen elsewhere in the app.

- **`GroupPauseBluetoothLobbyJoinScreen`** — the join lobby's own `Cancel`,
  same exact shape as the host lobby's before its fix: `CalmScreenColumn`'s
  default `Arrangement.Center` centred the whole `when(state) { ... }`
  block plus `Cancel` as one group, not anchored to the bottom on short
  states (e.g. `NfcHero` with nothing but the mascot and a title). Fixed
  identically: the per-state content moved into an inner `Column` with
  `weight(1f)` + `Arrangement.Center`, `Cancel` is the last non-weighted
  sibling, `.fillMaxWidth().height(48.dp)`.
- **`GroupPauseJoinScreen`'s `GroupPauseCodeEntryScreen`, `JoinMode.MANUAL`
  branch** — three buttons (the code field's own "Continua", "Scansiona
  invece", `Cancel`), same problem. Title + the manual-code field/error
  moved into the same weighted+centred inner `Column`; "Scansiona invece"
  (`CalmSecondaryButton`) and `Cancel` are the trailing anchored pair, both
  `.fillMaxWidth().height(48.dp)` now — "Scansiona invece" was wrap-content
  before, inconsistent with `Cancel` sitting right below it.
- **`GroupPauseJoinScreen`'s `ScanFullScreen`, no-camera-permission
  branch** — lower priority in the audit (a short, static rationale
  screen, not part of the "join" complaint specifically) but fixed anyway
  since the ask was "check all CTA buttons": the rationale text moved into
  a weighted+centred inner `Column`, "Concedi" (`Button`) and the "inserisci
  codice manualmente" `TextButton` below it are the trailing pair, both
  `.fillMaxWidth()`, the `Button` also `.height(48.dp)`.

The live-camera `ScanFullScreen` branch (permission already granted) was
left untouched — its `Cancel`/manual-entry controls are overlay `TextButton`s
positioned with `Modifier.align(Alignment.BottomCenter)`/`TopCenter` inside
a `Box`, already anchored by a different (and here, more appropriate)
mechanism than the weighted-Column technique used everywhere else in this
pass; there was nothing to fix.

Verified on the emulator: the join lobby's short `NfcHero` state now shows
`Cancel` pinned to the bottom instead of centred mid-screen with the
mascot; the manual-code screen shows "Scan"/`Cancel` both full-width at
the bottom; the camera-rationale screen shows "Grant"/"enter a code
manually" the same way.


## The manual-code screen was the one bare spot in the whole flow, and had the wrong CTA on top

Reported: "la pagina join time toghere è un po' vuota e manca otter. le ct
sono errate (scan è la primary)" — `GroupPauseCodeEntryScreen`'s
`JoinMode.MANUAL` branch, reached via "Enter a code manually" from the
full-screen scanner. Two separate complaints, both real:

- **No mascot.** Every other screen in this flow anchors itself around an
  otter mark — `OtterTapMark` for the NFC hero, `OtterZenMark` for
  connecting/waiting, `ParticipantRing`'s `OtterZenMark` in the host lobby.
  The manual-code screen alone had just a title and a text field, floating
  in otherwise empty space above the buttons. Fixed by adding
  `OtterZenMark(markSize = 88.dp)` above the title, same size already used
  for the sibling `Connecting`/`WaitingForHost` states in
  `GroupPauseBluetoothLobbyJoinScreen.kt`.
- **Wrong CTA hierarchy.** The screen had *two* buttons competing for
  "primary" attention: `ManualCodeTab`'s own submit button (a plain filled
  `Button`) and "Scan instead" (a `CalmSecondaryButton`, tonal). That's
  backwards from what this file's own comment already said —
  "Scansionare è la strada più rapida delle due" — the code disagreed with
  its own reasoning. Swapped: "Scan" is now the filled `Button` (primary),
  the manual-code submit is now `CalmSecondaryButton` (tonal, matching
  [CalmSecondaryButton]'s own doc comment: "resta comunque *sotto*
  all'azione primaria della schermata"). `Cancel` is unchanged, outlined,
  at the very bottom.

Verified on the emulator (screenshot taken and discarded after checking,
not committed): otter mark now sits above the title, "Scan" renders as the
solid dark-green primary button, "Join" (manual submit) as the lighter
tonal pill beneath the text field, "Cancel" outlined at the bottom —
matching the CTA hierarchy used everywhere else in this flow.

Follow-up, same screen: "Scan dovrebbe essere l'ultimo bottone, come da
specifica" — the swap above got the right button styled as primary but
left it in the wrong position. Every other screen in this pass puts the
primary CTA *last* (`Cancel` above, "Iniziamo"/confirm below, see "The
host lobby's Cancel/'Iniziamo' is a `Column` now, not a `Row`" above) —
this screen briefly had it first. Reordered: `Cancel` (outlined) then
`Scan` (filled, primary) as the last sibling, matching the convention.
Verified the same way, screenshot checked and discarded.


## `group_pause_lobby_with_many`, converted to plurals despite `quantity="one"` being unreachable today

Found during a full-project review (`TODO.md` "5.1"), not a report.
`lobbyTitleFor()` (`GroupPauseBluetoothLobbyHostScreen.kt`) already
branches `1 -> ...with_one`, `2 -> ...with_two`, `else -> ...with_many` —
so `with_many`'s `%2$d` (`participantNames.size - 1`) is always ≥ 2 by
construction; `quantity="one"` can never actually be selected for it in
English or Italian. Converted to `<plurals>` anyway, same reasoning
already applied to the pre-existing, structurally identical
`block_group_with_many` (`BlockScreen.kt`) elsewhere in this same feature:
a fixed string can't distinguish counts the way a language with a separate
"few" category (Polish, Russian — 2 through 4 take their own form) needs,
even though English/Italian never exercise that difference themselves.
Lint's `MissingQuantity` check requires the `one` variant to exist
regardless (every quantity the declared locale's plural rules define must
have an entry) — written with the same wording as `block_group_with_many`'s
own `one` variant for consistency, understood to be dead text for as long
as the calling code keeps its current 1/2/else split.

Verified: `PluralsCandidate`/`MissingQuantity` both clear from lint, full
build/lint/test pass. Not exercised live (would need three physical/second
devices in a lobby simultaneously, same live-Bluetooth verification limit
already documented elsewhere in this file for other NFC/Bluetooth-gated
features).

## Bug: "Time together" did nothing on a fresh install with no permissions granted

Reported directly ("prima installazione, non ho ancora dato i permessi e
tappando su 'tempo assieme' non succede nulla"). Root cause:
`MainScreen`'s `CalmSecondaryButton` for "Time together"
(`ui/screens/MainScreen.kt`) gates on the same
`BuildConfig.DEBUG || (accessibilityOk && dndOk)` check as tapping the
otter, and when that's false it calls the `onOtterTap` parameter instead
of navigating — the intent being to reuse the exact same
`PermissionExplainerDialog` the otter tap already shows, documented
inline as "Stesso controllo del tap sull'otter... così un controllo solo
le copre tutte e due". The wiring was never finished: `MainActivity.kt`'s
`MainScreen(...)` call never passed `onOtterTap`, so it silently fell
back to the composable's own `onOtterTap: () -> Unit = {}` default — a
no-op. The *actual* permission-check-and-show-dialog logic lived only
inside `PersistentOtter`'s own `onStart` lambda, built inline in
`MainActivity.kt` and never exposed anywhere else.

Fixed by lifting that lambda out of `PersistentOtter`'s `onStart` into a
shared `startOrPromptPermissions` in `MainActivity.kt`, used by both
`PersistentOtter.onStart` and `MainScreen`'s new `onOtterTap =
startOrPromptPermissions` argument — one source of truth for "check
permissions, either start a session or show the dialog", instead of one
real copy and one unwired parameter. Because `MainScreen`'s button
already private-gates on `ok` before ever calling `onOtterTap`, calling
the *session-starting* lambda from that else-branch is safe: it's only
reached when `ok` is false, so `startOrPromptPermissions` always takes
its "show the dialog" branch there, never "start a session by accident."

**Verified with the actual defect reproduced first, on-device.** Debug
builds bypass this entirely (`BuildConfig.DEBUG ||` short-circuits both
checks), which is exactly why the bug shipped unnoticed through this
project's usual debug-build testing — so verifying the fix required
temporarily forcing both `BuildConfig.DEBUG ||` checks to `false` (one in
`MainActivity.kt`, one duplicated inline in `MainScreen.kt`'s button
`onClick` — easy to miss the second one, which is what made the first
fix attempt still show nothing, still navigating straight through via
the *other* untouched bypass), reinstalling on a freshly-uninstalled app
(no password, no permissions), completing onboarding, denying the
notification-permission prompt, and tapping "Time together" from Home:
before the fix, `GroupPauseChooserActivity` opened anyway (confirms the
bug — the DEBUG bypass, not the `onOtterTap` no-op, was masking it once
one of the two checks was patched); after fixing `onOtterTap`'s wiring,
the same tap correctly shows "Permissions needed" (Accessibility +
Do Not Disturb, each with its own "Grant"). Both temporary `false &&`
overrides were then reverted (confirmed via the real
`BuildConfig.DEBUG ||` text restored in both files), and a normal debug
build was reinstalled and re-tested: tapping "Time together" with no
permissions granted now goes straight to `GroupPauseChooserActivity`
again, as intended for local development. Full build/lint/test suite
passes on the final, reverted-to-real code.

## Anello attorno all'otter, condiviso da tutte le schermate "in attesa" del flusso Join

Segnalato insieme al bug precedente, come continuazione della stessa
revisione screenshot-by-screenshot che aveva già trovato il vuoto
verticale eccessivo su lobby join, ripiego permesso fotocamera e
inserimento manuale del codice (le tre schermate, insieme a Change
Password, con più spazio vuoto della media del resto dell'app — trovato
mentre si rifacevano gli screenshot marketing del README, non da un
report esplicito). Proposta dell'utente per risolverlo: dare a tutte le
pagine di Tempo Insieme lo stesso trattamento "waiting for someone" già
usato dalla lobby host.

`OtterRingIllustration` (nuova, `CalmBackground.kt`) estrae lo
specchio d'acqua + anello di `ParticipantRing`
(`GroupPauseBluetoothLobbyHostScreen.kt`) in una versione senza satelliti
partecipante, riusabile da qualunque schermata con un singolo otter:
stessi raggi/colori, scalati per un otter da 88dp anziché 110dp (140dp di
canvas anziché 180dp). `dashed` segue la stessa logica dell'anello host:
tratteggiato quando la schermata non sta ancora facendo nulla di concreto
(permesso mancante, ricerca non partita), pieno quando lo sta facendo.

Applicato a:
- `GroupPauseBluetoothLobbyJoinScreen.kt` — `NfcHero` (`dashed = !allReady`,
  prima nudo), `SearchHero`/`SearchingIllustration` (`dashed = !searching`,
  le onde di ricerca animate restano un overlay sopra l'anello quando
  `searching` è vero, non lo sostituiscono — prima l'anello vero e proprio
  non c'era mai, solo le onde quando la ricerca era attiva), `Connecting` e
  `WaitingForHost` (`dashed = false`, prima nudi).
- `GroupPauseJoinScreen.kt` — l'inserimento manuale del codice
  (`dashed = false`, prima nudo) e il ripiego senza permesso fotocamera
  (`dashed = true`, **prima l'unica schermata dell'intero flusso Tempo
  Insieme senza alcuna mascotte**).

Non toccati: la lobby host (ha già `ParticipantRing`, che questa
composable non sostituisce — disegna anche i satelliti partecipante) e la
pagina QR/countdown (hanno già abbastanza contenuto sotto l'otter — QR,
codice, chip "parte tra" — da non aver mai avuto il problema segnalato).

Verificato sull'emulatore (Pixel 10 Pro AVD, flavor stable), tutte e
quattro le schermate toccate: lobby join (tratteggiato, permessi
Bluetooth mancanti), ripiego fotocamera (tratteggiato, permesso negato in
diretta per la verifica), inserimento manuale codice (pieno). Non
verificato dal vivo: `Connecting`/`WaitingForHost` (richiedono un secondo
dispositivo Bluetooth reale, stesso limite di verifica già documentato
altrove in questo file) e la variante "onde di ricerca attive" di
`SearchingIllustration` (stesso motivo — l'emulatore non ha un vero
adattatore Bluetooth con cui scoprire nulla). Full build/lint/test verde.

## Lobby join: NFC e ricerca fuse in un'unica schermata, non più due stati alternati

Segnalato subito dopo l'anello attorno all'otter qui sopra, sullo stesso
punto: "avvicinati a chi ti aspetta" (NFC) e "cerca un amico" (ricerca
Bluetooth manuale) erano due stati pieni della stessa lobby
(`JoinLobbyState.NfcHero`/`SearchHero`), raggiungibili l'uno dall'altro
solo toccando un link di testo — un passaggio in più per arrivare alla
ricerca quando l'NFC non basta (o non c'è un secondo telefono NFC a
portata), l'esatto "doppio step" segnalato.

Unificati in un solo stato `JoinLobbyState.Listening`: NFC (quando
`nfcAvailable`) e `join.startDiscovery()` partono **insieme** appena
`allReady`, non più l'uno o l'altro a seconda dello stato. Il testo
resta condizionato solo da `nfcAvailable` (titolo "Avvicinati a chi ti
aspetta" con NFC, altrimenti "Cerca chi ti sta aspettando" — lo stesso
delle due schermate di prima, non un terzo testo nuovo) e da se la lista
dei dispositivi trovati è vuota o no — non più da quale "modalità" è
attiva, perché non esiste più una modalità da scegliere.

**Perché non si riavvia `startDiscovery(autoConnectToNameMarker = ...)`
alla lettura NFC**: farlo avrebbe richiesto fermare la ricerca già in
corso e ripartire con un secondo `BroadcastReceiver`, o smontare/rimontare
`GroupPauseBluetoothJoin`. Invece il nome letto via NFC resta in
`pendingNfcMarker` (stato locale), e un `LaunchedEffect(pendingNfcMarker,
join.discovered.size)` si connette da sé appena quel nome compare fra i
dispositivi che la ricerca — già attiva — sta comunque trovando. Stesso
risultato per chi tocca i telefoni (connessione automatica, nessun tap
sulla lista), zero relitti Bluetooth da gestire in più.

**CTA "Scansiona o inserisci un codice": da pastiglia tinta a link nudo**
(`CalmLinkRow`, estratto in `CalmBackground.kt` dal Row già scritto a
mano per l'equivalente della lobby host — vedi la sezione precedente:
stesso identico trattamento, ora condiviso da due chiamanti invece di
uno). Segnalato per "alleggerire la pagina": fusa con l'ex `NfcHero`,
questa schermata ha ora anche l'elenco Bluetooth potenzialmente visibile,
non solo titolo+sottotitolo — la stessa pastiglia tinta che sulla lobby
host era già stata giudicata troppo pesante (vedi sezione "codice o QR"
qui sopra) lo sarebbe stata ancora di più qui.

`SearchingIllustration` (le onde animate) ora accetta l'otter come
parametro invece di disegnare sempre `OtterZenMark`: `OtterTapMark`
quando `nfcAvailable` (indizio "puoi anche avvicinare i telefoni"),
`OtterZenMark` altrimenti — l'anello e le onde restano identici in
entrambi i casi, cambia solo la mascotte al centro.

**Stringhe**: `group_pause_join_search_link` ("Cerca un amico nelle
vicinanze") e `group_pause_join_nfc_subtitle` ("Tieni i telefoni vicini
per un secondo") rimosse, non più raggiungibili da nessun punto del
codice — la seconda sostituita da `group_pause_join_listening_subtitle`
("Tieni i telefoni vicini, o aspetta che compaia qui sotto"), che
menziona entrambe le vie invece di una sola. `group_pause_join_nfc_title`
e `group_pause_join_search_hero_title` restano invariate, riusate come
titolo condizionato invece che uno per stato.

Verificato sull'emulatore (Pixel 10 Pro AVD, flavor stable, NFC non
disponibile — quindi solo il ramo `!nfcAvailable`, verificato dal vivo):
Home → Tempo insieme → Join mostra "Look for whoever's waiting for you" /
"Looking for a friend…" senza alcun link di switch, "Scan or enter a
code" come link con "›", nessun `GentleReadinessBanner` (Bluetooth già
concesso/attivo da una sessione precedente di questa stessa verifica), e
il link porta correttamente allo scanner QR. **Non verificato dal vivo**:
il ramo `nfcAvailable` (titolo "Avvicinati a chi ti aspetta" +
`OtterTapMark`, sottotitolo combinato) e l'auto-match via
`pendingNfcMarker` — richiedono un dispositivo con NFC reale, stesso
limite di verifica già documentato più volte in questo file per le
funzioni NFC/Bluetooth dal vivo. Full build/lint/test verde.

## Bug: la pillola di durata selezionata poteva essere fuori vista all'apertura della lobby host

Segnalato: `initialDurationMinutes` (la durata scelta in Home,
propagata dentro `GroupPauseBluetoothLobbyHostScreen`'s `durationMinutes`)
può essere una qualunque delle otto opzioni di `ScrollableMinutePillRow`
("30m"→"4h"), ma la riga scorrevole apre sempre con lo scroll a zero. Se
la durata portata da Home era oltre le prime due o tre pillole visibili
(es. "3h30"/"4h" — proprio il tipo di scelta che la dissolvenza sul bordo
destro della Home, aggiunta in questa stessa sessione, ora rende più
facile fare), la selezione restava fuori quadro: indistinguibile da
"nessuna selezione" finché non si scorreva a caso per trovarla.

Fix in `ScrollableMinutePillRow` (`GroupPauseHostScreen.kt`, unico punto
di definizione, condiviso dall'unico chiamante reale
`GroupPauseBluetoothLobbyHostScreen`): un `BringIntoViewRequester`
agganciato alla pillola selezionata (una sola alla volta, per
costruzione) + `bringIntoView()` in un `LaunchedEffect(Unit)` — quindi
solo alla prima composizione, non a ogni cambio di selezione: una volta
che l'utente tocca una pillola per cambiarla, quella è già in vista per
definizione (l'ha appena toccata), niente da riportare a fuoco.

Verificato sull'emulatore (Pixel 10 Pro AVD, flavor stable): selezionata
"4h" in Home (scorrendo la riga durate lì), Home → Tempo insieme →
Create — la lobby host si apre con "4h" già visibile ed evidenziata,
senza alcuno scroll manuale. Full build/lint/test verde.

## Lista dispositivi Bluetooth della lobby join, filtrata ai soli telefoni

Segnalato: senza filtro, il discovery Bluetooth classico
(`GroupPauseBluetoothJoin.startDiscovery()`) elenca qualunque dispositivo
classico in raggio con Bluetooth accesso — lavatrice, stampante,
lampadine — non solo l'altro telefono che ospita la lobby.

`BluetoothDevice.getBluetoothClass()?.majorDeviceClass ==
BluetoothClass.Device.Major.PHONE` filtra i dispositivi già dentro il
`BroadcastReceiver` di `ACTION_FOUND`, prima di aggiungerli a
`discovered`: è la classe hardware reale che Android riporta per il
dispositivo trovato (letta dal dispositivo remoto, non qualcosa che
questa app annuncia da sé), e l'host qui è sempre e solo un telefono
Android — nessun altro tipo di dispositivo espone il servizio RFCOMM di
questa app, quindi non c'è un caso legittimo in cui un non-telefono
dovrebbe comparire nell'elenco. `bluetoothClass` nullo (dispositivo che
non l'ha ancora annunciata al momento di `ACTION_FOUND`, o adapter che
non la conosce) esclude per dubbio, coerente con lo scopo del filtro.

**Non verificato dal vivo**: richiederebbe elettrodomestici Bluetooth
Classic reali nel raggio dell'emulatore per confermare che vengano
davvero esclusi (l'emulatore non ha un vero adattatore Bluetooth — stesso
limite di verifica già documentato più volte in questo file per le
funzioni Bluetooth dal vivo). Il filtro stesso è una singola condizione
booleana su un campo documentato dell'API pubblica `BluetoothClass`, non
euristica: il rischio residuo è che un dispositivo riporti la propria
classe in modo scorretto (fuori dal controllo di questa app), non che il
confronto sia sbagliato. Full build/lint/test verde.

## Lobby join: istruzioni più esplicite, nome Bluetooth locale visibile

Segnalato: la pagina "Unisciti" non spiegava di avvicinare il *retro* dei
telefoni (dove sta l'antenna NFC — dire solo "avvicina i telefoni" non
bastava a capire quale lato) né che i nomi nell'elenco Bluetooth vanno
toccati per collegarsi. Inoltre non c'era modo di controllare, a voce o
per messaggio con l'altra persona, che il proprio nome fosse comparso
correttamente dall'altra parte — soprattutto quando l'elenco mostra più
di un dispositivo.

Tre modifiche, tutte in `GroupPauseBluetoothLobbyJoinScreen.kt`:

- `group_pause_join_listening_subtitle` (mostrata quando nfcAvailable e
  la lista è vuota) ora dice esplicitamente "retro" ("Avvicina il retro
  dei telefoni, o scegli il nome del tuo amico qui sotto" — prima
  "Tieni i telefoni vicini, o aspetta che compaia qui sotto").
- Nuova `group_pause_join_pick_hint` ("Tocca il nome del tuo amico per
  collegarti"), mostrata sopra l'elenco dei dispositivi trovati quando
  non è vuoto — prima l'elenco appariva senza alcuna spiegazione di cosa
  farne, in nessuno dei due rami (NFC o ricerca: stesso elenco per
  entrambi da quando sono stati fusi, vedi la sezione sopra).
- Nuova `group_pause_join_your_name`, mostrata sempre (non solo quando
  la lista ha risultati): "Sei visibile come %1$s", con lo stesso
  `localName` (`localBluetoothDisplayName(context)`) già usato
  nell'handshake — non un valore separato da tenere sincronizzato a
  mano. Font più piccolo (13sp) e più tenue (alfa 0.55) delle altre
  scritte della schermata: è un'informazione di controllo, non
  un'istruzione da leggere per prima.

**Non verificato dal vivo**: il sottotitolo NFC-specifico e l'hint
sull'elenco non sono esercitabili su questo emulatore (nessun NFC,
nessun vero dispositivo Bluetooth Classic da scoprire per popolare la
lista — stesso limite di verifica documentato più volte in questo file).
Verificato invece "Sei visibile come sdk_gphone16k_arm64" comparire
correttamente sulla lobby join reale (Pixel 10 Pro AVD, flavor stable).
Full build/lint/test verde.

## Grafica della lobby join unificata a quella di Home/Chooser, increspature animate mentre cerca

Segnalato: unificare la grafica della pagina "Unisciti" con quella di
Home e del medaglione della schermata di scelta, e rendere più chiaro
che l'app sta cercando dispositivi Bluetooth e che una lista dinamica
comparirà.

`OtterRingIllustration` (`CalmBackground.kt`) passa da un disegno a 3
livelli scritto per questa fusione a un disegno a **5 livelli**, lo
stesso di `TandemPawsMedallion` (`GroupPauseChooserScreen.kt`) — anello
di contorno, anello interno (`dashed` sceglie fra tratteggiato/decorativo
e pieno/"pronto"), tre dischi dello stagno — con un parametro `size`
(default 160dp, invariato per gli altri chiamanti) per poterla
ingrandire dove serve. `TandemPawsMedallion` è ora un guscio sottile
attorno alla stessa funzione (`dashed = true` fisso: il suo anello
tratteggiato non ha mai rappresentato uno stato "non pronto", solo una
scelta decorativa del mockup) — non due disegni simili mantenuti a mano
in due file, uno solo.

Nuovo parametro `pulsing`: increspature animate in loop continuo (non a
scatto unico come `AmbientRipples` della Home — la ricerca può durare
minuti, fermarsi dopo pochi secondi avrebbe detto "ho smesso di
cercare"), stesso colore/idea visiva della Home, scalate al riquadro
dello specchio d'acqua invece che alla pagina intera. `SearchingIllustration`
(`GroupPauseBluetoothLobbyJoinScreen.kt`) ora delega interamente a
`OtterRingIllustration(size = 200.dp, pulsing = !hasResults && searching)`
invece di disegnare a mano il proprio pulse (rimosso insieme al suo
`rememberSearchPulse()`, la cui logica è confluita in
`rememberRipplePulse()` dentro `CalmBackground.kt`, condivisa).

**Bug trovato e corretto sull'emulatore prima di committare**: la prima
stesura del passaggio a 5 livelli usava un `pathEffect` tratteggiato in
*entrambi* i rami del parametro `dashed` (cambiava solo il passo del
tratteggio), quindi l'anello interno non diventava mai pieno per gli
stati "pronto" di lobby join/host (ricerca attiva, connessione in
corso...) — sempre lo stesso anello tratteggiato di `TandemPawsMedallion`,
qualunque fosse `dashed`. Corretto: `dashed = false` disegna ora un
tratto pieno (nessun `pathEffect`), come faceva la versione a 3 livelli
prima di questa fusione.

Verificato sull'emulatore (Pixel 10 Pro AVD, flavor stable): il
medaglione della schermata di scelta resta pixel-identico a prima del
refactor (confrontato screenshot contro screenshot); la lobby join, con
Bluetooth concesso e attivo, mostra ora l'anello interno pieno (non più
tratteggiato) nello stato "in ricerca" — la distinzione dashed/pieno
funziona di nuovo. Le increspature animate stesse non sono state
catturate in un singolo screenshot (l'animazione è ciclica, un frame
statico può cadere in un momento di bassa opacità) — la logica riusa
però lo stesso idioma `withInfiniteAnimationFrameMillis`/passo fisso già
verificato altrove in questo file per lo stesso tipo di animazione (vedi
"The pulse was implemented wrong the first time"), non un codice nuovo
da verificare da zero. Full build/lint/test verde.
