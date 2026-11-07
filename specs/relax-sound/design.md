# Relax Sound — Design

> **Status: Proposed** (2026-11-06). Built as a test build on 2026-11-06 (general sound, per-profile sound, block-screen toggle, host-only sound in a pause together); not released. Still missing: the guest label "plays on <host>'s phone" and the sound in the group recipe.

## Key files

| File | Role |
|---|---|
| `sound/RelaxSound.kt` (new) | `enum class RelaxSound { OFF, RAIN, WAVES, BROWN, DRONE }`, pure, with the stored name |
| `sound/SoundGenerator.kt` (new) | Fills PCM buffers per sound; pure Kotlin, no Android, so it is unit-testable |
| `sound/RelaxSoundPlayer.kt` (new) | `AudioTrack` (stream, `USAGE_MEDIA`/`CONTENT_TYPE_MUSIC`), audio focus, headphone-unplug receiver, fade in/out |
| `SessionForegroundService.kt` | Owns the player: starts it in `onStartCommand` when the session has a sound, stops it in `onDestroy` |
| `SessionManager.kt` | Holds the chosen sound for the active session; `startSession()` gains a `sound` parameter |
| `ui/screens/MainScreen.kt` | "Sound" row of `CalmPill` in the pause setup |
| `ui/screens/BlockScreen.kt` | The small toggle |
| `AllowedAppsManager.kt` | `AllowedAppsProfile` gains `sound: RelaxSound?` (null = use the general one), key `profile_<id>_sound`, removed in `deleteProfile` like `mode` |
| `AllowedAppsScreen.kt` | A sound row on the profile screen, below the mode picker |
| `SettingsScreen.kt` | The general sound row |
| group-pause recipe (`GroupPauseRecipe*`) | Carries the sound id; only the host phone's service plays it; version bump |

## How the sound is made

No assets. Samples are generated in blocks and written to the `AudioTrack`
from a worker thread owned by the service.

- **Brown noise:** integrated white noise with leakage, normalised.
- **Rain:** pink-ish noise high-passed, with sparse random short bursts.
- **Waves:** brown noise whose amplitude follows a slow envelope (about 8–12 s
  period, slightly randomised).
- **Drone:** two or three sine partials at low frequencies, slow amplitude
  drift. No melody.

Randomness comes from `kotlin.random.Random`: this is audio,
not security. Output at 22,050 Hz mono 16-bit: enough for these sounds and a
quarter of the CPU of 44.1 kHz stereo. The generator is stateless between
blocks except for filter state, so the stream never loops audibly.

## Foreground service type

Playing audio in the background needs `foregroundServiceType="mediaPlayback"`
and the `FOREGROUND_SERVICE_MEDIA_PLAYBACK` permission. The service today is
`specialUse` only, and Play requires a declaration (with a demo video) for
each type. This is the main cost of the feature: the manifest, the Play
Console declaration (`docs/store/declarations.md`) and a new video change
together. Do not ship it while the production request is under review.

## Persistence

The general sound is one key in the app's plain SharedPreferences (default
OFF). Per profile: `profile_<id>_sound`, read with
`runCatching { valueOf }.getOrNull()`, so a missing or corrupt value means
"Default" and existing profiles need no migration. Schedules store no sound:
they already store a profile. No new permission besides the service one
above: no storage, no microphone.

## Notes

- **Why generate and not ship files:** zero APK size, no licence to check
  (F-Droid wants free licences), nothing to download, no network.
- **Why no password to stop it:** turning the sound off loosens nothing of
  the lock.
- **Audio focus:** `AUDIOFOCUS_GAIN`; on transient loss, pause the track
  and resume; on permanent loss, stop and drop the toggle to off.
- **DND:** the pause's Do Not Disturb policy already lets media through.
- **Battery:** the worker thread sleeps between blocks; a 10-minute pause
  costs a few seconds of CPU.

## Tests

- `SoundGeneratorTest`: samples stay in range, no NaN, not silent, the
  waves envelope has the expected period.
- `RelaxSoundTest`: unknown stored name reads as OFF.
- `SessionManager` test: the sound stops with the session on every end path.
- `AccessibilityAuditTest`: the toggle has a name and a 48 dp area.

## Decisioni prese

1. **Suoni:** tutti e quattro (Rain, Waves, Brown noise, Drone).
2. **Dove si sceglie:** Impostazioni (suono generale) e profilo (suono per
   profilo, con "Predefinito" = quello generale).
3. **Pausa condivisa:** decide chi crea la pausa e il suono esce solo dal
   suo telefono. Due telefoni con lo stesso rumore nella stessa stanza non
   sono sincronizzati. Costo: la ricetta cambia formato (controllo di
   versione già presente); se l'host ha le cuffie nessun altro sente.
4. **Pause programmate:** seguono il suono del profilo scelto, nessun campo
   proprio.

5. **Nome:** "Profile" (it. "Profilo"). "Apps that stay" era già sbagliato
   con la modalità "All except these" e lo sarebbe ancora di più con il
   suono dentro; "Profile" è già usato in "Edit profile", "Delete profile" e
   `AllowedAppsProfile`. Cambiano le etichette che parlano di app: la riga
   delle Impostazioni diventa "Profiles", la riga della Home "Profile:
   <nome>", l'etichetta delle pause programmate "Profile". I titoli
   dell'elenco di app dentro il profilo restano. Stringhe da aggiornare in
   it, en e zh-CN.

## Decisioni aperte

1. **Quando:** dopo l'accesso alla produzione, per la nuova dichiarazione del
   servizio in primo piano.
