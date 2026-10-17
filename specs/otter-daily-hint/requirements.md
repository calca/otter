# Otter Daily Hint — Requirements

> **Status: Implemented** (2026-10-17).

## Context

The bubble over the otter ("Toccami quando vuoi una pausa") used to appear
only before the very first pause, then never again. Someone who opens the
app rarely can forget that the otter is what you tap. The bubble only shows
with the app open, so it doesn't fix forgetting outside the app — scheduled
pauses, the usage pause, the widget and the tile do that — but it nudges
whoever opens the app for something else, and it can point to the
automatic pauses.

## User Story 1: A daily nudge

As someone who uses the app now and then, when I open Home I want the
otter to invite me to a pause, so that I remember it's there.

### Acceptance Criteria

1. The first-pause bubble SHALL stay as it is: until the first pause ever,
   until tapped, never auto-hidden.
2. After that, a daily bubble SHALL appear at most **once a day**, on the
   first Home opening of the day, and only IF no pause started today. It
   counts as shown as soon as it is picked: reopening Home the same day
   SHALL NOT show it again, tapped or not.
3. Its text SHALL be a random phrase from a short list, always an
   invitation — never a reproach, a count of missed days or a streak.
4. It SHALL disappear by itself after about 8 seconds, or when tapped.
5. IF the user has no scheduled pauses THEN about one time in four the
   phrase SHALL be "Ti dimentichi? Posso partire da solo: tocca qui.", and
   the tap SHALL open Scheduled pauses.
6. A switch in Settings → Automatic ("Invito del giorno", on by default)
   SHALL turn the daily bubble off. It does not affect the first-pause one.
7. No notification: the nudge lives only inside the app.
