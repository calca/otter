# Allowed-App Profiles — Design

> **Status: Implemented** (2026-10-01).

## Key files

| File | Role |
|---|---|
| `AllowedAppsManager.kt` | From one set to profiles: `profiles()`, `profile(id)`, `save(profile)`, `delete(id)`, `selectedProfileId` |
| `AllowedAppsProfile` (new data class) | `id` (`Int`, 0 = default), `name`, `packages: Set<String>` |
| `SessionManager.kt` | `KEY_PROFILE_ID` stored at `startSession()`, cleared at `endSession()` |
| `AppBlockerAccessibilityService.kt` | `allowedPackages()` reads the active session's profile instead of the single list |
| `AllowedAppsActivity.kt` / `AllowedAppsScreen.kt` | Profile switcher at the top (pills), add; rename/delete from the selected pill's pencil |
| `MainScreen.kt` | Profile line under the pills, only with 2+ profiles |

## Storage

`SharedPreferences`: one string set per profile (`profile_<id>_packages`),
plus `profile_<id>_name`, `profile_ids`, `selected_profile`. The current
`allowed_packages` key becomes profile 0 on first read (the app is not
distributed, but existing test devices keep their list for free).

## Blocking

`BlockPolicy` does not change: it already takes the allowed set as a
parameter. Only the service's `allowedPackages()` changes source, so the
existing `BlockPolicyTest` keeps covering the rules.

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
