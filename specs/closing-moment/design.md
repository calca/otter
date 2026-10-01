# Closing Moment — Design

> **Status: Proposed — not implemented.**

## Key files (planned)

| File | Role |
|---|---|
| `SessionRecord.kt` | Two new columns: `mood` (`Int?`, 0 calm / 1 ordinary / 2 hard) and `note` (`String`, default `""`) |
| `CalmOtterDatabase.kt` | Version bump with `MIGRATION_3_4` (`ALTER TABLE sessions ADD COLUMN ...`), schema JSON exported, migration test extended |
| `SessionRecordDao.kt` | `updateReflection(id, mood, note)` |
| `ui/screens/ClosingMomentScreen.kt` (new) | The screen; root applies `safeDrawingPadding()` like every top-level screen |
| `MainActivity.kt` | After `onExpiredNaturally`, shows the closing moment instead of returning straight to Home |
| `HomeSummary.kt` | The "Pause ended at…" line becomes clickable when the pending summary has no reflection yet |
| `HistoryScreen.kt` / `SessionCsvExporter.kt` | Show and export the two fields |

## Which session it refers to

`endSession()` inserts the `SessionRecord` and today returns nothing. It
will return the inserted row id (Room `@Insert` returns `Long`), stored next
to the pending background summary (`KEY_PENDING_SUMMARY_*` in
`SessionManager`) so a pause that ended in the background can still be
answered from Home later. One pending reflection at a time is enough: a new
completed pause replaces the previous unanswered one.

## The screen

Same visual language as the release step and the Home pond: otter at
`OtterMarkSize`, the duration in the countdown style ("Un'ora di calma"),
three choice chips (not emoji: the otter faces from `mascot-marks` if a
calm/neutral/tired variant exists, otherwise text chips), a single-line
`OutlinedTextField` and two actions: "Done" (filled CTA, enabled after a
chip or a note) and "Skip" (text button). No timer, no auto-dismiss.

## Shared migration

`closing-moment`, `slow-exit` and `together-activity` each add a column to
`SessionRecord`. If they are built in the same round, they share **one**
version bump and one migration (`MIGRATION_3_4`) instead of three. The app
is not distributed, but the migration is still written and tested: it is
cheap and keeps the history on the devices already in use.

## Decisioni aperte

1. **Le tre risposte:** parole ("Calma", "Normale", "Faticosa") o facce
   dell'otter. Raccomandato parole, finché non esistono tre varianti
   dell'otter coerenti.
2. **Anche dopo una pausa di gruppo?** Raccomandato sì, uguale.
3. **Nota:** 60 caratteri, una riga. Più lunga diventa un diario.
