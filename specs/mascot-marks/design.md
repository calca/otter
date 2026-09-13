# Mascot Marks — Design

## Key files

| File | Role |
|---|---|
| `res/drawable/ic_launcher_background.xml` | Solid Sage (`#0F5238`) fill, full 108×108dp adaptive-icon canvas |
| `res/drawable/ic_launcher_foreground.xml` | "Still Otter" head — the only mark drawn as a hand-authored vector drawable, since it has to be referenced from a static adaptive-icon XML (and from Glance, which can't run arbitrary Canvas code) |
| `res/drawable/ic_launcher_monochrome.xml` | Head+ears silhouette only, no eyes/nose — themed/Material You icon (Android 13+); simplified further than the color foreground on purpose |
| `res/drawable/ic_otter_widget.xml` | Same silhouette as the foreground, tones inverted (dark head, light details) for the widget's light background — see below |
| `res/mipmap-anydpi-v26/ic_launcher.xml` + `ic_launcher_round.xml` | `<adaptive-icon>` wiring background+foreground+monochrome. minSdk is 26 (the API level adaptive icons shipped in), so there's no legacy PNG fallback to maintain |
| `ui/mascot/OtterMarks.kt` | `OtterFloatMark()`, `PactPawsMark()`, `SprigMark()` — drawn live via Compose `Canvas`, since none of them ever appear outside a Compose screen. `OtterFloatMark` is used in `MainScreen.kt` (Home, "Living Pond"), `OnboardingScreen.kt` (step 1), and now `BlockScreen.kt` too — the same composable, not separate copies. A fourth mark, `PausePawsMark()`, used to live here (see "Replacing PausePawsMark" below) — removed once nothing referenced it any more |

## Why one mark is a vector drawable and the rest are Compose `Canvas`

"Still Otter" needs to exist as a plain Android resource because two of its
consumers aren't Compose at all: the adaptive-icon XML the launcher reads,
and the Glance widget (`PauseWidgetProvider.kt`), which renders via
`Image(provider = ImageProvider(...))` rather than arbitrary drawing calls.
`OtterFloatMark` (and the other Canvas-drawn marks) only ever appear inside
a screen this app already renders with Compose, so they're plain `Canvas`
draw calls —
no path-data hand-conversion, and (unlike the vector drawables) they can
read `MaterialTheme.colorScheme` and stay palette-adaptive.

## Vector drawable path data

Android's `<path android:pathData>` accepts the same command grammar as
SVG (`M`/`L`/`A`/`Z`, etc.), so a circle of radius `r` at `(cx, cy)` is
written as two arcs: `M{cx-r},{cy} a{r},{r} 0 1,0 {2r},0 a{r},{r} 0 1,0
-{2r},0`. `ic_launcher_foreground.xml` unions three such circles (head + 2
ears) in one `<path>` — safe as a plain nonzero-winding union because the
ear centers sit *inside* the head circle by construction (that's what
produces the "peeking ear" crescent), so there's no risk of an accidental
hole.

**`ic_launcher_monochrome.xml` — real bug, found and fixed.** It
originally shipped as head+ears only, no eyes/nose, on the reasoning that
punching holes via `fillType="evenOdd"` needed the eye/nose subpaths wound
opposite to the head's — fragile to get right by hand without a renderer,
and simplifying down to a plain silhouette matches Google's own guidance
that monochrome/themed icons should be *more* reduced than the color
version, not just recolored. **That reasoning about winding direction was
wrong**: `evenOdd` doesn't care which way a subpath is wound (unlike the
`nonzero` rule the foreground's ear-union above actually depends on) — it
just counts how many subpath boundaries a point falls inside, and toggles
fill on each crossing. Any point inside both the head *and* an eye is
inside two boundaries (even → hole), regardless of either shape's drawing
direction. So the eye/nose subpaths could simply be appended to the same
path with `fillType="evenOdd"`, no direction-flipping needed — confirmed
by rendering the exact same `pathData` outside of Android first (Python/
Pillow, painting the eye/nose ellipses back in the background color,
which is geometrically identical to `evenOdd` for non-self-overlapping
subpaths like these) before touching the actual resource.

The plain-silhouette version turned out to be a real problem in practice,
not just a theoretical one: screenshotted on a real device next to Maps/
Telegram/Gmail with themed icons on, every other app's monochrome icon
stayed recognizable (pin, paper plane, envelope) while Calm Otter's was a
featureless rounded blob — indistinguishable as an otter, or from a
plain circle. "More reduced than the color version" doesn't mean *zero*
identifying detail; it means simplified enough to read as one flat shape,
which the eyes/nose (small, and inside the silhouette rather than
crossing its outline) don't interfere with. Fixed by adding them back as
holes via the `evenOdd` path above.

## Adaptive icon safe zone

All foreground content sits inside the center ~66dp safe circle of the
108×108dp canvas (head circle `r=25` centered near `(54,58)`), so it
survives every OEM mask shape (circle, squircle, rounded square) without
clipping — verified against all three during design review, not just
assumed.

## The `surfaceVariant` trap (read before adding a fourth mark)

`ui/theme/CalmOtterTheme.kt` only overrides `background`, `surface`,
`onBackground`, `onSurface`, `primary`, `onPrimary`, `error`, `onError` per
palette (see its own doc comment and `specs/multi-theme-system/design.md`).
Roles like `surfaceVariant` and `primaryContainer` are **not** customized
and silently fall back to Compose Material3's stock baseline scheme —
which is a fixed violet-gray, regardless of which of Sage/Lavender/
Terracotta is active.

The now-removed `OtterAtRestIllustration` originally used `surfaceVariant`
for the body and `primaryContainer` for the paws. Two bugs followed
directly from this:
1. Under the Sage theme, the otter's body rendered lavender-ish anyway
   (the uncustomized default leaking through), defeating the entire point
   of drawing with `MaterialTheme.colorScheme` instead of fixed hex.
2. The paws were nearly invisible — `primaryContainer`'s default happened
   to sit almost the same lightness as `surfaceVariant`'s default, so two
   different "uncustomized" roles landed on two very similar colors right
   next to each other. Both bugs were caught by installing the debug APK
   on an emulator and reading the actual rendered screen, not by code
   review — a good reminder that `MaterialTheme.colorScheme.<role>`
   compiling is not the same as that role actually meaning something in
   this app's theme.

Fixed at the time as `body = primary.copy(alpha = 0.14f)`, `paw =
primary.copy(alpha = 0.32f)` — two densities of the one role that *is*
customized per palette. `OtterFloatMark` (which absorbed this illustration's
onboarding job — see requirements.md's "Superseded marks") uses the same
recipe (`fur = primary.copy(alpha = 0.32f)`, `nose = primary` at full
strength). The now-removed `PausePawsMark` was written against
`primary`/`onPrimary` from the start and never had this problem — worth
remembering if a future mark is written the same way. If you add another mark, stay inside
the customized set (`specs/multi-theme-system/design.md` lists the two
"two parallel systems" this project juggles) or add the new role
explicitly to every `*Light`/`*Dark` scheme in `CalmOtterTheme.kt` first.

## One `Path` per fill color (read before adding overlapping shapes)

A second, unrelated bug hit both `OtterAtRestIllustration` and
`OtterFloatMark`: several of each mark's shapes are meant to read as one
silhouette (body + ears, or tail + torso + head + ear) and are drawn in the
*same* semi-transparent color, but as **separate** `drawCircle`/
`drawRoundRect`/`drawOval`/`drawPath` calls. Where two such shapes overlap
on screen, each call composites its own alpha independently, so the
overlap region ends up visibly darker/more saturated than the rest of the
silhouette — a compositing artifact, not an intentional color difference.
It's easy to miss in code review (every individual draw call looks
correct) and only shows up once rendered.

The fix in both marks: build one `Path` per fill color containing every
sub-shape that should look like a single flat tint (`Path().apply {
addOval(...); addOval(...); addRoundRect(...) }`), then a single
`drawPath(path, color = ...)` call. Compose fills a `Path` with all its
contours in one rasterization pass, so overlapping contours composite
once, not once per contour. Shapes that are *deliberately* layered at
different colors/alphas (e.g. `OtterAtRestIllustration`'s paws on top of
its torso) are unaffected by this — only same-color overlaps need
merging. If you add a mark with more than one shape in the same fill
color, check for overlaps and merge them into one `Path` up front rather
than discovering the seam on-device.

## Replacing onboarding's emoji icons: `PactPawsMark` and `SprigMark`

Onboarding's password step ("Choose the password together") and final step
("All set") originally used plain system emoji (`🔒`, `🌿`) as their
72sp-text "icon", the only two spots in onboarding that didn't share the
flat, hand-drawn `Canvas` style the rest of the app's marks use (step 1
already reused `OtterFloatMark`). Replaced with two new marks in the same
file, each proposed alongside alternatives in an HTML mockup and picked by
the project owner before implementing (see git history for that
conversation) rather than guessed at directly:

- **`PactPawsMark`** — reuses `PausePawsMark`'s exact badge construction
  (circle in `primary`, shapes in `onPrimary`) and its rounded-bar "paw"
  shape, just tilted toward each other (±18°, via `DrawScope.rotate`)
  instead of upright and side-by-side, reading as a handshake/pact instead
  of "pause". Deliberately reuses an existing shape vocabulary instead of
  introducing a new one (a padlock, an otter-plus-key — both considered and
  rejected in the mockup) so the two badge-style marks in the app
  (`PausePawsMark`, `PactPawsMark`) stay visually related.
- **`SprigMark`** — a two-leaf sprig built from two closed `Path`s (each a
  pair of `cubicTo` curves forming a leaf) plus a stroked stem `Path`,
  replacing the `🌿` emoji with the same motif redrawn in the app's own
  flat style: stem in `primary` (full strength, `Stroke` with rounded cap,
  mirroring how `OtterFloatMark`'s nose is the one full-strength accent on
  an otherwise translucent mark), leaves in `primary.copy(alpha = 0.32f)`
  (the same fur-density used by `OtterFloatMark`). No badge/circle
  background, unlike `PactPawsMark` — a badge read as too heavy for a
  closing/"all set" moment against the two considered alternatives
  (reusing `OtterFloatMark` a third time in one wizard, or a checkmark
  badge matching `PactPawsMark`'s construction).
- Both keep the same `surfaceVariant`-trap discipline as every other mark
  here: only `primary`/`onPrimary` (customized per palette) are read, never
  an uncustomized M3 role.
- The inline `🌿` inside the `onb5_title` *string* ("All set 🌿") was
  initially left untouched as ordinary decorative text-emoji usage, the
  same pattern used throughout the app's strings (`streak_days`'s `🔥`,
  `history_empty`'s `🌿`) — out of scope for this mark-replacement fix
  specifically. All of these were later removed in a separate pass (see
  "No emoji anywhere in user-facing text or the widget" below):
  `onb5_title`, `streak_days`, `history_empty`,
  `permission_reason_accessibility`, `permission_reason_dnd`, and the
  hardcoded notification phrase in `SessionForegroundService.kt` no
  longer contain any emoji glyph, in either locale.

## No emoji anywhere in user-facing text or the widget

A later, separate pass removed every remaining emoji character, on
request — including the ones this section explicitly called
"in scope for a different fix" above. Two categories, beyond the plain
string edits:

- **`SessionForegroundService.kt`'s `notificationSubPhrases`** — one entry
  ("Stai facendo bene 🌿") had the same trailing emoji as `onb5_title`, but
  isn't in `strings.xml` at all (a hardcoded Kotlin string list, so not
  covered by the i18n string-review workflow) — easy to miss when
  auditing only resource files.
- **The widget's `⏸`/`🦦` glyphs were real icons, not decorative text** —
  unlike the `strings.xml` cases above, these lived in `TextView`s used
  purely as a pictographic icon (26sp glyph, no surrounding sentence), in
  two places: `widget_pause.xml` (the plain-XML placeholder, see
  `home-screen-widget/design.md`) *and*, for `⏸` only, the real Glance
  active-state content in `PauseWidgetProvider.kt` — the idle state
  already used `Image(ImageProvider(R.drawable.ic_otter_widget))` instead
  of the `🦦` glyph in the Glance code (see "Why the widget needed its own
  drawable" below); only the placeholder XML still had `🦦` as literal
  text. Both `⏸` spots were replaced with a new
  `res/drawable/ic_pause_widget.xml` vector (two rounded bars, `#3D7A5C`
  to match the active-state countdown text color) via `ImageView`
  (placeholder) / `Image` (Glance), so the placeholder and the real
  widget content now agree, and `🦦` was replaced the same way in the
  placeholder using the pre-existing `ic_otter_widget.xml`.

**A stray emoji missed by that pass**: `CalmCountdown.kt`'s
`nearEndPhrases` list (the countdown's own varied under-5-minutes phrases,
unrelated to `strings.xml`/`SessionForegroundService.kt` above — a third
hardcoded Kotlin string list this app had) still had one trailing emoji on
"Quasi finita" — found and removed while touching this exact file for the
`BlockScreen` redesign below, not on its own pass.

## Replacing `PausePawsMark`: `BlockScreen` now reuses `OtterFloatMark`

`PausePawsMark` ("Paws Together" — two otter paws holding each other,
representing real sea-otter sleeping behavior) was `BlockScreen`'s only
mark, drawn as a static badge above the title/message text. Removed
entirely in a `BlockScreen` redesign, on request ("prendi ispirazione dalla
home, vorrei otter fluttuante come prima, riduci le scritte inutili"):
`BlockScreen` now shows the exact same `ProgressRing` + floating
`OtterFloatMark` combination `MainScreen`'s `PondScene` shows during an
active session, instead of its own distinct mark — one visual language for
"a session is active" everywhere it can be encountered, not a different
static badge specific to the block screens. `ProgressRing` and the
floating-offset animation (previously private, inline code inside
`PondScene`) were both lifted out to non-`private` top-level declarations
in `MainScreen.kt` — `rememberOtterFloatOffset(periodMillis)` for the
animation, `ProgressRing` unchanged otherwise — so `BlockScreen.kt` (same
package, `ui/screens`) can call them directly without an import. This also
meant `BlockScreen` needed to start tracking raw remaining/total
milliseconds (`totalMillis`, `remainingMillisState`), not just the already-
formatted countdown string it read before, to compute the ring's fill
fraction the same way `PondScene` does.

`PausePawsMark` had exactly one caller left afterward (itself), so it was
deleted outright rather than left orphaned — see `PactPawsMark`'s doc
comment in `OtterMarks.kt`, which used to link to it as "the same badge
construction", now describing that construction inline instead since the
mark it referenced no longer exists.

The same pass also removed `BlockScreen`'s `block_title`/`block_message`/
`block_actions_label` strings ("Pause in progress", the full paragraph
explaining the block's rules, and the caption above the allowed-apps row)
— on request, to cut text repeated every time the screen appears when the
visual language (ring, otter, lock icon) and the existing reflective
phrase/countdown already carry the meaning. `home_active_label` ("Paused"),
already used by `PondScene` for the same state, is reused here instead of
introducing a `BlockScreen`-specific label.

## Why the widget needed its own drawable

`ic_launcher_foreground.xml`'s otter is a light, near-white shape — correct
sitting on the launcher icon's solid Sage background, but it would be a
near-white shape on the *widget's* own background
(`Color(0xFFEAF0E8)`, a very light sage tint), i.e. almost invisible.
`ic_otter_widget.xml` is the same silhouette with the two fills swapped
(dark `#2C4A3E` head, light `#EAF0E8` details) to match the widget's
existing fixed palette instead.
