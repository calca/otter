# Surprise Pauses — Design

> **Status: Implemented** (2026-10-15).

## Shape: a field on `ScheduledPause`, not a new feature

Everything a surprise pause needs already exists for scheduled pauses:
`ScheduleManager` storage, `ScheduleAlarms` (exact when "Alarms &
reminders" is granted, `setAndAllowWhileIdle` otherwise), the heads-up,
`ScheduledPauseReceiver`, `passwordNeededToSave`, `AppScheduler.reconcile()`
on open/boot/time change, the editor page and the list. So:

```kotlin
data class ScheduledPause(
    …,
    val startMinuteOfDay: Int,        // set time; for a surprise: window start
    val windowEndMinuteOfDay: Int = -1, // -1 = set time (today's schedules)
    val drawnAt: Long = 0L,           // next start drawn for a surprise; 0 = none yet
)
```

Storage: two more fields on the `|` line (10 total). 8-field lines (today)
read as `windowEnd = -1, drawnAt = 0`, so existing schedules don't change.

## The draw

`nextOccurrence()` stays pure and stays the only place that knows about
days, DST and `skipUntil`. A surprise adds one pure step:

```kotlin
fun nextSurpriseStart(schedule, now, zone, random: Random): Long?
```

- finds the next day (same loop as `nextOccurrence`) whose window still has
  room after `now`: `latestStart = windowEnd - duration`;
- draws a minute in `[max(from, now+6 min), latestStart]`: the 6 minutes
  leave room for the 5-minute heads-up. A day where nothing is left moves
  on to the next day;
- returns the start in millis (local time → zone, like the DST fix).

**Persisted, then reused.** `ScheduleAlarms.arm()` for a surprise:

1. if `drawnAt > now` → arm on `drawnAt` (no new draw);
2. otherwise → draw, `manager.save(schedule.copy(drawnAt = …))`, arm.

That's what makes criterion 1.4 hold: `reconcile()` runs on every app
open, and without the saved draw every open would roll again. After the
start (or a skip) the receiver clears `drawnAt` and arms again, which
draws the next one.

Editing a schedule clears `drawnAt` (a new window means a new draw). This
is not a loophole on protected ones, because narrowing or moving the
window already needs the password, and widening it is allowed.

`random` is passed in so the tests are deterministic. `kotlin.random.Random`,
not `SecureRandom`: nobody is attacking the draw. Someone who reads the
app's private storage on a rooted phone can already switch off the
Accessibility service.

## Password rules

`isLooserThan` gains:

- `old.isSurprise && !isSurprise` (back to a set time: predictable again);
- for two surprises: `windowStart > old.windowStart || windowEnd < old.windowEnd`
  (narrower or moved). A wider window is stricter.
- `startMinuteOfDay != old.startMinuteOfDay` stays for set-time schedules.
  For surprises the rule above replaces it, otherwise widening the window
  earlier would read as "moved".

All of these stay in the pure `isLooserThan` / `passwordNeededToSave`,
with tests.

## UI

Editor (`ScheduleEditorScreen`):

- In the "When" card, under Duration, a **"Surprise"** row with a switch
  and a one-line hint ("Starts at a random moment within a time window"),
  styled like "Protected by password". It started as two `CalmPill`s above
  the time ("At a set time" / "Surprise"), dropped in review because they
  weighed more than the time itself. For a surprise, the large time becomes
  "14:00 – 18:00": two tappable times at 40 sp, each opening the same
  `TimePickerDialog` titled "Not before" / "Over by". The summary line
  reads "Mon–Fri · for 30 min, at a surprise time".
- `surpriseWindow(from, to, duration)` (pure) keeps the window valid
  without error messages (criterion 1.2): "to" rises to the minimum, and if
  that would pass midnight "from" moves back. A later "from" carries "to"
  along. Changing the duration re-applies it.

List row: "14:00–18:00 · 30 min", with "Mon–Fri · surprise" under it. No
icon.

## As built: notes

- **One draw per day.** After a surprise pause starts, `drawnAt` stays in
  the past and marks that day as used: the next draw starts from the
  following day, so the re-arm in the receiver doesn't put a second pause
  in the same afternoon.
- **"Skip the next one"** sets `skipUntil` to the end of that day's window
  (`surpriseWindowEndOn`), not to the drawn instant. Otherwise the new draw
  could land later in the same window. It also clears `drawnAt`.
- **The draw is redone only when something it depends on changes**
  (`drawChangedFrom`: days, duration, window). Saving the editor unchanged
  keeps the draw, so a protected surprise can't be rerolled by opening and
  saving it.
- **Corrupted data doesn't crash.** A window outside the day (only
  reachable by hand-editing storage) draws nothing. Before this guard it
  crashed `MainActivity` on open, inside `armAll`. Found on the emulator.
- **Verified on the API 37 emulator.** The draw stays the same across three
  app opens and the heads-up is armed 5 minutes earlier. At the drawn time
  the pause starts and the block covers the app already in front (issue
  #1's fix), and the next draw moves to the following day.

Strings (IT / EN):

| key | IT | EN |
|---|---|---|
| `schedule_kind_fixed` | A un'ora precisa | At a set time |
| `schedule_kind_surprise` | A sorpresa | Surprise |
| `schedule_window` | tra le %1$s e le %2$s | between %1$s and %2$s |
| `schedule_surprise_heads_up` | Tra 5 minuti parte una pausa a sorpresa | A surprise pause starts in 5 minutes |

## Tests

- `nextSurpriseStart`: inside the window and ending inside it; the
  6-minute margin; the day's window already over → next valid day; skip;
  DST day; fixed seed → fixed result.
- `ScheduleManager`: 8-field lines still read; 10-field round trip.
- `isLooserThan`: the three new cases, plus "wider is not looser".
- Receiver: after the start, `drawnAt` is cleared and the next draw is in
  the future.

## Decisions (2026-10-15)

1. **Name**: "A sorpresa" / "Surprise" (decided). "Casuale" sounded like a bug.
2. **Heads-up**: 5 minutes before, as for scheduled pauses (decided).
3. **Minimum window**: duration + 1 h (decided). With + 30 min the
   start falls inside half an hour and is nearly predictable.
4. **No windows across midnight** (decided): `nextOccurrence` stays
   simple, and pauses at night aren't wanted (see usage-pause).

No open decisions left.
