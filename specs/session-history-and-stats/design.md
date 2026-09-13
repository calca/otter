# Session History & Stats — Design

## Key files

| File | Role |
|---|---|
| `SessionRecord.kt` | Room `@Entity` (table `sessions`): id, startTimeMs, plannedMinutes, effectiveMinutes, completedNaturally |
| `CalmOtterDatabase.kt` | Single-entity Room database, `allowMainThreadQueries()` (see CLAUDE.md — deliberate, tiny dataset, called from a receiver with no coroutine scope) |
| `SessionRecordDao.kt` | Room DAO |
| `SessionHistoryManager.kt` | Thin wrapper over the DAO; singleton |
| `SessionStreak.kt` | Pure streak calculation, no Context |
| `WeeklyGoalManager.kt` | Plain-`SharedPreferences` optional goal (`GoalType.SESSIONS` \| `MINUTES` + target) |
| `SessionCsvExporter.kt` | Pure CSV serialization, RFC 4180 quoting |
| `HistoryActivity.kt` | ActionBar chrome + `showGoalDialog`/`showClearConfirmDialog` state; the dialogs themselves live in `HistoryScreen.kt` |
| `ui/screens/HistoryScreen.kt` | Stats bar, streak, weekly chart, goal progress, session list, empty state, `WeeklyGoalDialog`/`ClearHistoryConfirmDialog` |
| `ui/screens/WeeklyChart.kt` | The weekly bar chart Composable |

## Streak algorithm (`SessionStreak.currentStreakDays`)

Builds a `Set` of "day starts" (midnight-normalized `Calendar` epoch millis,
local timezone) that have ≥1 session. Starting from today's day-start: if
today isn't in the set, step back one day first (so an ongoing streak isn't
broken just because today hasn't happened yet); if that day isn't in the set
either, the streak is 0. Otherwise it counts backward one day at a time
while each day is present, incrementing a counter. Pure function, takes
`now` as a parameter for testability (`SessionStreakTest`).

## CSV export path

`HistoryActivity.exportHistory()` writes `SessionCsvExporter.toCsv(...)` to
a file in `cacheDir`, then shares it via `FileProvider` (authority
`$packageName.fileprovider`, paths configured in `res/xml/file_paths.xml`)
with `ACTION_SEND` + `FLAG_GRANT_READ_URI_PERMISSION` — this avoids
`FileUriExposedException` on modern Android, which a raw `file://` URI would
trigger. Columns: `Data,Ora,Minuti pianificati,Minuti effettivi,Esito` (the
CSV is Italian regardless of app locale — this hasn't been revisited since
CSV export predates the `values-en/` translation).

## Why `HistoryActivity` has no `resumeSignal`

Unlike `MainActivity`/`OnboardingActivity`, `HistoryActivity` never needs to
re-read OS-level permission state — its data (`sessions`, `goal`) only
changes from actions taken *within* this screen (clear, goal edit), so it's
reloaded directly after those actions rather than on every `onResume()`.

## From non-Compose dialogs to Compose Material3

The weekly-goal editor and the clear-history confirmation used to be built
with `AlertDialog.Builder` + raw `View`s (`RadioGroup`, `EditText`,
`LinearLayout`), not Compose `AlertDialog` — documented at the time as a
deliberate, consistent choice across the codebase for one-off native
dialogs, alongside what was then `MainActivity.promptPasswordThenOpenAllowedApps()`,
explicitly warning against "fixing" any one of them in isolation.

That password-verify dialog was pulled out of this group first, on direct
request ("non è material" — it visually stood out against a 100%-Compose
app), replaced by a shared `ui/screens/PasswordVerifyDialog.kt` (also used
by `BlockScreen`'s own unlock dialog, Compose from the start, predating
this native-dialogs note entirely). The other two ("uniforma" — make it
consistent) were converted the same way right after, closing out the
group rather than leaving a two-native-one-Compose split:

- **`WeeklyGoalDialog`** (`HistoryScreen.kt`) — the `RadioGroup` (goal
  type: sessions/minutes) becomes two Material3 `RadioButton`s in a `Row`,
  each wrapped in `Modifier.selectable(role = Role.RadioButton)` so tapping
  the label selects it too, not just the small circle (`RadioButton`'s
  own `onClick = null`, the wrapping `Row` handles the click — the
  standard Compose pattern for this). Colors are restricted to
  `primary`/`onSurface` via `RadioButtonDefaults.colors(...)`, not the
  default (which reads `onSurfaceVariant` for the unselected state — an
  uncustomized-per-palette role, the same trap documented for `Switch` in
  `home-and-settings/design.md` and for the mascot marks in
  `mascot-marks/design.md`). The `EditText` numeric target field originally
  became an `OutlinedTextField` with `KeyboardType.Number` — later replaced
  entirely (see "Preset target chips" below) with a preset chip row, no
  free-form number entry at all.
- **`ClearHistoryConfirmDialog`** (`HistoryScreen.kt`) — a plain
  title+message+confirm/cancel `AlertDialog`, no state of its own.
- **`HistoryActivity` now owns only two booleans**
  (`showGoalDialog`/`showClearConfirmDialog`) plus what happens on
  save/confirm (write through `weeklyGoalManager`/`historyManager`, refresh
  `goal`/`sessions`) — same shape as `SettingsActivity`'s
  `onManageAppsVerified`/`BlockScreen`'s `onUnlocked`. All per-dialog form
  state (selected goal type, target text, validation error) lives inside
  the dialog composables themselves, not hoisted in the Activity — same
  reasoning as `PasswordVerifyDialog`: since each is only ever composed
  inside `if (show) { ... }`, Compose tears down its `remember`ed state on
  dismiss, so reopening is naturally blank without a manual reset.
- **Trigger paths stayed exactly as they were**: `WeeklyGoalDialog` still
  opens from a Compose button inside `HistoryScreen` itself
  (`onEditGoal`); `ClearHistoryConfirmDialog` still opens from the native
  ActionBar overflow menu (`onOptionsItemSelected`, `MENU_CLEAR` — the menu
  itself is unavoidably a platform `Menu`/`MenuItem`, `WITH_ACTION_BAR`
  `themeVariant`, not something to convert to Compose) — only what each
  trigger *shows* changed, not how it's reached.

## Card-based visual redesign

Before this pass, `HistoryScreen` was the one remaining screen not using
the tinted-card language established everywhere else (`SettingsScreen.kt`:
`Surface(shape = RoundedCornerShape(20.dp), color = primary.copy(alpha =
0.06f))` sections with `onSurface.copy(alpha = 0.08f)` dividers between
subsections). Requested directly ("fai una proposta migliore di UI/UX
coerente con il resto dell'app"):

- **`StatsBar`** (sessions/total time/completed) is now that same tinted
  `Surface` instead of a flat `Row.background(colorScheme.surface)` bar —
  it stays the non-scrolling top bar it always was (see the class doc for
  why: mirrors the old `NestedScrollView` layout), just re-skinned.
- **`WeekOverviewCard`** (new) groups streak text + `WeeklyChart` + weekly
  summary + `WeeklyGoalSection` into one tinted card with a divider before
  the goal section — previously these floated directly on the page as
  separate elements.
- **`SessionRow`** is now a `Card` tinted `primary.copy(alpha = 0.04f)`,
  not `Row.background(colorScheme.surface)` — the same contrast bug just
  fixed in `AllowedAppsScreen.kt` (`surface` equals `background` in every
  light palette per `CalmOtterTheme.kt`, so a "plain surface" row was
  indistinguishable from the page itself in light mode).
- **"Set goal"/"Edit goal"** is now a `FilledTonalButton` instead of a
  `TextButton`, with explicit `containerColor`/`contentColor` (`primary`
  at 0.14f alpha / `primary`) rather than
  `ButtonDefaults.filledTonalButtonColors()`'s default, which reads
  `secondaryContainer`/`onSecondaryContainer` — roles `CalmOtterTheme.kt`
  never customizes per palette, the same "surfaceVariant trap" documented
  in CLAUDE.md and fixed for `Switch`/`Checkbox` elsewhere in the app.
- **Deliberately not touched: `Modifier.calmBackground()`.** It was
  briefly considered for this screen too, but `CalmBackground.kt`'s own
  doc comment already rules it out on purpose: it's reserved for
  Home/Onboarding/BlockScreen — "opening" or active-session screens — and
  explicitly *not* Settings/History, which stay navigation/administration
  screens. Applying it here would have contradicted that documented
  boundary rather than extended it.
- **`LinearProgressIndicator`'s `trackColor`** (goal progress bar) is now
  explicit (`onSurface.copy(alpha = 0.12f)`), not the M3 default — which
  reads `surfaceVariant`, rendering a fixed lavender track regardless of
  the selected palette. Same trap, one more instance of it.

## Preset target chips (`WeeklyGoalDialog`)

Requested directly ("invece della input box cosa possiamo inserire?"):
the numeric target `OutlinedTextField` + keyboard was replaced with a row
of preset chips, one per `GoalType`, using the exact same pill style as
`DurationChipRow` in `MainScreen.kt` (Home's own pause-duration picker) —
`Surface(shape = RoundedCornerShape(50), color = primary.copy(alpha =
0.22f selected / 0.08f unselected))`, not `FilterChip`, whose default
colors read the same uncustomized `secondaryContainer`/`surfaceVariant`
roles as everything else in this trap. `GoalTargetChipRow` (private to
`HistoryScreen.kt`) duplicates that style rather than importing it, since
`DurationChipRow` is private to `MainScreen.kt`.

Presets: `SESSION_GOAL_PRESETS = [3, 5, 7, 10, 14]`,
`MINUTE_GOAL_PRESETS = [120, 180, 300, 420, 600]` (2h/3h/5h/7h/10h, labeled
via the existing `formatMinutes()`). Both start above the trivial floor —
1 session or a few minutes a week would be met by accident — on explicit
request ("partirò da 3 a crescere per le sessioni, per le ore da 2h... un
minimo di sfida!"), and both lists share the same "5 presets, widening
gaps" shape despite different starting points. Switching `GoalType` resets
`selectedTarget` to the new type's first preset *only* if the current
value isn't already one of the new type's presets (so re-opening the
dialog on an existing goal, or flipping type and back, doesn't silently
change the selection). No validation state exists anymore — every preset
is by construction a valid target, so `weekly_goal_invalid` and
`weekly_goal_target_hint` were deleted from both `strings.xml` files as
dead resources rather than left unused.
