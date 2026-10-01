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
| any | Niente: state insieme e basta | Nothing: just be together |
| 10–30 | Preparate un tè o un caffè e bevetelo con calma | Make tea or coffee and drink it slowly |
| 10–30 | Raccontatevi la cosa migliore della settimana | Tell each other the best thing of your week |
| 10–60 | Uscite sul balcone o alla finestra e guardate fuori | Step onto the balcony or to a window and look outside |
| 10–60 | Ascoltate un disco dall'inizio alla fine | Listen to an album from start to finish |
| 30–90 | Fate due passi nel quartiere | Take a short walk around the block |
| 30–90 | Cucinate qualcosa di semplice insieme | Cook something simple together |
| 30–120 | Leggete, ognuno il suo libro, nella stessa stanza | Read, each your own book, in the same room |
| 30–120 | Un gioco di carte o da tavolo | A card or board game |
| 30–90 | Riordinate insieme un angolo di casa | Tidy up a corner of the house together |
| 30–120 | Disegnate o scrivete qualcosa, poi mostratelo | Draw or write something, then show each other |
| 30–90 | Fate una lista di posti dove vorreste andare | Make a list of places you'd like to go |
| 60–240 | Una passeggiata lunga, senza meta | A long walk, no destination |
| 60–240 | Cucinate una cena con calma | Cook a slow dinner |
| 60–240 | Andate in un parco e sedetevi un po' | Go to a park and sit for a while |
| 60–240 | Sistemate insieme le foto stampate o un vecchio album | Look through printed photos or an old album |
| 90–240 | Un pomeriggio di giochi da tavolo | An afternoon of board games |
| 90–240 | Andate in un posto vicino dove non siete mai stati | Go somewhere nearby you've never been |

## Decisioni aperte

1. **La lista sopra:** da rivedere riga per riga (aggiungere, togliere,
   riformulare).
2. **Durante la pausa, se le frasi sono spente:** l'attività si mostra
   comunque (raccomandato: è una proposta del gruppo, non una frase
   motivazionale).
3. **Cambiare attività dopo la partenza:** no (raccomandato). Lo scambio
   dati finisce all'inizio della pausa per scelta di progetto.
