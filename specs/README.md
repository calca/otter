# Specs

This directory documents Calm Otter's features as **specifications** — the
convention this project uses for spec-driven development.

## What's here now

The subdirectories below are **as-built specs**: they describe features
already implemented, written after the fact to give a spec-shaped baseline
for the current app. They are not aspirational and not a to-do list —
everything described is live in `main` today.

- [`onboarding-and-password/`](onboarding-and-password/requirements.md) — first-run wizard, password hashing, rate-limited verification
- [`pause-session-core/`](pause-session-core/requirements.md) — session lifecycle, DND, alarm-based expiry, foreground notification, boot restore
- [`app-blocking-and-home-lock/`](app-blocking-and-home-lock/requirements.md) — accessibility-service blocking, allowed-apps whitelist, Home-button interception
- [`session-history-and-stats/`](session-history-and-stats/requirements.md) — Room-backed history, streaks, weekly goal, CSV export
- [`multi-theme-system/`](multi-theme-system/requirements.md) — three-palette theming across XML window chrome and Compose content
- [`home-screen-widget/`](home-screen-widget/requirements.md) — the Glance 1×1 widget
- [`mascot-marks/`](mascot-marks/requirements.md) — the app icon and the two in-app otter illustrations
- [`home-and-settings/`](home-and-settings/requirements.md) — the calm-Home / Settings split
- [`group-pause/`](group-pause/requirements.md) — synchronized multi-device pause: QR/manual code (Phase 1) plus a live Bluetooth lobby with NFC tap-to-connect (Phase 2)

Each feature has:
- `requirements.md` — user stories with EARS-style acceptance criteria (WHEN/THE SYSTEM SHALL), matching current behavior
- `design.md` — the technical shape: key files/classes, data flow, and any non-obvious constraints

## Proposed (not built yet)

Written before the code and marked **Status: Proposed** at the top of each
file. Each `design.md` ends with "Decisioni aperte": the choices to settle
before building. When a feature ships, its pair is rewritten as-built and
moves to the list above.

- [`quick-settings-tile/`](quick-settings-tile/requirements.md) — start a pause from the notification shade
- [`breathing-pause/`](breathing-pause/requirements.md) — a 10-minute pause with a breathing ring
- [`together-activity/`](together-activity/requirements.md) — the host proposes something to do together; it travels with the pause
- [`closing-moment/`](closing-moment/requirements.md) — "How was it?" after a completed pause, saved in History
- [`together-history/`](together-history/requirements.md) — time spent with each companion, and a "Together" filter
- [`scheduled-pauses/`](scheduled-pauses/requirements.md) — recurring pauses that start by themselves; loosening needs the password
- [`allowed-app-profiles/`](allowed-app-profiles/requirements.md) — named allowed-apps lists, chosen per pause
- [`slow-exit/`](slow-exit/requirements.md) — on by default: end a pause without the password after a 10-minute wait
- [`weekly-summary/`](weekly-summary/requirements.md) — one quiet note on Sunday evening

**Suggested order**, smallest and least risky first, grouping the ones that
share work:

1. `quick-settings-tile`, `breathing-pause` — small, no schema change. The
   tile also tests starting a session from a non-Activity context, which
   `scheduled-pauses` needs.
2. `together-activity`, `closing-moment`, `slow-exit` — all three add a
   column to `SessionRecord`: built in the same round, they share one
   database version bump and one migration.
3. `together-history` — reads what step 2 stores.
4. `scheduled-pauses` — after a spike on starting the foreground service
   from an inexact alarm (see its design).
5. `allowed-app-profiles` — scheduled pauses then gain a profile.
6. `weekly-summary`.

## Convention for new features

For anything new that touches multiple files or needs an architectural
decision, use `/feature-dev` (see CLAUDE.md) — it runs a 7-phase guided
workflow (discovery → codebase exploration → clarifying questions →
architecture options → implementation → review → summary) and pauses for
your input at the key decision points.

`/feature-dev` itself is conversational and doesn't write spec files to
disk. If you want the resulting requirements/design captured here for future
reference (e.g. before a large or security-sensitive change), ask for a new
`specs/<feature-slug>/requirements.md` + `design.md` pair in the same shape
as the existing ones once the phase-3/phase-4 discussion has settled.

## What this is not

Not a replacement for `design.md` at the repo root — that file is a Stitch
design-token reference (colors, typography, spacing) for a UI redesign
exploration, unrelated to feature specs.
