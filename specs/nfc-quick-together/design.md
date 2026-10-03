# Quick Time Together by NFC — Design

> **Status: Implemented** (2026-10-03), **not yet verified on two real phones** (the emulator has no NFC).

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

Home has two buttons on a phone with NFC (`MainScreen.onQuickTogether`,
null without NFC), **side by side** in one row — stacked, Home overflowed
on a Samsung and the buttons went off screen; side by side it fits even
in Italian at 115% font scale (narrower content padding, the Italian
label wraps onto two lines): **"Phones together"** opens `QuickTogetherActivity` in
reader mode, **"Time together"** the existing chooser. Same permission
check as the otter tap. NFC off → NFC settings + a toast.
`QuickTogetherReaderScreen` is a page (back arrow, otter-tap illustration
pulsing, "Hold your friend's phone near", "A pause together, 1 h", the
activity with ↻, an error line when the other phone refuses); reader mode
only between `onResume` and `onPause`. On `Accepted` the same activity
shows `QuickTogetherCountdownScreen` (the existing countdown with "Pause
together with <name>" and the activity).

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

## Decisions

1. Duration: the proposer's (Home's selection).
2. 5-second countdown with Cancel on both phones.
3. App closed on B: notification with "Join" (`QuickTogetherInbox`,
   channel "Pauses together", high importance, expires at the shared end).
4. Cancel stops only one's own phone.
5. Two buttons on Home rather than a single entry with "Other ways ›"
   (which hid Bluetooth and QR too much), and a page rather than a bottom
   sheet.

## Implementation notes

- `QuickTogetherProtocol` (pure, `QuickTogetherProtocolTest`): HELLO
  `"QT" v state name`, PROPOSE `80 10 00 00 Lc v dur(2) act tag(2)
  delay(2) name`, result byte. Names ≤ 40 UTF-8 bytes, cut on a character.
- `GroupPauseHceService`: PROPOSE → `QuickTogetherInbox.deliver`; SELECT
  answers the lobby/release marker if one is set (unchanged), else HELLO.
  A Bluetooth-lobby joiner reading an idle phone now gets a HELLO instead
  of "not found": it's not a lobby name, so nothing matches — harmless.
- `AppForeground` (activity-lifecycle counter registered by the
  Application) decides activity vs notification on B.
- `SessionManager.startSession(endAtMillis = …)`: B's pause ends at the
  shared end even when it joins late from the notification.
