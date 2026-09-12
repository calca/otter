# Home & Settings Split — Design

## Key files

| File | Role |
|---|---|
| `MainActivity.kt` / `ui/screens/MainScreen.kt` | Home: session state, permission gates, streak, navigation to History and Settings |
| `SettingsActivity.kt` / `ui/screens/SettingsScreen.kt` | Theme picker, change-password launch, password-gated allowed-apps launch, phrases toggle |

## What moved where, and why

Everything that gated `startSession()` itself (accessibility/DND grant
buttons, Set-as-Home) **stayed on Home** — these aren't preferences, they're
blockers on the one action Home exists for. Everything that was a
preference or an occasional admin action (theme, password, allowed-apps,
phrases) **moved to Settings**. History is the one item that could have
gone either way and was deliberately kept on Home (see requirements.md
User Story 3) — it's frequent/rewarding to check, not "configuration".

`SettingsActivity` follows the exact same shape as `HistoryActivity`/
`ChangePasswordActivity`/`AllowedAppsActivity`: `WITH_ACTION_BAR` theme
variant, `onSupportNavigateUp()` finishes, no `resumeSignal` (nothing on
this screen depends on OS-level state that can change while backgrounded —
same reasoning as `HistoryActivity`, see its own design.md).
`promptPasswordThenOpenAllowedApps()` and `pickTheme()` were moved here
verbatim from `MainActivity` — same logic, same `PasswordManager`/
`ThemeManager` calls, just relocated with their triggering button.

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
  That same `Box` carries `.clip(CircleShape).clickable(enabled = canStart,
  onClick = onStart)` — **the otter itself is the Start-Pause control**;
  there is no separate button. `canStart = accessibilityOk && dndOk &&
  !sessionActive`, computed in `MainScreen` and threaded down, same
  permission logic `MainActivity` already exposed before this redesign.
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
