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
  `mascot-marks/design.md`). The `EditText` numeric target field becomes
  an `OutlinedTextField` with `KeyboardType.Number`. Validation
  (`target == null || target <= 0`) used to show `weekly_goal_invalid` as
  a `Toast`; now it's inline error text under the field instead (same
  pattern `PasswordVerifyDialog` already established for wrong-password),
  since a `Toast` appearing behind/under an already-open `AlertDialog`
  reads worse than text inside the dialog itself.
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
