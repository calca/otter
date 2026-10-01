# Breathing Pause — Requirements

> **Status: Proposed — not implemented.**

## Context

The shortest pause today is 30 minutes. Many moments that call for a pause
are short and tense: before a meeting, after an argument, when the thumb
goes to the feed by itself. A 10-minute pause with a breathing rhythm fits
those moments.

## User Story 1: A short pause that breathes

As the phone's user, I want a 10-minute pause that guides my breathing, so
I can step away even when I don't have half an hour.

### Acceptance Criteria

1. Home SHALL offer a "10 min" option before "30 min" in the duration
   pills.
2. WHEN a 10-minute pause is active THEN the block screen SHALL show the
   ring slowly expanding and contracting (about 4 s in, 6 s out) and the
   words "Breathe in" / "Breathe out" in place of the phrase.
3. WHEN the system "remove animations" setting is on THEN the ring SHALL
   stay still and the text SHALL read "Breathe slowly".
4. A breathing pause SHALL otherwise behave like any pause: DND, blocking,
   password, History, streaks and goals.

## User Story 2: The app remembers my duration

As the phone's user, I want Home to open with the duration I chose last
time, so I don't have to pick it again every time.

### Acceptance Criteria

1. WHEN the user taps a duration pill on Home THEN the system SHALL
   remember it, even if no pause is started.
2. WHEN a pause starts with a duration chosen by the user anywhere else
   (Time together, a duration picked in another screen) THEN the system
   SHALL remember that duration too.
3. WHEN Home opens THEN the remembered duration SHALL be preselected;
   with nothing remembered, the default stays 1 h.
4. The widget and the Quick Settings tile SHALL use the same remembered
   duration: one value for the whole app.
5. A scheduled pause SHALL NOT change the remembered duration, since it is
   not a choice made in that moment.
6. WHEN the remembered duration is no longer among the offered ones THEN
   Home SHALL fall back to the default.

## Out of scope

- Configurable breathing patterns.
- Sounds or haptics.
