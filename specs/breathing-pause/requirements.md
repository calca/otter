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

## Out of scope

- Configurable breathing patterns.
- Sounds or haptics.
