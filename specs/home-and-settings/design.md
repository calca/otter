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
