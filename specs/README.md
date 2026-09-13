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
- [`group-pause/`](group-pause/requirements.md) — synchronized multi-device pause via QR/manual code (Phase 1); live Bluetooth/NFC lobby documented as deferred Phase 2

Each feature has:
- `requirements.md` — user stories with EARS-style acceptance criteria (WHEN/THE SYSTEM SHALL), matching current behavior
- `design.md` — the technical shape: key files/classes, data flow, and any non-obvious constraints

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
