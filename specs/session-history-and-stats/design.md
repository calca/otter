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
  type: sessions/minutes) first became two Material3 `RadioButton`s, then
  was replaced again by a pill-chip row (see "Preset target chips" below —
  both the type and the target ended up as chip rows, one shared idiom).
  The `EditText` numeric target field originally became an
  `OutlinedTextField` with `KeyboardType.Number` — also later replaced
  entirely with a preset chip row, no free-form number entry at all.
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
  ActionBar menu (`onOptionsItemSelected`, `MENU_CLEAR` — the menu
  itself is unavoidably a platform `Menu`/`MenuItem`, `WITH_ACTION_BAR`
  `themeVariant`, not something to convert to Compose) — only what each
  trigger *shows* changed, not how it's reached. (That menu was an
  overflow at the time; it later became two always-visible icons — see
  "From an overflow menu to two ActionBar icons" below — but it's still
  the same `onOptionsItemSelected` path.)

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

## Preset target chips + chip type selector (`WeeklyGoalDialog`)

Requested directly ("invece della input box cosa possiamo inserire?"):
the numeric target `OutlinedTextField` + keyboard was replaced with a row
of preset chips, one per `GoalType`, using the exact same pill style as
`DurationChipRow` in `MainScreen.kt` (Home's own pause-duration picker) —
`Surface(shape = RoundedCornerShape(50), color = primary.copy(alpha =
0.22f selected / 0.08f unselected))`, not `FilterChip`, whose default
colors read the same uncustomized `secondaryContainer`/`surfaceVariant`
roles as everything else in this trap.

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

Immediately after ("invece dei radio button, si può fare di meglio?"),
the type selector (sessions/minutes) was converted from two
`RadioButton`s to a second chip row using the exact same pill style —
one selection idiom for the whole dialog instead of two. Both rows are
now `GoalChipRow<T>` (private, generic over the chip's value type: `T =
GoalType` for the 2-item non-scrolling type row, `T = Int` for the
5-item horizontally-scrolling target row) instead of two separate
near-duplicate composables. `RadioButton`/`RadioButtonDefaults`/
`Modifier.selectable`/`Role.RadioButton` were removed from this file
entirely along with their now-unneeded imports.

## From an overflow menu to two ActionBar icons

`HistoryActivity`'s two actions — export and clear — both sat behind the
`⋮` overflow (`SHOW_AS_ACTION_NEVER`). On request ("invece del menù con i
tre puntini, possiamo mettere 2 icone materiali?") they were promoted to
always-visible icons (`SHOW_AS_ACTION_ALWAYS`) with `setIcon(...)`. With
exactly two actions, the overflow was spending a tap to hide what fits in
the bar.

- **Export uses the Material *share* glyph, not a download/save one.**
  `exportHistory()` writes the CSV to `cacheDir` and opens an
  `ACTION_SEND` chooser — nothing lands anywhere the user could go back
  and find. A download icon would promise a file in Downloads that
  doesn't exist.
- **Clear uses the trash glyph and still goes through
  `ClearHistoryConfirmDialog`.** The confirmation matters *more* now, not
  less: an always-visible icon is easier to hit by accident than a buried
  menu item, and the action is irreversible.
- **Both icons tint with `?attr/colorControlNormal`**, the same attribute
  the ActionBar's own up-arrow uses, so all three icons match on every
  palette and in dark mode without per-theme drawables. Verified by
  sampling pixels rather than by eye: back arrow, share and trash all
  resolve to `#1D1B20` in light and `#A3AAAF` in dark.
- **The `menu.add(...)` titles were kept.** They're not redundant now
  that there are icons — Android surfaces them as the long-press tooltip
  and the TalkBack label (confirmed via `uiautomator dump`: the two items
  expose `content-desc="Export history"` / `"Clear history"`), so the
  icons aren't mute for anyone who doesn't recognise them.

### Side effect: the app now has no overflow popup at all

`HistoryActivity` was the only `onCreateOptionsMenu` in the app, so after
this change nothing shows an ActionBar popup menu. That makes the
`actionBarPopupTheme` / `ThemeOverlay.CalmOtter.*.PopupMenu` block in
`values/themes.xml` — added earlier specifically to stop the History
overflow rendering in the wrong palette — dead for the moment. It was
**left in place deliberately** rather than deleted: it costs nothing,
removing theme attributes risks regressions that wouldn't surface until
some future screen adds a menu, and that future screen would want it
back. If it's still unused when other cleanup happens, that's the time to
drop it.

## The empty state, when there is nothing to show yet

Before the first session, the page was a 100dp otter, two lines of grey
text, and the weekly-goal card — which, with no goal set, contained a
single centred button and nothing else. Three loose elements on an
otherwise white page: it read as a screen that had failed to load rather
than as a page waiting to fill up.

`EmptyHistory` now opens with the otter at 132dp — the same size Home
draws it — inside a 180dp circle tinted `primary` at 5%. The halo is what
ties this page to Home's pond; it is deliberately **static**, because
Home's animated ripples mean "waiting for you to start", which is true
there and not on a read-only page.

Under it sits one line from `history_empty_phrases`, picked at random per
screen opening (`stringArrayResource` + `remember`), italic inside a
`CalmCard`. That array is its own, **not** `pause_phrases`: those go
through `PhraseManager`, which returns null when the user has switched
off phrases during the pause — a preference about a different context
that would leave this page with a hole where the text belongs. The
existing `history_empty` string lost its second line ("Avvia la tua prima
pausa") and became just the title, since the phrase now carries the
invitation.

**And nothing else — no weekly goal here.** `WeeklyGoalSection` used to
be rendered in the empty state too, with `weekSessions`/`weekMinutes` at
0, to keep "Imposta obiettivo" reachable at all times. That was asked for
once and then withdrawn: before the first pause, a goal prompt reads as a
commitment to sign up to before you are allowed to begin, and this app
does not push. The goal now lives only in `WeekOverviewCard`, which
exists only once there are sessions to measure — see User Story 3's
fourth acceptance criterion.

`WeeklyGoalSection` did gain a `weekly_goal_none` line ("Nessun obiettivo
per questa settimana"), shown in place of the progress bar when
`goal == null`. Without it the section was a page-wide card containing
one centred button and no indication of what the card was for. It is
phrased as the missing datum, not as a second invitation: the button
underneath is already the call to action.


## Redesign pass: the session list

Two changes from the Stitch redesign.

**The outcome badge is round.** It used to be a rounded square; the session
row is the only place on the page with a small filled shape, and a circle
ties it to the pause screen's action badges rather than to the cards around
it. Colours are unchanged — `primary` tint for a session that ran its course,
`error` tint for one ended early — and so is the group arc on the otter mark.

**A reflective line closes the list.** The mockup ends the page with a quote
card; it is picked at random from `pause_phrases` on every opening, the same
pool the pause itself draws from, so the register matches. It is read
straight from resources rather than through `PhraseManager`: that manager
answers the "show phrases during the pause" preference, which is worded that
way because it means exactly that — what you are shown while you are stuck
with it. This is the last line of a page you chose to open and leave when you
like.

The mockup also marked night-time sessions with a moon glyph. Dropped on
request: the data exists (start time), but it is one more signal to learn on
a row that already carries date, outcome, companions and duration.


## Redesign pass: eight things, checked against the mockup one by one

Reported together against "Calm Otter - History & Journey" (Stitch project
`3158702940609906617`), none of them touching a number — all layout, all
on `HistoryScreen.kt`/`WeeklyChart.kt`.

**The stats bar scrolls with the page now.** It used to be pinned at the
top, reproducing `activity_history.xml`'s split between a fixed panel and
a `NestedScrollView` underneath — a deliberate choice, preserved through
the first Compose migration. Asked to change: on a page opened rarely and
read top to bottom, a pinned panel earns nothing a single scrolling
region doesn't already give, and one region is simpler to reason about
than two with different behaviour. The session rows stay plain
composables inside the scrolling `Column` either way (`sessions.forEach`,
not a nested `LazyColumn` — that was never about what's pinned).

**A `VerticalDivider` between each stat column.** The mockup's
`divide-x`; ours had none. Same 8%-opacity `onSurface` as every other
divider on this page, and only as tall as the row's own content
(`Row(Modifier.height(IntrinsicSize.Min))`), not stretched to the card's
full padding.

**The streak line is the chart's title, and now looks like one.** It sat
above the bars at 14sp with no icon, doing the job of a heading for
everything below it — chart, summary, goal — without the weight of one.
`SprigMark` (16dp, the same leaf already used for "Pause experience" in
Settings, not a Material icon pulled in for one occurrence) plus 17sp
closes that gap.

**Empty days draw a short grey pill instead of nothing.** `WeeklyChart`
used to skip the bar entirely when a day had zero minutes (`barH`
computed as literal `0f`) — a day with no session vanished from the
chart rather than reading as "none". Now every empty day draws a fixed
3dp pill, `onSurface` at 14% (the same muted tone the goal's own
`LinearProgressIndicator` track already uses in this file), rounded to a
full pill rather than the taller bars' `6.dp` corner radius. "Today"'s
label was already bold and tinted `primary` (`valuePaint`,
`isFakeBoldText`) before this pass — checked directly against the
screenshot rather than assumed, since the code read as already doing it.

**No divider between the chart and the goal.** Summary text
("N sessions this week · Xm") and the goal progress bar are one
continuing thought — "this week" becoming "toward this week's goal" —
not two sections the way distinct cards are. A `HorizontalDivider` sat
between them, cutting that thought in half; the mockup keeps both in one
card with nothing between. Removed, and `WeekOverviewCard`'s own doc
comment (which had described the card as using dividers "same as
Settings' sections") corrected along with it — it doesn't, any more.

**The closing phrase gained the leaf it was missing.** `SprigMark`
(18dp) centred above the quote, matching the mockup's icon there and the
same motif the streak line now also carries — this page uses one leaf
mark for "something reflective, not a metric" throughout, rather than
introducing a second glyph for the same idea.

**The export icon changed from share to download, and the file changed
name with it.** `ic_history_share.xml` was deliberately the Material
"share" glyph, not "download" — its own doc comment explained why: the
action never writes a file anywhere the user could find it again, it
opens an `ACTION_SEND` chooser with the CSV attached
(`HistoryActivity.exportHistory()`), so a download icon would have
promised a file in Downloads that doesn't exist. That reasoning was
sound and is still true of the code — but reported directly against how
it reads in practice: tapping it does hand you a CSV, obtained through a
system chooser rather than written to disk, and that is "I got the
file" to whoever taps it, not "I shared something with someone".
Behaviour is unchanged; only the icon is. Renamed to
`ic_history_download.xml` (the standard Material `file_download` glyph
path, same licensing basis as `EyeGlyph`'s reused paths elsewhere in
this app) rather than leaving a file called "share" containing a
download icon — `ic_history_delete.xml`'s own comment, which pointed to
the old file for its tint reasoning, updated to match.

Verified on-device, full-resolution screenshots cropped to each region
rather than judged from a shrunk full-page capture — a first crop landed
on the wrong part of the screen entirely (the top bar instead of the
chart) before bounds read off the accessibility tree pinned down the
right coordinates.
