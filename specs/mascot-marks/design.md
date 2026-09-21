# Mascot Marks — Design

## Key files

| File | Role |
|---|---|
| `res/drawable/ic_launcher_background.xml` | Solid Sage (`#0F5238`) fill, full 108×108dp adaptive-icon canvas |
| `res/drawable/ic_launcher_foreground.xml` | "Still Otter" head — the only mark drawn as a hand-authored vector drawable, since it has to be referenced from a static adaptive-icon XML (and from Glance, which can't run arbitrary Canvas code) |
| `res/drawable/ic_launcher_monochrome.xml` | Themed/Material You icon (Android 13+): head+ears silhouette with eyes/nose knocked out as transparent holes. Simplified further than the color foreground, but not to a bare silhouette — see "two real bugs, in sequence" below, including why this path must stay `nonzero` with reversed holes and must **not** use `evenOdd` |
| `res/drawable/ic_otter_widget.xml` | Same silhouette as the foreground, tones inverted (dark head, light details) for the widget's light background — see below |
| `res/mipmap-anydpi-v26/ic_launcher.xml` + `ic_launcher_round.xml` | `<adaptive-icon>` wiring background+foreground+monochrome. minSdk is 26 (the API level adaptive icons shipped in), so there's no legacy PNG fallback to maintain |
| `ui/mascot/OtterMarks.kt` | `OtterFloatMark()`, `PactPawsMark()`, `SprigMark()`, `TogetherMark()` — drawn live via Compose `Canvas`, since none of them ever appear outside a Compose screen. `OtterFloatMark` is used in `MainScreen.kt` (Home, "Living Pond"), `OnboardingScreen.kt` (step 1), and now `BlockScreen.kt` too — the same composable, not separate copies. A fourth mark, `PausePawsMark()`, used to live here (see "Replacing PausePawsMark" below) — removed once nothing referenced it any more |

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

**`ic_launcher_monochrome.xml` — two real bugs, in sequence.** Worth
reading as a pair, because the fix for the first one caused the second.

**Bug 1: the featureless blob.** It originally shipped as head+ears only,
no eyes/nose, on the reasoning that simplifying down to a plain
silhouette matches Google's guidance that monochrome/themed icons should
be *more* reduced than the color version. That turned out to be a real
problem in practice: screenshotted on a real device next to Maps/
Telegram/Gmail with themed icons on, every other app's monochrome icon
stayed recognizable (pin, paper plane, envelope) while Calm Otter's was a
featureless rounded blob — indistinguishable as an otter, or from a plain
circle. "More reduced than the color version" doesn't mean *zero*
identifying detail; it means simplified enough to read as one flat shape,
which the eyes/nose (small, and inside the silhouette rather than
crossing its outline) don't interfere with.

**Bug 2: the missing ears, caused by how bug 1 was fixed.** The fix
added the eye/nose subpaths and switched the path to
`fillType="evenOdd"`. The stated justification was that `evenOdd` doesn't
care which way a subpath is wound, so no direction-flipping was needed —
which is *true in isolation* and *the wrong rule for this shape*. The
head and the two ears deliberately overlap (that overlap is what produces
the "peeking ear" silhouette). Under `evenOdd`, a point inside the head
**and** inside an ear falls within two boundaries — even — and so becomes
transparent. The result, reported from the launcher with a screenshot,
was an otter with two white crescents where its ears should be.

**The correct rule here is `nonzero` with the holes wound backwards**
(sweep flag `1` instead of `0` on the eye/nose arcs): concordant subpaths
union — which is what head+ears need — while discordant ones subtract,
which is what the eyes and nose need. This is what the pre-bug-1
documentation had claimed was necessary, and it was right; it was
overridden on a correct-but-inapplicable general fact about `evenOdd`.

**Why the verification missed it.** Bug 1's fix *was* checked by
rendering outside Android first — but by painting the eye/nose ellipses
back in the background colour, which is only geometrically equivalent to
`evenOdd` for subpaths that don't overlap each other. The ears do overlap
the head, so the check modelled the part that worked and silently skipped
the part that broke. The re-fix was verified by rendering the real
`pathData` through an actual `fill-rule` implementation, comparing all
three candidates side by side (`evenOdd`; `nonzero` with concordant
holes; `nonzero` with reversed holes), and looking at the *whole*
silhouette rather than only the detail being added — then confirmed on
the device with themed icons switched on in Wallpaper & style.

The lesson worth keeping: when changing a fill rule, the blast radius is
every subpath in the path, not just the one being added.

## Adaptive icon safe zone

All foreground content sits inside the center ~66dp safe circle of the
108×108dp canvas, so it survives every OEM mask shape (circle, squircle,
rounded square) without clipping. For the original "Still Otter" pair that
came out of the geometry directly (head circle `r=25` centered near
`(54,58)`); the Zen face that replaced it needs an explicit scale group to
get there — see "The launcher icon follows" below. Either way the check is
the same: render the drawable with the mask and the safe circle over it,
don't assume.

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

## `TogetherMark`: two prints, not two paws

`TogetherMark` is the inline icon of "Tempo insieme" — Home's button, Home's
chooser and onboarding's step. It started as `PactPawsMark`'s two paws with
the badge removed: two tilted rounded rects, which said "paws" but not "in
pairs".

It is now the redesign's **Minimal Tandem Paws** icon (Stitch project
`3158702940609906617`, 24×24 SVG): two prints, one following the other. Same
method as `OtterZenMark` — the two pad outlines are the mockup's own
`pathData` handed to `PathParser` rather than redrawn by eye, and the eight
toes stay circles, which is what they are in the source.

`PactPawsMark` was left alone. There the paws sit inside a badge and act as
a seal on a pact, not as a track.

Two deliberate departures from the mockup:

- **Its two hex colours are dropped**, like every other mark here: `#1B3B2B`
  and `#8FA693` would stay sage green across all eight palette/theme
  combinations. The front print takes `tint` (`primary`), the back one the
  same tint lightened toward `surface`. *Lightened, not made translucent* —
  the prints overlap, and alpha would turn the shared area into a third,
  darker colour, a smudge exactly where the two tracks cross.
- **Lightened by 30%, not the mockup's 45%.** There the sage print sits on
  white; here it sits on a container that is already `primary` at 14%, and
  at 45% the trailing print vanished into the button.

The Home button asks for **22dp**, not the 18dp the old mark used: eight
toes between the two prints mush into a single blob below about 20dp.
Verified at real size on the device, not in a preview.

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
`OtterFloatMark` combination `MainScreen`'s `PondOtter` shows during an
active session, instead of its own distinct mark — one visual language for
"a session is active" everywhere it can be encountered, not a different
static badge specific to the block screens. `ProgressRing` and the
floating-offset animation (previously private, inline code inside
`PondOtter`) were both lifted out to non-`private` top-level declarations
in `MainScreen.kt` — `rememberOtterFloatOffset(periodMillis)` for the
animation, `ProgressRing` unchanged otherwise — so `BlockScreen.kt` (same
package, `ui/screens`) can call them directly without an import. This also
meant `BlockScreen` needed to start tracking raw remaining/total
milliseconds (`totalMillis`, `remainingMillisState`), not just the already-
formatted countdown string it read before, to compute the ring's fill
fraction the same way `PondOtter` does.

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
already used by `PondOtter` for the same state, is reused here instead of
introducing a `BlockScreen`-specific label.

## Why the widget needed its own drawable

`ic_launcher_foreground.xml`'s otter is a light, near-white shape — correct
sitting on the launcher icon's solid Sage background, but it would be a
near-white shape on the *widget's* own background
(`Color(0xFFEAF0E8)`, a very light sage tint), i.e. almost invisible.
`ic_otter_widget.xml` is the same silhouette with the two fills swapped
(dark `#2C4A3E` head, light `#EAF0E8` details) to match the widget's
existing fixed palette instead.


## The Zen Otter: the redesign's face

`OtterZenMark` is the mascot from the app redesign (Stitch design system
"Calm Otter Sanctuary"): front-facing muzzle, eyes closed in two serene
arcs, blushed cheeks, a soft halo behind. Its geometry is the original
SVG's path data handed to `PathParser`, not redrawn by eye — the same
method already used for the eye icon in `PasswordOutlinedTextField`.

It replaces `OtterFloatMark` **inside the ring** (Home, `BlockScreen`, and
the accessibility bridge window that has to show the same thing) and on the
opening screens (onboarding). `OtterFloatMark` stays: it is still used by
the group-pause screens and the history empty state, and it is the version
to fall back to.

Two colour decisions worth keeping:

- **The ink is `primary` pushed toward black**, not a theme role. `onSurface`
  is light in dark mode and would vanish against the fur, which is light
  there too; a fixed black would ignore the palette. Mixed this way it stays
  dark in both themes and keeps the hue of whichever palette is selected.
- **The blush is the one colour that does not follow the palette.** It reads
  as skin, not as brand accent: tinted green or terracotta it stops reading
  as a cheek at all. It stays `#D7A99C` at 45%.

### The muzzle went dark in dark mode

The muzzle and inner-ear tints were originally `veil` (the `tertiary` role)
composited over `surface`: in light mode `surface` is near-white in every
palette, so this happened to read as a pale patch, and the "why" comment
even called it out as the deliberate replacement for plain white. Nobody
had checked dark mode, where `surface` is dark by definition and `tertiary`
is *also* dark there (it is the pond-ring colour, meant to sit quietly on a
dark background, not to lighten one) — compositing dark over dark gave a
near-black muzzle, reported as "otter non è bello in dark mode" and
confirmed on the emulator: what should read as a soft face patch instead
looked like a hole punched in the fur.

Fixed by compositing against a **fixed** light base (`MuzzleBase`,
`#F4F3EC`) instead of the theme's `surface`, so the patch stays pale
regardless of theme mode — the same reasoning already used for the blush
above, just not yet applied here. Caught in the same pass: `innerEar` used
`compositeOver` with a fully-opaque `veil`, which returns `veil` unchanged
regardless of the base colour underneath it — so the inner-ear tint was
never actually affected by `surface` (or now `MuzzleBase`) at all, light
mode included; it only looked right there by coincidence, because `veil`
itself happens to be pale in light mode. Both are now built with `lerp`
against `MuzzleBase` instead, which blends rather than overwrites.

### The launcher icon follows

`ic_launcher_foreground.xml` and `ic_launcher_monochrome.xml` are now the Zen
face; the previous "Still Otter" pair is kept beside them as
`*_still_otter.xml`. The adaptive background moves from Sage green to Deep
Forest pine (`#1B3B2B`) and the fur to `#8FA693`, the design system's "Muted
Mountain Sage" — the in-app mascot takes its colours from the palette, but
the launcher has no way to know which palette was chosen.

The viewport is 120 (the SVG's) while width/height stay 108dp. The path
data as exported spans x 27..89, y 32..89 — the ear circles reach ~39 units
from the centre, past the adaptive icon's safe radius (66 of 108, i.e. 36.5
of 120), so on a real launcher the ears sat flush against the mask edge.
Both drawables therefore wrap their paths in

    <group android:scaleX="0.72" android:scaleY="0.72"
           android:pivotX="60" android:pivotY="60">

which pulls the furthest point in to ~28 units and leaves visible breathing
room inside any mask shape. Scaling the group rather than re-exporting the
path data keeps the two files byte-comparable with their `*_still_otter.xml`
predecessors and with `OtterZenMark`'s Compose path constants.

Verified by rendering both drawables outside Android with the launcher's
circular mask and the safe circle drawn on top, then on the emulator's app
drawer (both flavors, whose icons are identical).

The monochrome version keeps the winding trick documented above — eyes and
nose cut out by drawing them with the opposite sweep flag under the nonzero
rule, never `evenOdd`, which would eat the ears where they overlap the head.
Verified again the same way: rendering the path data outside Android and
looking at the whole silhouette, ears included.
