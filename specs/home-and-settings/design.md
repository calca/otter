# Home & Settings Split — Design

## Key files

| File | Role |
|---|---|
| `MainActivity.kt` / `ui/screens/MainScreen.kt` | Home: session state, streak, tap-to-explain permission dialog, navigation to History and Settings |
| `SettingsActivity.kt` / `ui/screens/SettingsScreen.kt` | Permission/Home-app status card, theme picker, change-password launch, password-gated allowed-apps launch, phrases toggle |
| `PermissionChecks.kt` | Two top-level functions (`isAccessibilityServiceEnabled`, `isDndAccessGranted`) shared by `MainActivity` and `SettingsActivity` — previously identical private copies in each, plus a third copy in `OnboardingActivity` that was deleted outright (not switched to the shared function) when the permissions step left the onboarding wizard, see `onboarding-and-password/requirements.md`'s "3-step wizard" |

## What moved where, and why

Everything that was a preference, an occasional admin action, or a status
check that doesn't need to interrupt starting a pause (theme, password,
allowed-apps, phrases, and — since the "Permissions off Home" pass below —
accessibility/DND/Home-app status) **moved to Settings**. History is the
one item that could have gone either way and was deliberately kept on Home
(see requirements.md User Story 3) — it's frequent/rewarding to check, not
"configuration". Home itself keeps only what's needed to start a pause;
even the two permissions that *do* gate `startSession()` are no longer a
persistent Home fixture — see "Permissions off Home" for why tapping the
otter, not a banner, is what surfaces them now.

`SettingsActivity` follows the same shape as `HistoryActivity`/
`ChangePasswordActivity`/`AllowedAppsActivity`: `WITH_ACTION_BAR` theme
variant, `onSupportNavigateUp()` finishes. Unlike those, and unlike its own
earlier version, it now **does** carry a `resumeSignal` (incremented in
`onResume()`, same pattern `MainActivity` uses) — once the permissions/
Home-app status card moved here, this screen gained exactly the kind of
OS-level state that can change while backgrounded (user taps a status row,
goes to system settings, comes back) that used to be the reason it
*didn't* need one. (`OnboardingActivity` used to carry the same pattern
too, for its own now-removed permissions step — see
`onboarding-and-password/design.md`, which no longer has one either.)
`promptPasswordThenOpenAllowedApps()` and `pickTheme()` were moved here
verbatim from `MainActivity` in an earlier pass — same logic, same
`PasswordManager`/`ThemeManager` calls, just relocated with their
triggering button; `isDefaultHome()`/`promptSetAsHome()` (from
`app-blocking-and-home-lock/design.md`) moved here the same way in this
pass.

## Streak on Home

`MainScreen` now takes a `SessionHistoryManager` and recomputes
`SessionStreak.currentStreakDays(sessionHistoryManager.getAll())` inside
`refreshDerivedState()`, alongside the existing accessibility/DND/home/
session checks — same `resumeSignal`-driven refresh pattern already used
for everything else on this screen (see CLAUDE.md). Reuses
`R.string.streak_days` (`"🔥 %1$d giorni di fila"`), the exact string
`HistoryScreen` already uses for the same number — see
`session-history-and-stats/design.md`.

## `MainScreen`'s reduced parameter surface

Removed entirely from `MainScreen`'s signature: `phraseManager`,
`currentTheme`, `onPickTheme`, `onManageApps`, `onChangePassword` — none of
that state or those callbacks belong to Home anymore. Added:
`sessionHistoryManager` (for the streak) and `onSettings`. `ThemePicker`/
`ThemeDot` (the AndroidView-wrapped theme-dot selector — see
`multi-theme-system/design.md`) moved to `SettingsScreen.kt` as private
composables, unchanged internally.

## Living Pond: `PondScene` (`MainScreen.kt`)

A second redesign pass replaced the plain duration-picker/Start-button/
streak-text/History-button column with an otter-centric "pond" scene, kept
in the same file as private composables (no new file — everything here is
Home-specific, not a general-purpose mascot mark, except `OtterFloatMark`
itself which lives in `ui/mascot/OtterMarks.kt` alongside the other two
marks since it's reused nowhere outside Home but is thematically a mark).

- **`PondScene`** — a `Column` containing a fixed-size `Box` (the "pond":
  ripples/ring + otter, always centered) followed by either the duration
  chips or the active-session readout. `sessionActive` switches both the
  Box's contents and the row below it.
- **`AmbientRipples`** — three `Canvas`-drawn circles animated via one
  shared `rememberInfiniteTransition` float `t` (`0f..1f`, linear,
  restarting), each ring reading `(t + phase) % 1f` at a different `phase`
  (`0f`/`0.33f`/`0.66f`) so they appear staggered from a single animation
  driver rather than three independent ones. Purely decorative — shown
  only when `!sessionActive`.
- **`ProgressRing`** — two `drawArc` calls (a full track + a partial sweep
  from `-90°`, i.e. starting at 12 o'clock) over a fixed-size `Canvas`.
  `fraction = 1 - remainingMillis/totalMillis`, recomputed by
  `refreshDerivedState()` on the same `resumeSignal` cadence as everything
  else on this screen — **it does not tick live on a per-second timer**;
  matching the pre-existing `session_active_with_time` status text, which
  never did either. Shown only when `sessionActive`.
- **`OtterFloatMark`** (`ui/mascot/OtterMarks.kt`) sits inside a `Box` with
  `.offset(y = floatOffset.dp)` driven by its own
  `rememberInfiniteTransition` (a slower period while a session is active —
  5200ms vs 3200ms — meant to read as "settled" rather than "waiting").
  That same `Box` carries `.clip(CircleShape).clickable(enabled =
  !sessionActive, onClick = onStart)` — **the otter itself is the
  Start-Pause control**, and it is *always* enabled while idle (never
  `canStart`-gated on permissions — see "Permissions off Home" below for
  what `onStart` does when something's still missing).
- **`DurationChipRow`** — a hand-drawn chip row (`Surface(onClick = ...)`
  per `DURATION_LABELS` entry, background colored via
  `primary.copy(alpha = 0.08f/0.22f)` for unselected/selected — a soft tint
  of the active palette, not a saturated fill; label text stays `onSurface`
  for legibility), **not** M3's `FilterChip`. `FilterChip`'s selected/
  unselected colors pull from `secondaryContainer`/`primaryContainer`-family
  roles, which are not customized per palette (see the `surfaceVariant`
  trap note in CLAUDE.md and `mascot-marks/design.md`) — using it here
  would silently reintroduce a fixed, non-palette-aware color, exactly the
  bug this file already documents once. Replaces the old `NumberPicker`/
  `AndroidView` wheel entirely; `selectedDurationIndex` (`1..8`, matching
  `DURATION_LABELS`) lives in `MainScreen`'s own `remember` state instead of
  being read off an `AndroidView` reference at click time.
- **`SessionsChartCard`** — one `Surface(onClick = onHistory)` wrapping a
  header row (streak text or `home_chart_label`, plus the `home_history_cta`
  affordance text, both `onSurface`) and, when `hasHistory` is true, a `Row`
  of 7 `Box`es whose `fillMaxHeight(fraction)` encodes
  `weekSummary.dailyMinutes` plus a second `Row` of 7 `Text` initials below
  it (`weekSummary.weekdayIndices` indexed into the `weekday_initials`
  string-array, today's bolded/darker); bars are `primary.copy(alpha =
  0.18f)`, today's bar `primary.copy(alpha = 0.55f)`. When `hasHistory` is
  false (no session ever recorded) the bars/labels are replaced by one
  `home_chart_empty` line instead — otherwise every bar sits at the
  `0.04f` minimum-height floor and seven identical slivers read as a
  rendering bug, not as "zero sessions" (see requirements.md). The whole
  card is the History entry point (`Modifier.alpha(0.6f)` when
  `sessionActive`, instead of being hidden) — there is no separate History
  button anymore.
- **`WeekSummary`** (private data class) — `dailyMinutes`, `weekdayIndices`,
  `totalSessions`, `totalMinutes` for the rolling last-7-days window,
  computed together in one pass by `weekSummaryOf()` (replaces the earlier
  `last7DayMinutes()`). `weekdayIndices[i]` is `Calendar.DAY_OF_WEEK - 1`
  for that slot (`0`=Sunday), matching `weekday_initials`' index order.
  `weeklySummaryText()` (a small `@Composable` returning `String`) picks
  `weekly_summary_none`/`_one`/`_many` from `totalSessions`, formatted via a
  private `formatMinutes()` — both are a deliberate duplicate of
  `HistoryScreen.kt`'s own `weeklyChartData()`/`formatMinutes()` (same
  rolling-7-day semantics, same string family), not shared code, following
  the same precedent as `SessionStreak.dayStart()` documented above.

Every neutral-looking surface in this scene (ripples, chip backgrounds,
chart bars, the otter's fur) is actually `primary` at low alpha, not
`onSurface` — a soft tint of whichever palette (Sage/Lavender/Terracotta)
is active rather than a flat gray, so switching palette in Settings visibly
changes Home even though nothing here reads as "loud". Text and the
progress-ring track use `onSurface` (legibility, or a neutral track
regardless of palette). `primary` at full intensity appears in exactly two
places: the active-session progress ring's sweep arc, and the otter's nose
(a fixed small accent, mirroring the pebble in `OtterAtRestIllustration` —
see `mascot-marks/design.md`). All of the above still avoid every other M3
color role — only the eight that `CalmOtterTheme.kt` actually customizes
per palette (`background`/`surface`/`onBackground`/`onSurface`/`primary`/
`onPrimary`/`error`/`onError`) are ever used — see the `surfaceVariant`
trap note in CLAUDE.md.

## Anchored layout: the middle `Column` carries `weight(1f)`

A third pass gave `MainScreen`'s root `Column` three regions instead of one
flat top-aligned flow: the title row and permission prompts (fixed height,
top), a middle `Column(Modifier.weight(1f), verticalArrangement =
Arrangement.Center)` wrapping `PondScene` plus the weekly-summary line (this
region expands to fill whatever space is left and centers its content
inside it), and `SessionsChartCard` (fixed height, bottom — falls naturally
below the weighted region instead of trailing right under the pond). The
root `Column` also dropped its `.verticalScroll(...)`: nothing here is a
form, so there's no keyboard to dodge, and a `weight(1f)` child needs a
bounded-height parent to center within, which an indefinitely-tall
scrolling parent isn't. `PondScene`'s pond `Box` grew from 200dp to 260dp
(otter 96dp→124dp, active-session ring 136dp→176dp) — with the extra
vertical room now going to the pond instead of empty space below the card,
a larger otter reads as the deliberate focal point rather than one element
sized for a cramped top section.

## Permissions off Home: `PermissionExplainerDialog` and `PermissionStatusCard`

A fourth pass removed `MainScreen`'s permission-missing text, "Grant
permissions" button, and "Set as Home" button entirely, replacing them
with two separate, more targeted pieces of UI.

- **`PermissionExplainerDialog`** (private composable, `MainScreen.kt`) —
  a plain M3 `AlertDialog` (the first Compose-native dialog in this
  codebase; every existing dialog elsewhere — `SettingsActivity`'s
  password prompt, `HistoryActivity`'s clear-history/weekly-goal dialogs —
  is a View-based `androidx.appcompat.app.AlertDialog.Builder` invoked
  imperatively from an `Activity`, not a good fit here since this dialog's
  state (`showPermissionDialog`) and content both live in `MainScreen`
  itself). It renders one `PermissionReasonRow` per *currently missing*
  permission only (`if (!accessibilityOk) ... if (!dndOk) ...` — a
  permission already granted gets no row, so a second tap after granting
  one shows only what's left, or doesn't open at all if nothing's left).
  Each row's reason string (`permission_reason_accessibility`/`_dnd`)
  mirrors onboarding step 3's wording but is its own string, not extracted
  from `onb3_body` — onboarding's combined multi-paragraph string was left
  untouched to avoid any risk to that separately-tested flow. `AlertDialog`
  itself is left with its default M3 container color
  (`AlertDialogDefaults.containerColor`, effectively `surfaceContainerHigh`)
  — that role isn't customized per palette either (same family as the
  `surfaceVariant` trap in CLAUDE.md), but a dialog surface reading as a
  fairly neutral system-chrome tone is normal even in most themed M3 apps,
  unlike a hand-drawn mascot mark; only the row content is a place this
  design chooses `onSurface` deliberately, not left to a default.
  `MainScreen`'s `onStart` lambda passed into `PondScene` is what decides
  which behavior a tap gets: `if (accessibilityOk && dndOk) startSession()
  else showPermissionDialog = true`.
- **`PermissionStatusCard`** (private composable, `SettingsScreen.kt`) — a
  `Surface` (same `primary.copy(alpha = 0.06f)` tint as `SessionsChartCard`
  on Home, for visual consistency between the app's two card-shaped
  containers) wrapping three `PermissionStatusRow`s (accessibility, DND,
  Home-app), each a small circle (filled + "✓" when done, outlined and
  empty when not) + label + trailing "Done" (muted) or an action word in
  `primary` (`"Grant"` for the two system permissions, `"Set"` for Home-app
  — distinct verb since it's a different kind of action, an app-level
  setting rather than a system permission grant). Always visible, all
  three rows, whether done or not — a status view to check occasionally,
  not a to-do list that shrinks; contrast with the Home dialog above, which
  only ever shows what's missing.
- Both **reuse `isAccessibilityServiceEnabled()`/`isDndAccessGranted()`**
  from the new shared `PermissionChecks.kt` (see "What moved where, and
  why") rather than each Activity computing them independently.
- **`isDefaultHome()`/`promptSetAsHome()`** moved to `SettingsActivity`
  unchanged (same `ComponentName`/`PackageManager` dance documented in
  `app-blocking-and-home-lock/design.md`) — `SettingsScreen` receives them
  as `isDefaultHome: () -> Boolean` / `onSetHome: () -> Unit`, refreshed by
  the same `resumeSignal` as the two permission checks.
