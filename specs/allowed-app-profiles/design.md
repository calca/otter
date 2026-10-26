# Allowed-App Profiles — Design

> **Status: Implemented** (2026-10-01).

## Key files

| File | Role |
|---|---|
| `AllowedAppsManager.kt` | From one set to profiles: `profiles()`, `profile(id)`, `save(profile)`, `delete(id)`, `selectedProfileId`, `setMode(id, mode)` |
| `AllowedAppsProfile` (new data class) | `id` (`Int`, 0 = default), `name`, `packages: Set<String>`, `mode: AllowedAppsMode` (allowlist/blocklist, see "Mode" below) |
| `SessionManager.kt` | `KEY_PROFILE_ID` stored at `startSession()`, cleared at `endSession()` |
| `AppBlockerAccessibilityService.kt` | `shouldBlock()` reads the active session's profile (`mode` + `packages`) instead of a single allowed set |
| `AllowedAppLaunchItems.kt` | Block-screen launch badges: mode-aware (see "Mode" below) |
| `UsageWatcher.kt` | Usage-pause "stays excluded from the count" check: mode-aware (see "Mode" below) |
| `AllowedAppsActivity.kt` / `AllowedAppsScreen.kt` | Profile switcher at the top (pills), add; rename/delete from the selected pill's pencil; mode picker (two pills) below the profile switcher |
| `MainScreen.kt` | Profile line under the pills, only with 2+ profiles |

## Storage

`SharedPreferences`: one string set per profile (`profile_<id>_packages`),
plus `profile_<id>_name`, `profile_ids`, `selected_profile`, and
`profile_<id>_mode` (see "Mode"). The current `allowed_packages` key becomes
profile 0 on first read (the app is not distributed, but existing test
devices keep their list for free).

## Blocking

`AppBlockerAccessibilityService` reads the session's profile (`mode` +
`packages`) and passes both to `BlockPolicy.shouldBlock()`, which also takes
the always-exempt set from `BlockPolicy.exemptPackages()` (dialer, SystemUI,
android, own package, in-call, keyboards — unconditional in both modes).
`BlockPolicyTest` covers the rules for both modes.

## Mode: allowlist vs blocklist (issue #2, "Not Allowed Apps")

Each profile also carries an `AllowedAppsMode` (`ALLOWLIST` or `BLOCKLIST`),
**per-profile, not app-wide**: a user can keep one profile as a tight
allowlist and another as an open blocklist. Stored as
`profile_<id>_mode`; a missing or unrecognized value reads as `ALLOWLIST`,
so every profile persisted before this field existed (including the legacy
default under `allowed_packages`) keeps working with no migration.

- **Allowlist** (the original behaviour): only `packages` stay reachable
  during a pause; everything else is blocked. Capped at
  `AllowedAppsManager.MAX_ALLOWED_APPS` (3) — see
  app-blocking-and-home-lock/design.md for why.
- **Blocklist**: only `packages` are blocked; everything else, Home
  included, stays reachable. **No cap** — blocking a handful of apps and
  leaving the rest of the phone free is the point of the mode, so there is
  no reason to limit how many can be picked.
- Switching a profile's mode does **not** clear `packages`: the same
  selected apps are reinterpreted under the new meaning (no dialog, no data
  loss) — the simplest option, and consistent with how the rest of the
  profile UI treats `packages` as independent of presentation.
- `AllowedAppLaunchItems.kt` (block-screen launch badges): in blocklist
  mode there's no small "stays" list to show as shortcuts, so only
  Phone + Unlock appear — showing the forbidden apps as one-tap launch
  badges would be the opposite of the feature's intent.
- `UsageWatcher.kt` (usage-pause "excluded from the count" check): mirrors
  `shouldBlock()`'s branch — allowlist membership keeps counting paused,
  blocklist membership does the opposite.
- Home-button consequence: in blocklist mode, pressing Home during a pause
  does **not** land on the block screen unless the device launcher's own
  package happens to be in the forbidden list — correct and intended
  ("leave the rest of the phone free"), but a real behavioural difference
  from allowlist mode's default-deny (see
  app-blocking-and-home-lock/requirements.md, User Story 3).

## Editing a profile

The selected custom profile's pill carries a pencil (`CalmPill`'s
`trailingIcon`, content description "Edit profile"); tapping that pill
again opens one "Edit profile" dialog: the name field, Save/Cancel, and a
red "Delete profile" button under the field, which goes on to the existing
delete confirmation. Standard has no pencil (it can't be renamed or
deleted). This replaced two loose "Rename"/"Delete" text buttons under the
pills, which read as detached from the profile they acted on.

## Decisioni prese

1. **Numero di profili:** predefinito + 4.
2. **Nome del predefinito:** "Standard".
3. **Pause di gruppo:** ognuno usa il proprio profilo; non viaggia nella
   ricetta.

## Home: the profile line, muted

The line under the duration pills was "Allowed: Standard ›" in link green —
reported as one technical word too many in the most important spot of
Home. Now just the profile name, "Standard ›", in `onSurface` 60% (an
apps icon was tried and removed, together with the sprig on the streak
line below, to clean up Home): still tappable (same dialog), no longer reads as an
action. TalkBack still reads "Allowed: Standard". Shown only with 2+
profiles, as before (`AllowedProfileLine`).

## Wording: "apps that stay"

"App consentite / Allowed apps" sounded like a rulebook — someone granting
permission. Now **"App che restano / Apps that stay"**, echoing onboarding
("only the phone and a few apps you choose stay reachable"): Settings row,
page title ("Apps that stay during a pause"), intro ("During a pause
everything stops except the phone and the apps you pick here"), schedule
editor row, Home dialog ("Which apps stay in this pause?"), TalkBack line
("Apps that stay: Standard"), the limit ("At most 10 apps"); store
listing, privacy policy and README follow. Resource keys unchanged.

