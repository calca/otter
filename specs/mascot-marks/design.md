# Mascot Marks — Design

## Key files

| File | Role |
|---|---|
| `res/drawable/ic_launcher_background.xml` | Solid Sage (`#0F5238`) fill, full 108×108dp adaptive-icon canvas |
| `res/drawable/ic_launcher_foreground.xml` | "Still Otter" head — the only mark drawn as a hand-authored vector drawable, since it has to be referenced from a static adaptive-icon XML (and from Glance, which can't run arbitrary Canvas code) |
| `res/drawable/ic_launcher_monochrome.xml` | Head+ears silhouette only, no eyes/nose — themed/Material You icon (Android 13+); simplified further than the color foreground on purpose |
| `res/drawable/ic_otter_widget.xml` | Same silhouette as the foreground, tones inverted (dark head, light details) for the widget's light background — see below |
| `res/mipmap-anydpi-v26/ic_launcher.xml` + `ic_launcher_round.xml` | `<adaptive-icon>` wiring background+foreground+monochrome. minSdk is 26 (the API level adaptive icons shipped in), so there's no legacy PNG fallback to maintain |
| `ui/mascot/OtterMarks.kt` | `OtterFloatMark()`, `PausePawsMark()`, `PactPawsMark()`, `SprigMark()` — all four drawn live via Compose `Canvas`, since none of them ever appear outside a Compose screen. `OtterFloatMark` is used in both `MainScreen.kt` (Home, "Living Pond") and `OnboardingScreen.kt` (step 1) — the same composable, not two copies |

## Why one mark is a vector drawable and the rest are Compose `Canvas`

"Still Otter" needs to exist as a plain Android resource because two of its
consumers aren't Compose at all: the adaptive-icon XML the launcher reads,
and the Glance widget (`PauseWidgetProvider.kt`), which renders via
`Image(provider = ImageProvider(...))` rather than arbitrary drawing calls.
`OtterFloatMark` and `PausePawsMark` only ever appear inside a screen this
app already renders with Compose, so they're plain `Canvas` draw calls —
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
hole. `ic_launcher_monochrome.xml` deliberately does **not** attempt to
punch eye/nose holes into that silhouette via `fillType="evenOdd"` — doing
that correctly requires the eye/nose subpaths to wind in the opposite
direction from the head, which is fragile to get right by hand without a
renderer to check against. Simplifying the monochrome icon down to a
plain silhouette sidesteps the problem entirely and happens to match
Google's own guidance (monochrome/themed icons should be *more* reduced
than the color version, not just recolored).

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
strength). `PausePawsMark` was written against `primary`/`onPrimary` from
the start and never had this problem. If you add another mark, stay inside
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
- The inline `🌿` inside the `onb5_title` *string* ("All set 🌿") was left
  untouched — that's ordinary decorative text-emoji usage, the same
  pattern used throughout the app's strings (`streak_days`'s `🔥`,
  `history_empty`'s `🌿`), not a standalone "icon" replacing an
  illustration, so it wasn't in scope for this fix.

## Why the widget needed its own drawable

`ic_launcher_foreground.xml`'s otter is a light, near-white shape — correct
sitting on the launcher icon's solid Sage background, but it would be a
near-white shape on the *widget's* own background
(`Color(0xFFEAF0E8)`, a very light sage tint), i.e. almost invisible.
`ic_otter_widget.xml` is the same silhouette with the two fills swapped
(dark `#2C4A3E` head, light `#EAF0E8` details) to match the widget's
existing fixed palette instead.
