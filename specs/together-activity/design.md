# Together Activity — Design

> **Status: Proposed — not implemented.**

## Key files (planned)

| File | Role |
|---|---|
| `TogetherActivities.kt` (new) | The catalog: `TogetherActivity(id: Int, @StringRes text, minMinutes, maxMinutes)`; `fitting(duration)`; `next(duration, seen)`; `byId(id)` |
| `res/values{,-en}/strings.xml` | One string per activity (`together_activity_<slug>`) |
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

| Fits | Italiano | English |
|---|---|---|
| any | Niente: stiamo assieme | Nothing: let's just be together |
| 10–30 | Prepariamo un tè o un caffè e beviamolo con calma | Let's make tea or coffee and drink it slowly |
| 10–30 | Raccontiamoci la cosa più bella della settimana | Let's tell each other the best thing of our week |
| 10–60 | Usciamo sul balcone o andiamo alla finestra a guardare fuori | Let's step onto the balcony or look out of the window |
| 10–60 | Ascoltiamo un disco dall'inizio alla fine | Let's listen to an album from start to finish |
| 30–90 | Facciamo due passi nel quartiere | Let's take a short walk around the block |
| 30–90 | Cuciniamo qualcosa di semplice insieme | Let's cook something simple together |
| 30–120 | Leggiamo, ognuno il suo libro, nella stessa stanza | Let's read, each our own book, in the same room |
| 30–120 | Facciamo una partita a carte o a un gioco da tavolo | Let's play cards or a board game |
| 30–90 | Riordiniamo insieme un angolo di casa | Let's tidy up a corner of the house together |
| 30–120 | Disegniamo o scriviamo qualcosa, poi ce lo mostriamo | Let's draw or write something, then show each other |
| 30–90 | Facciamo una lista di posti dove vorremmo andare | Let's make a list of places we'd like to go |
| 60–240 | Facciamo una passeggiata lunga, senza meta | Let's take a long walk, no destination |
| 60–240 | Prepariamo una cena con calma | Let's cook a slow dinner |
| 60–240 | Andiamo in un parco e sediamoci un po' | Let's go to a park and sit for a while |
| 60–240 | Guardiamo insieme foto stampate o un vecchio album | Let's look through printed photos or an old album |
| 90–240 | Passiamo un pomeriggio di giochi da tavolo | Let's spend an afternoon playing board games |
| 90–240 | Andiamo in un posto vicino dove non siamo mai stati | Let's go somewhere nearby we've never been |

Every entry is written in the first person plural ("Facciamo…",
"Let's…"): the people in the pause are proposing it to each other, not
being told what to do by the app.

## Decisioni aperte

1. **La lista sopra:** da rivedere riga per riga (aggiungere, togliere,
   riformulare).
2. **Durante la pausa, se le frasi sono spente:** l'attività si mostra
   comunque (raccomandato: è una proposta del gruppo, non una frase
   motivazionale).
3. **Cambiare attività dopo la partenza:** no (raccomandato). Lo scambio
   dati finisce all'inizio della pausa per scelta di progetto.
