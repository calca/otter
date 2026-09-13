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

## Deferred: Phase 2 — live lobby via Bluetooth, NFC bootstrap

Discussed at length with the project owner before choosing Phase 1's
scope, and explicitly deferred rather than built now, for two concrete
reasons: it needs a real live channel Phase 1 doesn't have, and it needs
two physical radios to verify, which wasn't available in the environment
this was built and verified in.

The fuller vision:

- **A live lobby**, not a one-shot code: the host sees a running list of
  who has joined (device name, read via `Settings.Global.DEVICE_NAME` —
  confirmed readable with no Bluetooth permission required) before
  starting, and Start is a real host-gated action instead of an
  unconditional countdown.
- **Bluetooth (Nearby Connections or similar)** as the transport for that
  lobby — this is the part a one-shot QR/text code structurally cannot
  provide: scanning a QR tells the *joiner* the recipe, but gives the
  *host* no signal that anyone scanned it at all. A live lobby requires an
  actual open connection during the waiting period, not just at the
  handshake instant.
- **NFC as an alternative way to start that connection** — specifically
  **Host Card Emulation (HCE)**, tapping two phones together to bootstrap
  the same live session Bluetooth would otherwise need a QR/manual code to
  bootstrap. Classic Android Beam / NFC P2P (`NdefPush`) was considered and
  rejected: it's been deprecated and unreliable since Android 10, and isn't
  a reasonable foundation for a feature meant to be a flagship. HCE (the
  same mechanism contactless payment apps use) is the sound replacement.
- **Named participants** — once the host actually knows who joined (via
  the live lobby), `block_group_indicator` and `history_group_tag` could
  show real names/count instead of Phase 1's generic "part of a group
  pause" text.
- **Minimum-one-participant gate** — confirmed as a real requirement
  ("almeno un'altra persona") during design, but only enforceable with a
  live channel: Phase 1's host has no way to know if zero people ever see
  the code, so it cannot gate on that. This becomes possible once Phase 2
  supplies the join signal the gate needs.

None of this is coded yet. If Phase 2 is picked up, `GroupPauseRecipe`'s
"handshake" step should be replaceable by a Bluetooth/NFC-bootstrapped
live session without needing to touch `SessionManager`,
`CalmOtterDatabase`, or any of Phase 1's Home/BlockScreen/History
touch points — those were written against "a recipe becomes a started
session," a contract Phase 2 can still satisfy, just via a richer path to
get there.

## Verification performed (single device)

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
