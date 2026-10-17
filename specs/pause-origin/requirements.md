# Pause Origin — Requirements

> **Status: Implemented** (2026-10-17).

## Context

Three kinds of pause start without a gesture from the user at that moment:
scheduled, surprise, and after long use. Each has a heads-up notification
five minutes before, but it is easy to miss (a full-screen video, a shade
nobody opens, notifications not allowed). Then the block screen appears,
identical to a pause started by hand, and the user is surprised without
knowing why (reported).

## User Story 1: Knowing why the screen is blocked

As the phone's user, when a pause starts by itself, I want the block
screen to tell me where it came from, so that it feels like the pact I
made and not like a malfunction.

### Acceptance Criteria

1. WHEN a pause starts by itself THEN the status label on the block screen
   SHALL name its origin **in place of** "In pausa", on one line (never a
   second line): "Pausa delle 21:00" (scheduled, the time as set),
   "Pausa a sorpresa" (surprise, no time — as in its heads-up),
   "Pausa dopo 45 min di schermo" (after long use, the rule's minutes).
2. A pause started by hand (Home, widget, tile, together) SHALL keep
   "In pausa".
3. The ongoing pause notification SHALL use the same text as its title,
   so the reason is reachable from the shade with the screen locked. A
   group pause keeps its "Time together" title.

## User Story 2: A welcome instead of a jolt

As the phone's user, when a pause starts by itself while I'm using the
phone, I want a word from the otter first, so that the change of screen
isn't abrupt.

### Acceptance Criteria

1. WHEN the block screen shows an automatic pause for the first time THEN
   it SHALL show, in place of the reflective phrase, a welcome matching the
   origin ("È l'ora della pausa che avevi programmato.", "Sorpresa! Una
   pausa che non ti aspettavi.", "Lo schermo è acceso da un po':
   prendiamoci una pausa."), then fade to the usual phrase after about
   8 seconds.
2. The welcome SHALL appear once per pause: the block screen reopens at
   every blocked app, and a repeated welcome would be noise.
3. The welcome SHALL appear also in a breathing pause (≤ 10 minutes, where
   the phrase is otherwise absent), since the usage pause is usually one.
