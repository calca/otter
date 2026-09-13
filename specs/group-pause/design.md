# Group Pause — Design

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
| `QrCodeGenerator.kt` | `generateQrCodeBitmap(content, sizePx)` — wraps `com.google.zxing.qrcode.QRCodeWriter`, paints the result into a `Bitmap.Config.RGB_565` manually. |
| `ui/screens/GroupPauseHostScreen.kt` | Duration + start-delay pickers → generates a recipe → shows QR + text code + the shared countdown. |
| `ui/screens/GroupPauseJoinScreen.kt` | Scan (CameraX + zxing) or manual-code entry → decodes a recipe → the shared countdown. |
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
  one extra line under "Paused": `block_group_indicator` ("Part of a group
  pause"). Deliberately generic — see the "no participant count" limitation
  in requirements.md.
- `HistoryScreen.kt`'s `SessionRow` — a small `history_group_tag` line
  ("Group pause") under the existing outcome text, shown when
  `session.isGroupSession`. Same "no names" scoping as above.
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

Built as a second pairing mode alongside Phase 1's QR/manual code (which
is unchanged and still the default — Phase 2 is additive, selected via a
"QR/Codice" vs "Bluetooth" pill in `GroupPauseHostScreen`/
`GroupPauseJoinScreen`'s setup screens). Solves Phase 1's core limitation:
with a live channel, the host can now see who has joined before starting,
and gates "Start" on at least one participant being present.

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

- `nfc/GroupPauseHceService.kt` — a `HostApduService`. Before the host
  enables the "Avvicina i telefoni" toggle, the lobby screen sets a
  `@Volatile` companion field (`pendingMarker`) to the marker string;
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
  connected names, but `BlockScreen`'s `block_group_indicator` and
  `HistoryScreen`'s `history_group_tag` are unchanged from Phase 1 —
  still generic ("part of a group pause"), not "with Marco and Giulia."
  Nothing in `SessionRecord`/`GroupPauseRecipe` carries participant names
  forward past the lobby; wiring that through was judged out of scope for
  this pass and would be a natural next increment.
- **Verification is code-level only for the live paths.** See
  "Verification performed" below — an actual two-device Bluetooth
  handshake or NFC tap was not observed in this session, since only one
  adb-connected device was available.

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
