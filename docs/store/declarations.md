# Play Console declarations

Answers for the forms under *Policy → App content*. They describe what the
code actually does as of the commit that last touched this file; if a
feature changes, check them again.

## Data safety

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **No** |
| Is all of the user data collected by your app encrypted in transit? | Not applicable: no data leaves the device (the app has no `INTERNET` permission) |
| Do you provide a way for users to request that their data is deleted? | Not applicable: nothing is collected. On-device data is deleted by uninstalling or clearing app storage; History can also be cleared in the app |

Why "no" is correct: Play's definition of *collected* is data transmitted
off the device. Everything Calm Otter stores (history, settings, the
password hash) stays in the app's private storage, and the short-range
Bluetooth/NFC exchange of a shared pause goes phone-to-phone, never to the
developer or a third party. See `docs/privacy-policy.md`.
Android's own backup (`allowBackup`, password excluded) goes to the user's
own Google account under their control and is not "collection" by the app;
the privacy policy says so explicitly.

## Accessibility API

Calm Otter is **not** an accessibility tool (`android:isAccessibilityTool="false"`
in `res/xml/accessibility_service_config.xml`), so Play requires:

1. **In-app prominent disclosure and consent** — done: the dialog "How Calm
   Otter uses Accessibility" (`AccessibilityDisclosure.kt`) appears before
   every path to the system Accessibility settings, with "Agree and
   continue" / "Not now".
2. **The declaration form.** Suggested text for "Describe the core
   functionality that uses the Accessibility API":

   > Calm Otter is a digital-wellbeing app: the user (or a person they
   > trust) starts a timed pause during which every app except the phone
   > dialer and a short list of allowed apps is covered by a full-screen
   > pause screen. The AccessibilityService listens only to
   > TYPE_WINDOW_STATE_CHANGED events to learn which app came to the
   > foreground during an active pause, and if it isn't allowed, shows the
   > pause screen on top of it. It does not retrieve window content
   > (canRetrieveWindowContent=false), does not read text or user input,
   > does not perform actions on other apps, and stores or transmits
   > nothing — the app has no INTERNET permission. Outside a pause, the
   > service ignores every event.

3. **A short video** (unlisted YouTube link) showing: the disclosure
   dialog, enabling the service, starting a pause, opening a blocked app
   and seeing the pause screen. **Still to record** — about 30 seconds on
   a real phone is enough.

## Foreground service: special use

`SessionForegroundService` declares `foregroundServiceType="specialUse"`
with the subtype "Keeps a user-started focus pause running until it ends:
the timer notification, Do Not Disturb and the app block stay active".
Suggested justification for the form:

> The service runs only while a pause the user started is active (from 10
> minutes to 4 hours). It shows the remaining time in an ongoing
> notification and keeps the process alive so the pause (Do Not Disturb
> and the app block) is not interrupted by the system. It stops as soon as
> the pause ends. None of the specific foreground service types describes
> a user-started focus/blocking session.

## Other permissions

No special declaration needed for: `ACCESS_NOTIFICATION_POLICY`,
`POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, `CAMERA`, Bluetooth
(`BLUETOOTH_SCAN` with `neverForLocation`, `CONNECT`, `ADVERTISE`), `NFC`.
Not used (so nothing to declare): `QUERY_ALL_PACKAGES` (the app uses a
`<queries>` block), exact alarms, SMS, call log, location.

## Content rating (IARC questionnaire)

Category: *Utility, productivity, communication or other*. No violence,
sexual content, profanity, drugs, gambling or user-generated content shared
with others, no purchases. Expected rating: **Everyone / PEGI 3**.

## Target audience and content

- **Target age: 16 and over** (suggested). The app is not designed for
  children, and a lower age group would bring in the Families policy
  requirements for no benefit.
- **Ads**: none. **In-app purchases**: none. **News app**: no.
  **Government app**: no. **Financial features**: none. **Health app**:
  no (it's digital wellbeing, not health).
