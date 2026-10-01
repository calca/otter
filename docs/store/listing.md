# Google Play listing

Texts to paste into Play Console → *Grow → Store presence → Main store
listing*. Italian is the default language (like the app); English as a
translation. Lengths are checked against Play's limits.

| Field | Limit |
|---|---|
| App name | 30 |
| Short description | 80 |
| Full description | 4000 |

## App name

`Calm Otter` (10)

## Short description

- **IT** (76): Metti giù il telefono, di proposito. Una pausa dalle app, da soli o insieme.
- **EN** (67): Put the phone down, on purpose. Pause your apps, alone or together.

## Full description — Italiano

```
Metti giù il telefono, di proposito.

Calm Otter blocca tutte le app tranne il Telefono e silenzia le notifiche per il tempo che scegli: da una pausa respiro di 10 minuti a quattro ore. Chiamate, sveglie e la musica che stai ascoltando passano comunque.

UN PATTO, NON UNA GABBIA
La pausa la chiude prima del tempo solo chi conosce la password: tu, oppure una persona di fiducia che la sceglie per te. Se la password non è raggiungibile c'è anche un'uscita lenta: aspetti dieci minuti e la pausa finisce, segnata in cronologia.

PAUSE CHE PARTONO DA SOLE
"Nei giorni feriali alle 21:00 per un'ora", "la domenica mattina, due ore": le imposti una volta e partono da sole, con un avviso cinque minuti prima. Aggiungerle è libero; spegnerle o accorciarle chiede la password.

TEMPO INSIEME
Una pausa condivisa con chi hai accanto: il partner, la famiglia, i colleghi. Un QR o un codice, oppure avvicinando i telefoni. L'app propone anche qualcosa da fare insieme — una passeggiata, una partita a carte, o niente, solo stare insieme.

PICCOLI RITUALI
- Una pausa respiro di dieci minuti, con l'anello che si allarga e si stringe.
- "Com'è andata?" alla fine di una pausa, con una parola e una nota se vuoi.
- Una nota la domenica sera con le pause della settimana. Mai serie da non perdere né confronti.

PER LE APP CHE SERVONO
Liste di app consentite per tipo di pausa: Mappe e la chat di lavoro per il pomeriggio, niente per la sera.

NIENTE DATI, NIENTE ACCOUNT
Calm Otter non ha il permesso di usare internet. Niente registrazione, niente pubblicità, niente analisi: la cronologia resta sul telefono. Il codice è aperto, su GitHub.

ONESTÀ SUI LIMITI
È un blocco "morbido": usa il servizio di accessibilità e Non disturbare, che si possono disattivare dalle impostazioni di Android. Calm Otter non finge il contrario. Il servizio di accessibilità serve solo a sapere quale app si apre durante una pausa: non legge lo schermo né quello che scrivi.
```

## Full description — English

```
Put the phone down, on purpose.

Calm Otter blocks every app but Phone and silences notifications for as long as you choose: from a 10-minute breather to four hours. Calls, alarms and the music you're listening to still get through.

A PACT, NOT A CAGE
Only the person who knows the password can end a pause early: you, or someone you trust who sets it for you. If the password can't be reached there's a slower way out: wait ten minutes and the pause ends, marked in your history.

PAUSES THAT START BY THEMSELVES
"Weekdays at 9pm for an hour", "Sunday morning, two hours": set them once and they start on their own, with a heads-up five minutes before. Adding one is free; switching it off or shortening it needs the password.

TIME TOGETHER
A pause shared with whoever is next to you: your partner, your family, your colleagues. A QR or a code, or just tap the phones together. The app even suggests something to do together — a walk, a game of cards, or nothing at all, just being together.

SMALL RITUALS
- A 10-minute breathing pause, with a ring that slowly expands and contracts.
- "How was it?" at the end of a pause, with a word and a note if you like.
- A Sunday evening note with your pauses of the week. Never streaks to keep or comparisons.

FOR THE APPS YOU NEED
Allowed-app lists for each kind of pause: Maps and the work chat in the afternoon, nothing in the evening.

NO DATA, NO ACCOUNT
Calm Otter doesn't have permission to use the internet. No sign-up, no ads, no analytics: your history stays on your phone. The code is open, on GitHub.

HONEST ABOUT ITS LIMITS
It's a "soft" block: it relies on the Accessibility service and Do Not Disturb, which can be switched off in Android settings. Calm Otter doesn't pretend otherwise. The Accessibility service is used only to know which app is opening during a pause: it doesn't read the screen or what you type.
```

## Graphics

All in this folder, generated from the app itself:

| Asset | File | Play requirement |
|---|---|---|
| App icon | `graphics/icon-512.png` | 512 × 512, 32-bit PNG, full square (Play applies the mask) |
| Feature graphic | `graphics/feature-1024x500.png` | 1024 × 500 |
| Phone screenshots | `screenshots/01…05-*.png` | 2–8 images, long side ≤ 2× short side (these are 1280 × 2560) |

Regenerate icon and feature graphic after a design change with
`./gradlew testStableDebugUnitTest --tests "*StoreGraphicsTest*" -Pstore.graphics=true`.
The screenshots were taken on the emulator (English, Sage palette) and
cropped to remove the status and navigation bars.

## Other fields

- **Category**: Productivity.
- **Tags**: digital wellbeing, focus, productivity.
- **Contact**: an email address is required by Play — choose which one to
  publish (not filled in here on purpose). Website: <https://github.com/calca/otter>.
- **Privacy policy URL**: <https://github.com/calca/otter/blob/main/docs/privacy-policy.md>
