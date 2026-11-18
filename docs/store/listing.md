# Google Play listing

Texts to paste into Play Console → *Grow → Store presence → Main store
listing*. On Play the default language is English (en-US), with Italian (it-IT)
as a translation. Lengths are checked against Play's limits.

| Field | Limit |
|---|---|
| App name | 30 |
| Short description | 80 |
| Full description | 4000 |

## App name

`Calm Otter` (10)

## Short description

- **IT** (75): Blocca le app e silenzia il telefono per una pausa vera. Da soli o insieme.
- **EN** (70): Block apps and silence your phone for a real break. Alone or together.

What people search for (blocca app, tempo schermo, concentrazione /
block apps, screen time, focus) sits in plain sentences here and in the
first lines of the full description, never as a list of keywords (Play
rejects keyword stuffing). The full description stays honest about the
limits, and makes no claim the app doesn't keep: the app does show "N days
in a row", so no "no streaks" promise.

## Full description — Italiano (2079)

```
Metti giù il telefono, di proposito.

Calm Otter è una pausa vera dal telefono. Tocchi l'otter, scegli quanto, e per quel tempo le app si fermano e le notifiche tacciono. Per staccare la sera, concentrarti sullo studio o sul lavoro, o stare davvero con chi hai accanto.

COME FUNZIONA
• Da 10 minuti a 4 ore, con un tocco
• Blocchi tutto tranne le app che ti servono, oppure solo quelle che ti distraggono
• Profili diversi per momenti diversi: Mappe di giorno, nessuna app la sera
• Non disturbare si attiva da solo, chiamate e sveglie passano sempre

UN PATTO, NON UNA GABBIA
La pausa la chiude prima del tempo solo chi conosce la password: tu, oppure una persona di fiducia che la sceglie per te. Se non è raggiungibile c'è un'uscita lenta: aspetti dieci minuti e la pausa finisce.

PAUSE CHE PARTONO DA SOLE
• Programmate: "nei giorni feriali alle 21:00, per un'ora"
• A sorpresa: in un momento a caso nelle due ore dopo l'orario che scegli
• Dopo un uso lungo: una pausa breve quando sei al telefono da tanto, con un avviso cinque minuti prima. Conta solo il tempo a schermo acceso, mai quali app usi, e di notte non parte.

TEMPO INSIEME
Avvicinate i telefoni e partite insieme. Senza NFC, o in tanti, c'è un QR, un codice o una stanza via Bluetooth. L'app propone anche cosa fare: una passeggiata, una partita a carte, o niente, solo stare insieme.

PICCOLI RITUALI
• Una pausa respiro di dieci minuti
• "Com'è andata?" alla fine, con una parola e una nota se vuoi
• La tua settimana a colpo d'occhio, con un obiettivo se lo vuoi

PRIVACY, DAVVERO
Nessun account, nessuna pubblicità, nessuna analisi. Calm Otter non ha nemmeno il permesso di usare internet: tutto resta sul telefono. Il codice è aperto, su GitHub.

ONESTÀ SUI LIMITI
È un blocco "morbido": usa il servizio di accessibilità e Non disturbare, che si possono disattivare dalle impostazioni di Android. Il servizio di accessibilità serve solo a sapere quale app è in primo piano, per coprirla durante una pausa e, se lo attivi, per contare il tempo a schermo acceso. Non legge lo schermo né quello che scrivi.
```

## Full description — English (2077)

```
Put the phone down, on purpose.

Calm Otter is a real break from your phone. Tap the otter, choose how long, and for that time your apps stop and notifications go quiet. For switching off in the evening, focusing on study or work, or really being with the people around you.

HOW IT WORKS
• From 10 minutes to 4 hours, with one tap
• Block everything except the apps you need, or just the ones that distract you
• Different profiles for different moments: Maps by day, no apps in the evening
• Do Not Disturb turns on by itself, calls and alarms always get through

A PACT, NOT A CAGE
Only the person who knows the password can end a pause early: you, or someone you trust who sets it for you. If they can't be reached, there's a slow way out: wait ten minutes and the pause ends.

PAUSES THAT START ON THEIR OWN
• Scheduled: "weekdays at 9pm, for an hour"
• Surprise: at a random moment in the two hours after the time you pick
• After long use: a short pause when you've been on your phone for a long time, with a heads-up five minutes before. It counts screen-on time only, never which apps you use, and it never starts at night.

TIME TOGETHER
Hold your phones together and start at the same moment. Without NFC, or with more people, there's a QR code, a code or a Bluetooth lobby. The app even suggests something to do: a walk, a game of cards, or nothing at all, just being together.

SMALL RITUALS
• A 10-minute breathing pause
• "How was it?" at the end, with a word and a note if you like
• Your week at a glance, with a goal if you want one

PRIVACY, FOR REAL
No account, no ads, no analytics. Calm Otter doesn't even have permission to use the internet: everything stays on your phone. The code is open, on GitHub.

HONEST ABOUT ITS LIMITS
It's a "soft" block: it relies on the Accessibility service and Do Not Disturb, which can be switched off in Android settings. The Accessibility service is used only to know which app is in the foreground, to cover it during a pause and, if you turn it on, to count screen-on time. It doesn't read the screen or what you type.
```

## Graphics

Generated from the app's own composables, one set per language:

| Asset | File | Play requirement |
|---|---|---|
| App icon | `graphics/icon-512.png` | 512 × 512, 32-bit PNG, full square (Play applies the mask) |
| Feature graphic | `graphics/feature-it.png`, `graphics/feature-en.png` | 1024 × 500 |
| Phone screenshots | `screenshots/it/01…06-*.png`, `screenshots/en/…` | 2–8 images; these are 1080 × 1920 (9:16) |

Each screenshot is a caption above the real screen in a large phone frame
that runs off the bottom edge, on the dark color of one of the four
palettes (a different one per screenshot, so the row on Play doesn't read
as one image): home, pause, time together, scheduled pauses, "how was
it?", history. Upload them with `gh workflow run play-listing.yml -f what=screenshots`
(`what=texts` uploads the short and full descriptions above, in both
languages; `all`, the default, does both). Either way the listing goes to
review. Regenerate after a design change with
`./gradlew testStableDebugUnitTest --tests "*StoreScreenshotsTest*" -Pstore.graphics=true`
(the icon: `--tests "*StoreGraphicsTest*"`, same flag). Captions live in
`StoreScreenshotsTest.kt`.

## Other fields

- **Category**: Productivity.
- **Tags**: digital wellbeing, focus, productivity.
- **Contact**: an email address is required by Play — choose which one to
  publish (not filled in here on purpose). Website: <https://github.com/calca/otter>.
- **Privacy policy URL**: <https://github.com/calca/otter/blob/main/docs/privacy-policy.md>
