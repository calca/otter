# Together Activity — Design

> **Status: Proposed — not implemented.**

## Key files (planned)

| File | Role |
|---|---|
| `TogetherActivities.kt` (new) | The catalog: `TogetherActivity(id: Int, @StringRes text, minMinutes, maxMinutes)`; `fitting(duration)`; `next(duration, seen)`; `byId(id)` |
| `res/values{,-en}/strings.xml` | Two strings per activity: the sentence and its short form for History |
| `GroupPauseRecipe.kt` | New field `activityId: Int` (one byte), payload 8 → 9 bytes |
| `bluetooth/GroupPauseBluetoothProtocol.kt` | `LOBBY:` line carries the activity id too, so guests see it before the start |
| `GroupPauseHostScreen.kt`, `GroupPauseBluetoothLobbyHostScreen.kt` | Suggestion card with "Another one" |
| `GroupPauseBluetoothLobbyJoinScreen.kt`, `GroupPauseJoinScreen.kt` | Show the received suggestion |
| `SessionManager.kt` | `KEY_GROUP_ACTIVITY` stored at start, written to history at end |
| `SessionRecord.kt` | New column `activityId INTEGER NOT NULL DEFAULT 0` (0 = none) |
| `BlockScreen.kt` / `HistoryScreen.kt` | Show it |

## The id travels, not the text

Only the numeric id travels: each phone renders it in its own language, so
an Italian host and an English guest see the same activity in their own
words. Ids are stable forever (never reused); removing an activity leaves
a gap. Id `0` means "no activity" (solo pause, or a host that removed it).

## Recipe: no compatibility layer

Payload becomes `startAtSec(4) | duration(1) | groupTag(2) | activityId(1)
| checksum(1)` = 9 bytes, 12 base64url characters instead of 11. A code
from the old format has the wrong length and is rejected by the existing
`bytes.size != PAYLOAD_SIZE` check — exactly the "invalid code" path, no
special case. `GroupPauseRecipeTest` gains: round trip with an activity,
unknown id rejected, old 8-byte code rejected.

## Suggestions

`next(duration, seen)` picks randomly among activities whose
`minMinutes..maxMinutes` contains the duration and that are not in `seen`;
when all have been seen it starts over. The "nothing" entry fits every
duration. Pure functions, unit-tested; the screen just keeps `seen` in
state.

## History

The column shares the version bump with `closing-moment` and `slow-exit`
when built in the same round (see `specs/closing-moment/design.md`).

## First catalog draft (to review)

| Fits | Italiano | English | Breve (Cronologia) | Short (History) |
|---|---|---|---|---|
| any | Niente: stiamo assieme | Nothing: let's just be together | insieme | together |
| 10–30 | Prepariamo un tè e beviamolo con calma | Let's make tea and drink it slowly | un tè | tea |
| 10–30 | Prendiamoci un caffè insieme, senza fretta | Let's have a coffee together, no rush | un caffè | coffee |
| 10–30 | Raccontiamoci la cosa più bella della settimana | Let's tell each other the best thing of our week | la settimana | our week |
| 10–60 | Usciamo sul balcone a guardare fuori | Let's step out onto the balcony and look around | il balcone | the balcony |
| 10–60 | Ascoltiamo un disco dall'inizio alla fine | Let's listen to an album from start to finish | un disco | an album |
| 30–90 | Facciamo due passi nel quartiere | Let's take a short walk around the block | due passi | a short walk |
| 30–90 | Cuciniamo qualcosa di semplice insieme | Let's cook something simple together | in cucina | cooking |
| 30–120 | Leggiamo, ognuno il suo libro, nella stessa stanza | Let's read, each our own book, in the same room | lettura | reading |
| 30–120 | Facciamo una partita a carte | Let's play a game of cards | carte | cards |
| 30–120 | Giochiamo a un gioco da tavolo | Let's play a board game | gioco da tavolo | a board game |
| 30–90 | Riordiniamo insieme un angolo di casa | Let's tidy up a corner of the house together | riordino | tidying up |
| 30–120 | Disegniamo qualcosa, poi ce lo mostriamo | Let's draw something, then show each other | disegno | drawing |
| 30–120 | Scriviamo qualcosa, poi ce lo leggiamo | Let's write something, then read it to each other | scrittura | writing |
| 30–90 | Facciamo una lista di posti dove vorremmo andare | Let's make a list of places we'd like to go | posti da vedere | places to go |
| 60–240 | Facciamo una passeggiata lunga, senza meta | Let's take a long walk, no destination | passeggiata | a long walk |
| 60–240 | Prepariamo una cena con calma | Let's cook a slow dinner | cena | dinner |
| 60–240 | Andiamo in un parco e sediamoci un po' | Let's go to a park and sit for a while | al parco | the park |
| 60–240 | Sfogliamo insieme un vecchio album di foto | Let's look through an old photo album together | vecchie foto | old photos |
| 90–240 | Passiamo un pomeriggio di giochi da tavolo | Let's spend an afternoon playing board games | giochi da tavolo | board games |
| 90–240 | Andiamo in un posto vicino dove non siamo mai stati | Let's go somewhere nearby we've never been | un posto nuovo | somewhere new |

Every entry is written in the first person plural ("Facciamo…",
"Let's…"): the people in the pause are proposing it to each other, not
being told what to do by the app. Each entry is **one** activity, never
"this or that": an alternative becomes its own entry.

Each entry also has a **short form** for History, where the full sentence
would not fit one line: "with Marta · a long walk". Two strings per
activity (`together_activity_<slug>` and `together_activity_<slug>_short`).

## Decisioni prese

1. **La lista:** prima persona plurale, una sola attività per voce
   (nessuna "o"); da rivedere ancora riga per riga se serve.
2. **Frasi spente:** l'attività si mostra comunque.
3. **Cambiare attività dopo la partenza:** no.
4. **Cronologia:** forma breve per ogni attività.
