# Quick Time Together by NFC — Requirements

> **Status: Proposed** (2026-10-03). Not built yet; see "Decisioni aperte" in design.md.

## Context

Starting a pause together with the person next to you takes today, by NFC:
Home → Time together → Create / Join (each phone) → lobby → system dialog
"make this phone visible via Bluetooth" (host) → tap → Bluetooth connection
→ "Let's go" → countdown. The tap only helps two phones *find each other*
over Bluetooth; everything else is waiting and permissions.

The pause's recipe (duration, start, activity, a name) is about ten bytes:
the NFC exchange can carry it directly, in both directions, in one tap. No
Bluetooth at all on this path.

Decided with the user: **one tap, by one person** ("tocco unico"). The
phone that starts is the reader; the other phone is always a passive card —
in Home, anywhere in the app, or with the app closed. A symmetric version
where both phones alternate reader/card on their own was considered and
dropped: unreliable toggling of reader mode, missed taps when both are in
the same phase, no contactless payments while reading, more chances to hit
Samsung's NFC chooser.

## User Story 1: One tap and we start

As someone sitting next to a friend, I want to start a pause together by
touching our phones once, so that the friction of starting is close to
zero.

### Acceptance Criteria

1. WHEN the user taps "Time together" on Home AND the device has NFC turned
   on THEN the system SHALL go straight to a "Hold your friend's phone
   near" screen in reader mode — no Create / Join choice, no Bluetooth
   dialog, no Bluetooth permission.
2. The screen SHALL show the duration that will be proposed (the one
   selected on Home) and let it be changed there, plus the suggested
   activity with its "another one" icon.
3. WHEN the two phones touch AND the other phone can join (see Story 3)
   THEN both phones SHALL show "Pause together with <name> · <duration>",
   the activity, and "Starts in 5…" with a Cancel action, and SHALL start
   the pause together when the countdown ends.
4. The pause SHALL end at the same moment on both phones, regardless of a
   small difference between the two phones' clocks.
5. Each phone's history SHALL record the other person's name and the
   activity, as group pauses do today.

## User Story 2: My friend's app is closed

As the friend, I want the tap to work even if Calm Otter isn't open on my
phone, so we don't have to coordinate before touching phones.

### Acceptance Criteria

1. WHEN the phones touch AND Calm Otter is not open on the friend's phone
   AND the phone is unlocked THEN the friend's phone SHALL answer the tap
   by itself and show a notification "<name> proposes a pause together ·
   <duration>" with a "Join" action.
2. WHEN the friend joins from the notification THEN their pause SHALL start
   right away and end at the same moment as the other phone's pause.
3. WHEN the friend doesn't join within the proposed duration THEN the
   notification SHALL disappear and nothing SHALL start.

## User Story 3: When it can't work

### Acceptance Criteria

1. WHEN the friend's phone is already in a pause THEN the reader phone
   SHALL say so ("<name> is already on a pause") and start nothing.
2. WHEN the friend's phone lacks the permissions a pause needs THEN the
   reader phone SHALL say "<name> needs to finish setting up Calm Otter"
   and start nothing.
3. WHEN the two phones run different, incompatible versions THEN both
   SHALL say to update the app, as the code/QR path does today.
4. WHEN the device has no NFC or NFC is off THEN "Time together" SHALL open
   the current flow (Create / Join, Bluetooth, QR, code), with a short line
   offering to turn NFC on.
5. The reader screen SHALL keep a "Other ways ›" link to the current
   Bluetooth / QR / code flow, for people who aren't side by side.

## User Story 4: Nothing starts by accident

### Acceptance Criteria

1. A pause SHALL never start without the 5-second countdown with Cancel on
   the phone where it starts (both phones when the app is open; the
   notification's "Join" is itself the confirmation when it isn't).
2. Reader mode SHALL be on only while the "Hold your friend's phone near"
   screen is visible; Home itself never reads NFC.
3. Nothing SHALL leave the two phones: the exchange is phone to phone over
   NFC, as today.
