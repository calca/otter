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
| `HistoryActivity.kt` | ActionBar chrome + the two non-Compose `AlertDialog`s (goal editor, clear confirmation) |
| `ui/screens/HistoryScreen.kt` | Stats bar, streak, weekly chart, goal progress, session list, empty state |
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

## Non-Compose dialogs

The weekly-goal editor and the clear-history confirmation are built with
`AlertDialog.Builder` + raw `View`s (`RadioGroup`, `EditText`,
`LinearLayout`), not Compose `AlertDialog`. This used to be documented as a
deliberate, consistent choice across the codebase for one-off native
dialogs, including what was then `MainActivity.promptPasswordThenOpenAllowedApps()`
— explicitly warning against "fixing" any one of them in isolation.

That password-verify dialog was pulled out of this group anyway, on direct
request ("non è material" — it visually stood out against a 100%-Compose
app in a way the other two apparently don't prompt the same complaint) and
replaced by a shared `ui/screens/PasswordVerifyDialog.kt`, now also used by
`BlockScreen`'s own unlock dialog (which was Compose from the start,
predating this native-dialogs note entirely). This is a deliberate,
acknowledged exception, not an oversight: the weekly-goal editor and
clear-history confirmation below remain native by the same original
reasoning as before — don't convert them "to match" without their own
explicit request, since nothing about *them* was reported as a problem.
