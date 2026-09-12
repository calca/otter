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
   You) THEN the system SHALL have a simplified single-color silhouette
   (head + ears only, no eyes/nose) available for that purpose.

## User Story 2: Onboarding illustration

As a first-time user, I want the introduction step to show something more
considered than a bare emoji, so the first impression matches the rest of
the app's design.

### Acceptance Criteria

1. WHEN the onboarding wizard shows its first step THEN the system SHALL
   render the "Otter at Rest" illustration (floating on its back, holding
   a stone) instead of an emoji.
2. WHEN the user has selected a palette (Sage/Lavender/Terracotta) or a
   light/dark mode THEN the illustration's colors SHALL follow that choice
   — it must not be fixed to one hardcoded color set.
3. Every other onboarding step keeps its existing plain-emoji `StepBody`
   behavior unchanged (🤝 🔑 🔒 🌿) — this feature only touches step 1.

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

## User Story 4: Block-screen mark

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

## Out of scope

- "Otter at Rest" is not used anywhere marketing/store-listing related —
  no store assets were part of this request.
- No splash-screen system was added; "Still Otter" as the launcher icon is
  as far as the app-icon work goes this round.
