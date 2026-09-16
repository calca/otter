# Home & Settings Split — Design

## Key files

| File | Role |
|---|---|
| `MainActivity.kt` / `ui/screens/MainScreen.kt` | Home: session state, streak, tap-to-explain permission dialog, navigation to History and Settings |
| `SettingsActivity.kt` / `ui/screens/SettingsScreen.kt` | Permission/Home-app status card, theme picker, change-password launch, password-gated allowed-apps launch, phrases toggle |
| `PermissionChecks.kt` | Two top-level functions (`isAccessibilityServiceEnabled`, `isDndAccessGranted`) shared by `MainActivity` and `SettingsActivity` — previously identical private copies in each, plus a third copy in `OnboardingActivity` that was deleted outright (not switched to the shared function) when the permissions step left the onboarding wizard, see `onboarding-and-password/requirements.md`'s "3-step wizard" |
| `ui/screens/CalmBackground.kt` | `Modifier.calmBackground()`, a `primary`-tinted vertical-gradient background applied to Home's and Onboarding's root `Column`s only — see "Tinted background" below |

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
pass. `promptPasswordThenOpenAllowedApps()` itself was later deleted
outright, not just moved again — see "Non-Compose dialogs" in
`session-history-and-stats/design.md` for why, and
`ui/screens/PasswordVerifyDialog.kt` for what replaced it.

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
composables at that time, unchanged internally — later replaced outright by
`ThemeListCard`/`ThemeListRow`, see "Full list-card redesign" below.

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
- **Tap-to-start confirmation animation** — requested directly ("vorrei
  un'animazione quando tappo su otter per avviare la sessione"): before this,
  tapping the otter called `onStart` (and therefore
  `sessionManager.startSession()`/`onSessionStarted()`, which swaps straight
  to `BlockScreen`) synchronously, an instant cut with no feedback on the tap
  itself. `PondScene` now holds its own `isStarting` (`mutableStateOf(false)`)
  gate: on tap it (a) disables further taps
  (`clickable(enabled = !sessionActive && !isStarting, ...)`), (b) springs the
  otter up to 1.18× scale (`animateFloatAsState` with
  `Spring.DampingRatioMediumBouncy`/`Spring.StiffnessMedium` — an overshooting
  "pop", not a linear scale), and (c) plays a one-shot `TapConfirmBurst` — an
  expanding-and-fading ring plus a quicker-fading center flash, driven by an
  `Animatable(0f)` animated to `1f` over 380ms (`FastOutSlowInEasing`) — layered
  over the continuous `AmbientRipples`, more pronounced than them since this
  one has to read as "confirmed", not merely ambient. `onStart` (the real
  session-start callback) is only invoked once that 380ms animation
  completes, not on the tap itself — the whole gesture still resolves in
  well under half a second, so responsiveness isn't traded away, but the tap
  now has a visible acknowledgment before the screen swap instead of a bare
  cut.
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
  codebase; at the time, every existing dialog elsewhere — `SettingsActivity`'s
  password prompt, `HistoryActivity`'s clear-history/weekly-goal dialogs —
  was a View-based `androidx.appcompat.app.AlertDialog.Builder` invoked
  imperatively from an `Activity`, not a good fit here since this dialog's
  state (`showPermissionDialog`) and content both live in `MainScreen`
  itself; all three of the others were converted to Compose too in later
  passes, on request — see `session-history-and-stats/design.md`'s "From
  non-Compose dialogs to Compose Material3"). It renders one `PermissionReasonRow` per *currently missing*
  permission only (`if (!accessibilityOk) ... if (!dndOk) ...` — a
  permission already granted gets no row, so a second tap after granting
  one shows only what's left, or doesn't open at all if nothing's left).
  Each row's reason string (`permission_reason_accessibility`/`_dnd`)
  mirrors onboarding step 3's wording but is its own string, not extracted
  from `onb3_body` — onboarding's combined multi-paragraph string was left
  untouched to avoid any risk to that separately-tested flow. `AlertDialog`
  was originally left with its default M3 container color
  (`AlertDialogDefaults.containerColor`, effectively `surfaceContainerHigh`)
  — reasoned at the time as an acceptable "neutral system-chrome tone",
  unlike a hand-drawn mascot mark. **This turned out wrong once more
  dialogs existed**: with only this one dialog in the app, the uncustomized
  purple/pink M3 default was easy to read as "generic system chrome"; once
  `PasswordVerifyDialog`/`WeeklyGoalDialog`/`ClearHistoryConfirmDialog`
  existed too, the same fixed hue on *every* dialog regardless of
  Sage/Lavender/Terracotta became visible as a real mismatch and was
  reported directly. Fixed in `CalmOtterTheme.kt` by customizing
  `surfaceContainerHigh` per palette after all (see its own doc comment
  for the up-to-date list of which roles are customized — `surfaceVariant`/
  `primaryContainer` still aren't, per CLAUDE.md). Only the row
  content remains a place this design chooses `onSurface` deliberately,
  same as before.
  `MainScreen`'s `onStart` lambda passed into `PondScene` is what decides
  which behavior a tap gets: `if (BuildConfig.DEBUG || (accessibilityOk &&
  dndOk)) startSession() else showPermissionDialog = true` — the
  `BuildConfig.DEBUG ||` short-circuits the whole check in debug builds
  (including CI's `assembleDebug`), so a session can be started for manual
  testing without actually granting Accessibility/DND first. Release builds
  are unaffected (`BuildConfig.DEBUG` is `false` there, same check as
  before). This doesn't fake the underlying OS permissions — the real
  service still won't run if Accessibility genuinely isn't enabled, and
  `SessionManager.setPauseDnd()` already no-ops silently when DND
  access isn't granted (see `app-blocking-and-home-lock/design.md`) — it
  only skips the *app-level* gate that would otherwise stop you from
  starting a session at all while iterating on everything else (Settings,
  History, the block screen). Requires `buildFeatures.buildConfig = true`
  in `app/build.gradle.kts` (off by default on modern AGP) to generate the
  `BuildConfig` class at all.
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
  unchanged at the time — `SettingsScreen` receives them as
  `isDefaultHome: () -> Boolean` / `onSetHome: () -> Unit`, refreshed by the
  same `resumeSignal` as the two permission checks. `promptSetAsHome()`'s
  own implementation was later found broken on Android 10+ and rewritten
  around `RoleManager` — see `app-blocking-and-home-lock/design.md`'s
  "Settings' 'Home app' switch: two real bugs, both found via logcat".

## Tinted background: `Modifier.calmBackground()`

A fifth pass gave Home and Onboarding (only — not Settings or History) a
very light `primary`-tinted vertical gradient background,
in place of the plain neutral `background` they inherited from the window
theme before. `Modifier.calmBackground()` (`ui/screens/CalmBackground.kt`)
is a `@Composable` `Modifier` extension:

```kotlin
this.background(
    brush = Brush.verticalGradient(
        colors = listOf(primary.copy(alpha = 0.09f), primary.copy(alpha = 0.02f))
    )
)
```

applied to the root `Column` right after `.fillMaxSize()` and before
`.safeDrawingPadding()`/`.padding(...)` (so the tint fills the whole screen,
including the system-bar insets, not just the safe-drawing area). It's a
single shared function (not duplicated per-screen) because every caller
wants the exact same treatment; it lives in `ui/screens/` since
`MainScreen.kt`, `OnboardingScreen.kt`, and (added later, see
`app-blocking-and-home-lock/design.md`'s "BlockScreen redesign")
`BlockScreen.kt` are all in that package and can use an
internal-visibility file directly, no export needed elsewhere.

**Why a low-alpha gradient of `primary` and not `primary` itself as a solid
fill** — the alternative was explicitly considered and rejected: `primary`
at full intensity as the background would have broken `OtterFloatMark`
everywhere it's drawn, not just the text contrast. In the real implementation (see
`mascot-marks/design.md`) the mark's fur is `primary` at low alpha, its
"eyes" are drawn in the literal `background` color (meant to read as
negative space against the screen), and its nose is `primary` at full
strength — if `background` itself became `primary`, fur/eyes/nose would
collapse toward the same hue and the mark would render as a near-featureless
blob, on top of every `onSurface`/`onBackground` text needing to flip to a
light color to stay legible on a dark/saturated fill. The chosen low-alpha
gradient composites over the existing neutral `background` without
changing what color role any other element already reads — `OtterFloatMark`,
chips, the chart card, and every text color are completely unaffected,
which is why this was a small, low-risk change instead of a redesign.

## Info section: `InfoCard`/`InfoLinkRow` (`SettingsScreen.kt`)

A sixth pass added an "Info" section at the bottom of Settings — the only
UI in the whole app that opens something external (a browser). Same visual
language as `PermissionStatusCard` above (`Surface` at `primary.copy(alpha
= 0.06f)`, `HorizontalDivider` between rows), for consistency between
Settings' two status/info cards, not styled as buttons like "Change
password"/"Manage allowed apps" — there's nothing to *do* here, just
somewhere to go.

- **`InfoCard`** wraps three `InfoLinkRow`s: GitHub repo, license, developer.
- **`InfoLinkRow`** — a label (`Modifier.weight(1f)`, so a long label
  doesn't push the trailing glyph off-screen) plus a trailing "↗" in
  `primary`, the whole row `Modifier.clickable`. No icons anywhere else in
  Settings, so none here either — consistent with the rest of the screen.
- **URLs live in `SettingsActivity.kt`** as two top-level `private const
  val`s (`GITHUB_REPO_URL`, `GITHUB_DEVELOPER_URL`), not in `SettingsScreen.kt`
  — the screen only receives `onOpenGitHub`/`onOpenLicense`/`onOpenDeveloper`
  lambdas, each just `startActivity(Intent(ACTION_VIEW, Uri.parse(url)))`
  via a shared private `openUrl()` helper. The license URL is derived from
  the same `GITHUB_REPO_URL` (`"$GITHUB_REPO_URL/blob/main/LICENSE"`) rather
  than being its own constant, so the two never drift apart.
- **The `LICENSE` file the license row links to is a real file** at the
  repository root (MIT, added alongside this feature) — the in-app string
  and the link both point at an actual license grant, not a claim with
  nothing backing it. `README.md` gained a one-line `## License` section
  linking the same file, so it's discoverable without opening the app too.
- No `<queries>` manifest entry was needed for the `ACTION_VIEW`
  `http`/`https` intent — that's one of Android's automatically-exempted
  implicit-intent signatures under package-visibility rules (unlike
  `AllowedAppsActivity`'s `ACTION_MAIN`/`CATEGORY_LAUNCHER` query, see
  `app-blocking-and-home-lock/design.md`, which does need a `<queries>`
  entry since it enumerates *all* launchable apps, not one well-known
  implicit action).

## Full list-card redesign: `ThemeListCard`, `HomeCard`, `PasswordCard`, `PhrasesCard`

A seventh pass made every Settings section visually consistent — the same
`Surface(shape = RoundedCornerShape(20.dp), color =
MaterialTheme.colorScheme.primary.copy(alpha = 0.06f))` card, the same
`SectionLabel` heading above it — replacing the old dot-based theme picker
and plain checkbox with the same list-row/toggle idioms already used by
`PermissionStatusCard`.

- **`ThemeListCard`/`ThemeListRow`** replace `ThemePicker`/`ThemeDot`
  outright (the AndroidView `StateListDrawable`-based 3-dot row mentioned
  above, and the now-orphaned `drawable/theme_dot_{sage,lavender,terracotta}.xml`
  it read, all deleted). Each row is a swatch circle (`colorResource(R.color.*_primary)`,
  reactive to day/night — see `multi-theme-system/design.md` for why
  `colorResource()` and not `MaterialTheme.colorScheme.primary` is what
  makes each row show its *own* palette color regardless of which theme is
  currently active) + label + a trailing `settings_theme_selected` ("Selected")
  string on the active row instead of the old filled/outlined dot styling.
  `ThemeListRow`'s tap target is the whole row (`Modifier.clickable`), not
  just the swatch.
- **`HomeCard`** — a single row, `permission_row_home` label + a `Switch`
  bound to `homeOk`/`onSetHome`. Replaces the old "Set as Home" text button
  inside `PermissionStatusCard`; Home-app status is no longer mixed in with
  the two OS permission rows, it's its own section since it isn't a
  system-level "grant" in the same sense.
- **`PasswordCard`** — when `partnerName` is non-null/non-empty, a leading
  read-only row (`settings_password_set_by`, e.g. "Set by Luca") above a
  divider, then the existing `SettingsActionRow`s for "Change password" and
  "Manage allowed apps" (both pre-existing, just relocated into this
  specific card/section instead of loose rows). The name row is omitted
  entirely (no empty "Set by" placeholder) when no partner name was
  captured during onboarding or a plain `setPassword(password)` call was
  used — see `onboarding-and-password/design.md` for where the name comes
  from.
- **`PhrasesCard`** — the "Show phrases during pause" preference moved from
  a plain `Checkbox` + label row into the same card-with-`Switch` shape as
  `HomeCard`, for visual consistency; behavior (`phraseManager.isEnabled()`/
  `setEnabled()`) unchanged.
- **`calmSwitchColors()`** (private, `SettingsScreen.kt`) — a shared
  `SwitchDefaults.colors(...)` restricted to the same eight palette-safe
  roles as everywhere else in this app (`primary`/`onPrimary`/`onSurface`
  only, see the `surfaceVariant` trap note in CLAUDE.md), used by both
  `HomeCard`'s and `PhrasesCard`'s `Switch`es so they look identical and
  never accidentally pull in an uncustomized M3 role.
- Net effect: every Settings section now reads as "a labeled card", with
  `PermissionStatusCard` no longer the only one — `HomeCard`, `ThemeListCard`,
  `PasswordCard`, `PhrasesCard`, and the pre-existing `InfoCard` all share
  the same visual grammar.

## Every full screen scrolls: `CalmScreenColumn`

The app's screens were `Column(fillMaxSize)` with no scrolling. When the
content didn't fit — a larger system font, a shorter screen — it was simply
clipped, **silently**. Measured on Home at `font_scale 1.5`: the "Tempo
insieme" button and the "again with…" shortcut disappeared entirely,
absent even from the semantics tree, with nothing on screen suggesting
anything was missing. Two entry points to a whole feature, gone. The
history card's title also overlapped its "History →" link, two `Text`s in
a `SpaceBetween` row with no width constraint between them.

`CalmScreenColumn` (`CalmBackground.kt`) is now the standard container:
`verticalScroll` plus `heightIn(min = maxHeight)` from a
`BoxWithConstraints`. The minimum height is what keeps the normal case
looking unchanged — while the content fits, the column is exactly the
viewport and `verticalArrangement` lays it out as before; past that it
grows and scrolls.

Applied to every full screen that lacked it: both group-pause lobbies, the
countdown, the host setup and QR-delay steps, the join code entry, and the
release step. Home uses the same idiom inline with
`Arrangement.SpaceBetween`, which preserves "header at top, card at the
bottom" without `weight`.

**`Modifier.weight` cannot be used inside a vertical scroll** — the
available height is infinite there. Home's middle column previously relied
on `weight(1f)`; distributing space is now `verticalArrangement`'s job.
That constraint is the single thing to remember when adding to these
screens.

