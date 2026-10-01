# Multi-Theme System — Requirements

## Context

Calm Otter offers four color palettes. This is a pure aesthetic
preference, applied consistently across every screen and across both the
XML-driven window chrome and the Compose content within it.

## User Story 1: Choosing a palette

As the phone's user, I want to pick between four palettes (Sage, Still
Water, Dusk Sand, Dawn Clay), so the app matches my preference.

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
4. WHEN a screen needs an ActionBar (settings-style screens) THEN the
   system SHALL apply the matching palette on top of that structure, not
   just the base look.
5. WHEN the device is in dark mode THEN each palette's picker swatch in
   Settings SHALL show that palette's actual dark-mode accent color,
   matching what selecting it applies — not the light-mode color
   regardless of system theme.
6. The color values of each palette SHALL be defined in exactly one place
   (`values/colors.xml` and its `values-night/` twin), read by both the
   window theme and the Compose content, so that changing a color never
   requires editing a second copy.
7. Each palette SHALL define every role in both light and dark modes, with
   text and call-to-action contrast of at least 4.5:1, and no two palettes
   SHALL share (nearly) the same hue — enforced by a unit test.

## Out of scope

- The actual color values per palette (Sage/Still Water/Dusk Sand/Dawn Clay ×
  light/dark) are asset content, not behavior — see `design.md` for where
  they live if a value needs correcting.
