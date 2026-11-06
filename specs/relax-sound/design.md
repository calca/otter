# Relax Sound — Design

> **Status: Proposed** (2026-11-06). Not built yet.

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
| `ScheduleManager.kt` / scheduled-pause editor | A `sound` field on a schedule, default OFF |

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

`LastDurationStore`-style: one key for the last chosen sound. A schedule
gets a `sound` column in its stored form (default OFF, missing reads as OFF,
so existing schedules need no migration). No new permission besides the
service one above: no storage, no microphone.

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

## Decisioni aperte

1. **Which sounds to ship first:** all four, or only brown noise and rain.
2. **Where the picker lives:** Home next to the duration pills, or a row in
   Settings with the choice remembered.
3. **Group pauses:** each phone its own sound (proposed), or the host's
   choice in the recipe (changes the recipe format, and the version check).
4. **Sound on a scheduled pause:** per schedule (proposed), or follow the
   last choice.
5. **Timing:** after the production access, because of the new foreground
   service declaration.
