# Together Activity — Design

> **Status: Implemented** (2026-10-01).

## Key files

| File | Role |
|---|---|
| `TogetherActivities.kt` (new) | The catalog: `TogetherActivity(id: Int, category, @StringRes text, minMinutes, maxMinutes)`, `enum TogetherCategory`; `fitting(duration)`; `next(duration, seen)`; `byId(id)` |
| `res/values{,-en}/strings.xml` | One string per activity, plus the four category names |
| `res/drawable/ic_together_*.xml` (new) | The four category icons |
| `GroupPauseRecipe.kt` | New field `activityId: Int` (one byte) and a version byte: payload 8 → 10 bytes |
| `bluetooth/GroupPauseBluetoothProtocol.kt` | `LOBBY:` line carries the activity id too, so guests see it before the start |
| `GroupPauseHostScreen.kt`, `GroupPauseBluetoothLobbyHostScreen.kt` | Suggestion card; for the proposer, "What shall we do?" is itself a small button with ↻ on the left that changes the suggestion (was a label plus a separate refresh icon, and before that a text button under the phrase) |
| `GroupPauseBluetoothLobbyJoinScreen.kt`, `GroupPauseJoinScreen.kt` | Show the received suggestion |
| `SessionManager.kt` | `KEY_GROUP_ACTIVITY` stored at start, written to history at end |
| `SessionRecord.kt` | New column `activityId INTEGER NOT NULL DEFAULT 0` (0 = none) |
| `BlockScreen.kt` / `HistoryScreen.kt` | Show it |

## The id travels, not the text

Only the numeric id travels: each phone renders it in its own language, so
an Italian host and an English guest see the same activity in their own
words. Ids are stable forever (never reused); removing an activity leaves
a gap. Id `0` means "no activity" (solo pause, or a host that removed it).

## Recipe and protocol: versioned

Payload: `version(1) | startAtSec(4) | duration(1) | groupTag(2) |
activityId(1) | checksum(1)` = 10 bytes, 14 base64url characters. The first
byte is `GROUP_PAUSE_PROTOCOL_VERSION` (2 = recipe with the activity); the
Bluetooth HELLO carries it too (`HELLO:2|name`), and a host that receives a
different one answers `MISMATCH:<version>` and closes. A code or lobby from
another version of the app is recognised as such
(`RecipeDecodeResult.OtherVersion`) and the person joining reads "update
the app on both phones", not "invalid code"; a mistyped code is still just
invalid. The version must be bumped with every format change.

## Suggestions

`next(duration, seen, lastCategory)` picks randomly among activities whose
`minMinutes..maxMinutes` contains the duration and that are not in `seen`,
preferring a category other than `lastCategory`; when all have been seen it
starts over. The "nothing" entry fits every
duration. Pure functions, unit-tested; the screen just keeps `seen` in
state.

## History

The column shares the version bump with `closing-moment` and `slow-exit`
when built in the same round (see `specs/closing-moment/design.md`).

## Catalog (first version)

| Fits | Categoria | Italiano | English |
|---|---|---|---|
| any | Con calma | Niente: stiamo assieme | Nothing: let's just be together |
| 10–30 | A tavola | Prepariamo un tè e beviamolo con calma | Let's make tea and drink it slowly |
| 10–30 | A tavola | Prendiamoci un caffè insieme, senza fretta | Let's have a coffee together, no rush |
| 10–30 | Con calma | Raccontiamoci la cosa più bella della settimana | Let's tell each other the best thing of our week |
| 10–60 | Fuori | Usciamo sul balcone a guardare fuori | Let's step out onto the balcony and look around |
| 10–60 | Con calma | Ascoltiamo un disco dall'inizio alla fine | Let's listen to an album from start to finish |
| 30–90 | Fuori | Facciamo due passi nel quartiere | Let's take a short walk around the block |
| 30–90 | A tavola | Cuciniamo qualcosa di semplice insieme | Let's cook something simple together |
| 30–120 | Con calma | Leggiamo, ognuno il suo libro, nella stessa stanza | Let's read, each our own book, in the same room |
| 30–120 | Giochi | Facciamo una partita a carte | Let's play a game of cards |
| 30–120 | Giochi | Giochiamo a un gioco da tavolo | Let's play a board game |
| 30–90 | Con calma | Riordiniamo insieme un angolo di casa | Let's tidy up a corner of the house together |
| 30–120 | Con calma | Disegniamo qualcosa, poi ce lo mostriamo | Let's draw something, then show each other |
| 30–120 | Con calma | Scriviamo qualcosa, poi ce lo leggiamo | Let's write something, then read it to each other |
| 30–90 | Con calma | Facciamo una lista di posti dove vorremmo andare | Let's make a list of places we'd like to go |
| 60–240 | Fuori | Facciamo una passeggiata lunga, senza meta | Let's take a long walk, no destination |
| 60–240 | A tavola | Prepariamo una cena con calma | Let's cook a slow dinner |
| 60–240 | Fuori | Andiamo in un parco e sediamoci un po' | Let's go to a park and sit for a while |
| 60–240 | Con calma | Sfogliamo insieme un vecchio album di foto | Let's look through an old photo album together |
| 90–240 | Giochi | Passiamo un pomeriggio di giochi da tavolo | Let's spend an afternoon playing board games |
| 90–240 | Fuori | Andiamo in un posto vicino dove non siamo mai stati | Let's go somewhere nearby we've never been |

Every entry is written in the first person plural ("Facciamo…",
"Let's…"): the people in the pause are proposing it to each other, not
being told what to do by the app. Each entry is **one** activity, never
"this or that": an alternative becomes its own entry.

Each entry belongs to one of four **categories**, each with its own icon:
**Fuori / Outside** (tree), **A tavola / At the table** (cup), **Giochi /
Games** (die), **Con calma / Slow** (leaf). History shows the category icon
next to the companions instead of the full sentence, which would not fit
one line: "with Marta · 🌳". The icon's content description is the category
name, so TalkBack reads "with Marta, outside". The four icons are our own
vector drawables, like the History action icons: the project does not use
`material-icons-extended`.

Categories also make "Another one" useful: `next()` prefers a category
different from the suggestion just shown, so the alternative is actually
different (a walk after the balcony is not much of a change).

## Decisioni prese

1. **La lista:** prima persona plurale, una sola attività per voce
   (nessuna "o"); da rivedere ancora riga per riga se serve.
2. **Frasi spente:** l'attività si mostra comunque.
3. **Cambiare attività dopo la partenza:** no.
4. **Cronologia:** l'icona della categoria, non una forma breve per
   attività.
5. **Categorie:** Fuori, A tavola, Giochi, Con calma.
