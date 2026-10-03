# Quick Time Together by NFC — Design

> **Status: Proposed** (2026-10-03). Nothing below is built yet.

## Roles

- **Reader (A)**: the person who taps "Time together". `GroupPauseNfcReader`
  in reader mode, only while the new `QuickTogetherScreen` is visible.
- **Card (B)**: everyone else. `GroupPauseHceService` (already declared,
  AID `F0010203040506`, category "other") answers **always**, not only
  inside the host lobby as today. It works with the app closed as long as
  the phone is unlocked (Android HCE).

## Exchange: two APDUs, one tap

```
A → B   SELECT AID F0010203040506
B → A   HELLO   v=3 | state | name          (state: READY / IN_PAUSE / NOT_SET_UP)
A → B   PROPOSE v=3 | recipe | delayMs | name
B → A   OK  |  REFUSED(state)
```

- `recipe` is today's group recipe (duration, activity, group tag), so
  history, companions and the "release the others" step keep working.
- **Start = receipt time + `delayMs`** (5 000 ms), computed by each phone on
  its own clock: no absolute timestamps, so clock skew doesn't matter.
  The end is start + duration on each phone; the two ends differ only by
  the tap's own latency (milliseconds).
- `state` lets A refuse early with a clear message (Story 3) without B
  showing anything.
- Version byte bumped to 3; a different major version answers
  `REFUSED(VERSION)` on both sides.
- The existing commands stay on the same AID, told apart by the INS byte:
  the host-lobby marker (Bluetooth path) and the release token
  (`groupPauseUnlockToken`, early unlock) keep working unchanged.

## B's side

- **App in the foreground** (any screen): `GroupPauseHceService` posts the
  proposal to the app; a full-screen `QuickTogetherCountdown` appears
  ("Pause together with Marta · 1 h — Starts in 5… [Cancel]").
- **App closed or in the background**: a starting Activity from an HCE
  service is not allowed in the background, so a heads-up notification
  "Marta proposes a pause together · 1 h" with **Join**. Join starts the
  pause right away, with end = the shared end time (so it's shorter by the
  time it took to accept). The notification expires at that end time.
- B's eligibility (`state`): not in a pause; Accessibility and Do Not
  Disturb granted (the same check as the otter tap; debug builds bypass
  it, as today).

## A's side

`QuickTogetherScreen` replaces the Create / Join chooser **when NFC is on**:
otter with a "hold near" illustration, the proposed duration (pills, Home's
selection), the activity with ↻, and "Other ways ›" (the current chooser).
On `OK` it shows the same countdown as B. Without NFC, "Time together" opens
the current chooser as today, plus a line to turn NFC on.

## Cancel

There's no channel back after the tap. Cancel on one phone stops that
phone's pause only; the other phone's pause runs (and its history still
says "with <name>"). Accepted: both people are side by side and can say so;
a second tap could later carry a "cancelled" message if it matters.

## Samsung NFC chooser

With B's app **open**, `NfcTapGuard` already makes Calm Otter the preferred
service. With B's app **closed**, Android offers no way to be preferred
for a "category other" AID: if a Samsung shows the "Complete action with"
chooser there, the fallback is to open the app and tap again. To verify on
the two Samsungs before shipping.

## What doesn't change

The Bluetooth lobby, QR and code paths stay as they are, reachable from
"Other ways ›" and when NFC is off. No new permission: NFC is already
declared; no Bluetooth on this path.

## Decisioni aperte

1. **Durata proposta:** quella scelta da chi tocca (A), mostrata e
   modificabile sulla schermata, *oppure* la più corta tra quella di A e
   quella selezionata in Home su B? (Proposta: quella di A — è chi propone,
   e B la vede prima di confermare.)
2. **Countdown di 5 secondi** anche su A, o su A parte subito? (Proposta:
   5 secondi su entrambi, stesso momento.)
3. **App chiusa su B**: notifica con "Unisciti" (proposta) oppure niente,
   e serve aprire l'app — più semplice ma meno magico.
4. **Annulla**: accettiamo che annulli solo sul proprio telefono?
