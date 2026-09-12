# Multi-Theme System — Requirements

## Context

Calm Otter offers three color palettes. This is a pure aesthetic
preference, applied consistently across every screen and across both the
XML-driven window chrome and the Compose content within it.

## User Story 1: Choosing a palette

As the phone's user, I want to pick between three palettes (Sage, Lavender,
Terracotta), so the app matches my preference.

### Acceptance Criteria

1. WHEN the user picks a palette in Settings (see
   `home-and-settings/requirements.md` — the picker moved off the main
   screen in that split) THEN the system SHALL persist the choice and SHALL
   recreate Settings itself so the new colors apply there immediately,
   without requiring an app restart.
2. WHEN the user then returns to Home THEN the system SHALL show the new
   palette immediately too — both the Compose content and the status bar
   — even though Home was resumed, not recreated, when Settings closed
   (see `design.md`'s "Applying a new theme without `recreate()`" for how;
   this used to require killing and relaunching the app before the fix).
3. WHEN any screen is created THEN the system SHALL apply the persisted
   palette (defaulting to Sage if none was ever chosen) before that
   screen's first frame is drawn — no visible flash of the wrong palette.
4. WHEN a screen needs a variant beyond the base look — an ActionBar
   (settings-style screens) or the full-bleed block screen — THEN the
   system SHALL apply the matching palette+variant combination, not just
   the base palette.

## Out of scope

- The actual color values per palette (Sage/Lavender/Terracotta ×
  light/dark) are asset content, not behavior — see `design.md` for where
  they live if a value needs correcting.
