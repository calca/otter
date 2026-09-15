# Mascot Marks — Requirements

## Context

Calm Otter had no launcher icon at all (no `mipmap-anydpi-v26/`, no
`android:icon` on `<application>` — a genuine gap, not something these
marks replace) and used the 🦦 emoji as a placeholder in two spots. This
introduces three purpose-built otter marks, each scoped to the one job it's
actually good at, replacing the emoji and filling the icon gap.

## User Story 1: App icon

As a user browsing my home screen or app drawer, I want Calm Otter to have
a real, recognizable icon, so it doesn't look unfinished next to every
other app.

### Acceptance Criteria

1. WHEN the app is installed THEN the system SHALL show an adaptive icon
   (foreground otter head on a solid Sage background) as both the launcher
   icon and the round icon variant.
2. WHEN the OS applies any adaptive-icon mask (circle, squircle, rounded
   square) THEN the otter head SHALL stay fully within the mask's safe
   zone — no ear or facial feature clipped by any of the three shapes.
3. WHEN the device supports themed/monochrome icons (Android 13+, Material
   You) THEN the system SHALL have a simplified single-color version
   available for that purpose — head + ears silhouette, with the eyes and
   nose cut through as transparent holes so it still reads as an otter
   face instead of a plain blob (see design.md — an earlier head+ears-only
   version without them shipped first and was found, on a real device
   next to other apps' themed icons, to be indistinguishable from a
   generic rounded shape).
4. WHEN the themed icon is rendered THEN the ears SHALL be solid, joined
   to the head — a later fix for criterion 3 punched them into crescents
   by switching the path to `evenOdd`, which subtracts the ear/head
   overlap; see design.md.
5. Any change to this icon's fill rule SHALL be verified by rendering the
   complete silhouette, not only the detail being changed, since a fill
   rule applies to every subpath in the path.

## User Story 2: Onboarding illustration

As a first-time user, I want the introduction step to show something more
considered than a bare emoji, so the first impression matches the rest of
the app's design.

### Acceptance Criteria

1. WHEN the onboarding wizard shows its first step THEN the system SHALL
   render the same otter mark used on Home (`OtterFloatMark`, the "Living
   Pond" mark — see `home-and-settings/design.md`) instead of an emoji, so
   the very first thing a new user sees is the mascot they'll meet again
   every time they open the app.
2. WHEN the password step ("Choose the password together") is shown THEN
   the system SHALL render `PactPawsMark` (two paws tilted toward each
   other in a circular badge) instead of the `🔒` emoji.
3. WHEN the final step ("All set") is shown THEN the system SHALL render
   `SprigMark` (a hand-drawn two-leaf sprig) instead of the `🌿` emoji used
   as that step's illustration — at the time, the same emoji still
   appeared inline in that step's title *string* ("All set 🌿"), out of
   scope for this story; a later, separate pass removed it (and every
   other remaining emoji in the app's strings and the widget) — see
   design.md's "No emoji anywhere in user-facing text or the widget".
4. WHEN the user has selected a palette (Sage/Lavender/Terracotta) or a
   light/dark mode THEN every mark's colors SHALL follow that choice — none
   are fixed to one hardcoded color set.

This step originally showed a dedicated "Otter at Rest" illustration
(floating on its back, holding a stone), later removed in favor of reusing
`OtterFloatMark` — one fewer mark to keep visually consistent, and a more
deliberate first impression (see "Superseded marks" below). The password
and final steps originally used plain emoji instead of a mark at all; both
were replaced once the same "not on-brand" problem was reported for them
too (see design.md's "Replacing onboarding's emoji icons").

## User Story 3: Widget idle state

As a user with the home-screen widget placed, I want its idle state to
show a proper mark instead of an emoji glyph, matching the rest of the
brand.

### Acceptance Criteria

1. WHEN no session is active THEN the widget SHALL show the "Still Otter"
   mark instead of the 🦦 emoji, at a size and contrast that hold up in a
   1×1 widget cell.
2. The widget's own fixed color scheme (light sage background,
   `#2C4A3E`-family text) is unaffected — the mark used here is
   contrast-matched to that background specifically, not theme-adaptive
   (Glance widget content already isn't theme-adaptive today, see
   `home-screen-widget/design.md`).

## User Story 4: Block-screen mark (superseded — see "Superseded marks" below)

As the person seeing the block screen, I want the one screen where
"paused" is literally what's happening to say so visually, not just in
text next to a generic countdown.

### Acceptance Criteria

1. WHEN the block screen (or the Home-button block variant) is shown THEN
   the system SHALL display the "Paws Together" mark above the title —
   two otter paws pressed together, doubling as a pause glyph.
2. The mark SHALL use only the two theme roles proven to be
   palette-adaptive in this app (`primary`, `onPrimary`) — see design.md
   for why the obvious `surfaceVariant`/`primaryContainer` roles are
   unsafe here.

A later `BlockScreen` redesign replaced this mark and its title entirely —
see "Superseded marks" below and design.md's "Replacing `PausePawsMark`".

## Out of scope

- No splash-screen system was added; "Still Otter" as the launcher icon is
  as far as the app-icon work goes this round.

## Superseded marks

"Otter at Rest" (`OtterAtRestIllustration`, a lying-down otter holding a
stone) was the original onboarding-step-1 mark. It's gone from the
codebase now — User Story 2 above reuses `OtterFloatMark` (the Home mark)
instead — but it's worth recording why it existed and what went wrong with
it, since the next mark added to this file will hit the same traps:
1. It was the first mark written against `surfaceVariant`/`primaryContainer`
   and hit the trap documented in design.md — fixed at the time by moving
   to `primary` at two alpha levels, the same recipe `OtterFloatMark` uses.
2. Separately, drawing its body/head/ears/paws as multiple overlapping
   `drawCircle`/`drawRoundRect`/`drawOval` calls of the *same* semi-transparent
   color caused a compositing artifact (overlap regions look darker, since
   the alpha of two stacked draws compounds) — the same bug later found in
   `OtterFloatMark`'s ears vs. muzzle. Both were fixed by merging same-color
   overlapping shapes into one `Path` per fill color, drawn as a single
   `drawPath` call. See design.md's "One Path per fill color" section.

`PausePawsMark` ("Paws Together", User Story 4 above) is also gone now —
`BlockScreen` was redesigned to reuse `OtterFloatMark` plus the same
`ProgressRing` `MainScreen` draws during an active session, instead of its
own static badge, and the mark had no other caller left once that happened.
See design.md's "Replacing `PausePawsMark`" for the full reasoning; nothing
about its construction was wrong (unlike the two marks above, it never hit
either trap) — it was simply superseded by a decision to make every
"session active" screen share one visual language rather than each having
its own.
