# Allowed-App Profiles — Requirements

> **Status: Implemented** (2026-10-01).

## Context

One allowed-apps list doesn't fit every pause. During work hours Maps and
a work chat may be non-negotiable; in the evening nothing should be. Today
the only way to adapt is changing the list (password) before and after,
which nobody does.

## User Story 1: Named lists

As the accountability partner, I want to define a few named lists of
allowed apps, so each kind of pause allows only what it needs.

### Acceptance Criteria

1. The system SHALL have a default profile that always exists and holds
   today's allowed-apps list.
2. The user SHALL be able to add up to four more profiles, each with a
   name and its own allowed apps.
3. Creating, renaming, editing and deleting profiles SHALL require the
   password, exactly as editing the list does today.
4. Phone (the default dialer), the keyboard and system UI SHALL stay
   allowed in every profile, as today (`BlockPolicy`).

## User Story 2: Choosing a profile for a pause

As the phone's user, I want to pick the profile when I start a pause, so
the pause allows what fits the moment.

### Acceptance Criteria

1. WHEN more than one profile exists THEN Home SHALL show the selected
   profile under the duration pills ("Allowed: Evening ›"); tapping it
   picks another. WHEN only the default exists THEN Home SHALL look as
   today.
2. Choosing a profile for the next pause SHALL NOT require the password
   (all profiles were already approved by the password holder).
3. WHEN a pause starts THEN the system SHALL use the chosen profile for its
   whole duration; changing the selection during a pause SHALL NOT be
   possible.
4. The widget, the Quick Settings tile and Time together SHALL use the
   last chosen profile; a scheduled pause SHALL use its own profile.

## User Story 3: Allowlist vs blocklist (issue #2, "Not Allowed Apps")

As the accountability partner, I want some profiles to say "only block
these apps" instead of "only allow these apps", so I can leave the rest of
the phone free and just shut off the one or two things that matter.

### Acceptance Criteria

1. Each profile SHALL have a mode, **allowlist** or **blocklist**, chosen
   independently per profile (not one app-wide setting).
2. In allowlist mode (the default, and the only mode before this story),
   only the profile's apps stay reachable during a pause; everything else
   is blocked, capped at `MAX_ALLOWED_APPS` (3).
3. In blocklist mode, only the profile's apps are blocked during a pause;
   everything else, Home included, stays reachable. There SHALL be no cap
   on how many apps can be picked.
4. Phone, the keyboard and system UI SHALL stay reachable in every profile
   regardless of mode, as today (`BlockPolicy`).
5. Switching a profile's mode SHALL NOT clear its selected apps — the same
   list is reinterpreted under the new mode.

## Out of scope

- Time-based automatic profile switching (that is a scheduled pause with a
  profile).
- Per-profile Do Not Disturb settings.
- A confirmation prompt when switching a profile's mode.
