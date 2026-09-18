# Multi-Theme System — Design

## Key files

| File | Role |
|---|---|
| `ThemeManager.kt` | Reads/writes the chosen `AppTheme` (plain `SharedPreferences`); resolves the correct `@style` for an Activity+variant combination |
| `BaseActivity.kt` | Calls `ThemeManager.applyTheme()` before `super.onCreate()` |
| `res/values/themes.xml` (+ `values-night/themes.xml`) | 3 palettes × 3 variants of AppCompat/Material3 XML themes |
| `res/values/colors.xml` | Color values referenced by `themes.xml` (`sage_*`, `dusk_sand_*`, `dawn_clay_*`, plus shared `m3_*` structural colors) |
| `res/values-night/colors.xml` | Overrides only `sage_primary`/`dusk_sand_primary`/`dawn_clay_primary` with their dark-mode values — the one place any of `colors.xml` gets a night variant, added so the theme-list swatches (`ThemeListRow`, which reads these 3 names via `colorResource()`) show the right color in dark mode |
| `ui/theme/CalmOtterTheme.kt` | Independent Compose `MaterialExpressiveTheme` color schemes, one per palette × light/dark |

## Two parallel systems — this is intentional, keep them in sync manually

Every screen is Compose content (see CLAUDE.md), but each Activity is still
an `AppCompatActivity` with a real Android window/status bar/splash that
needs a theme applied *before* Compose ever renders — that's what
`BaseActivity.onCreate()` → `ThemeManager.applyTheme()` → `setTheme(styleId)`
does, using the XML `themes.xml`/`colors.xml` system. Once inside Compose,
`CalmOtterTheme(appTheme = ...)` wraps content in a `MaterialTheme` built
from **hardcoded** `Color(...)` literals in `CalmOtterTheme.kt` — it does
**not** read `@color/*` resources.

**Consequence for future changes**: editing a palette color in
`colors.xml`/`themes.xml` alone changes the window chrome but not the
Compose content (or vice versa). Any palette color change must be mirrored
in both places by hand — each `Color(...)` literal in `CalmOtterTheme.kt`
carries a `// @color/xxx` comment naming the XML value it was copied from,
specifically so this can be checked at a glance.

**Also non-obvious**: `values/themes.xml` (light) and `values-night/themes.xml`
(dark) are not the same design language — only the light file was updated
by the "Desing.md" M3 redesign; the night file still uses the pre-redesign
MaterialComponents attributes (`colorPrimaryVariant`,
`android:textColorPrimary/Secondary`) with hardcoded hex values, not
`@color/*` references — `values-night/colors.xml` now exists (see "Key
files" above and the theme-picker-dots fix below), but only as a narrow,
separate override for 3 color names the dot drawables read; the dead
`pause_*`-only version of this file that used to exist was removed earlier
and `values-night/themes.xml` still doesn't reference it or any other
`@color/*` name. Don't assume light and dark hex values for the same
palette should look like tint/shade variants of each other; they were
authored separately. `CalmOtterTheme.kt`'s light/dark schemes correctly
mirror this split (`*Light` from `values/`, `*Dark` from `values-night/`).

## `ThemeVariant`

Three variants per palette, chosen per-Activity via
`override val themeVariant`:
- `BASE` — plain `NoActionBar` theme (most Compose screens draw their own top bar or none)
- `WITH_ACTION_BAR` — settings-style screens (`HistoryActivity`,
  `ChangePasswordActivity`, `AllowedAppsActivity`, `OnboardingActivity`) that
  use the native `supportActionBar` for a title + Up button
- `BLOCK` — the full-bleed block screen (`BlockOverlayActivity`); `MainActivity`
  stays on `BASE` even when it shows this same block screen (Home button
  pressed during an active session — see
  `app-blocking-and-home-lock/design.md`'s "One Activity, two roles"),
  since `BASE` and `BLOCK` resolve to identical XML styles anyway (the
  `.Block` aliases below add nothing)

## `WITH_ACTION_BAR`'s overflow menu needs its own `ThemeOverlay`, not just `colorSurface`

`HistoryActivity`'s overflow menu ("Export history"/"Clear history") was
reported directly as wrong-colored ("il menù di export ha il colore
errato e non del tema") — a fixed Material3 lavender in light mode, a
fixed neutral dark grey (`#121212`-ish) in dark mode, neither tracking
Sage/Dusk Sand/Dawn Clay.

**Why setting `colorSurface` (already correctly per-palette) and even
`popupMenuBackground` directly on `Theme.CalmOtter.<Palette>.WithActionBar`
does *not* fix this**, verified by measuring actual rendered pixel values
(not just eyeballing a screenshot — a first attempt at this fix looked
plausibly closer in a screenshot but measured as unchanged): the
ActionBar's overflow popup is not rendered using the Activity's own theme
directly. Both `Theme.Material3.DayNight` and `Theme.MaterialComponents.DayNight`
(the light/dark parents here — see "Two parallel systems" above) set
their own `actionBarPopupTheme` to a fixed library `ThemeOverlay`
(`ThemeOverlay.Material3.Light` / `ThemeOverlay.MaterialComponents.Dark`),
and the overflow popup is shown inside *that* overlay's `ContextThemeWrapper`,
which defines its own `colorSurface` independent of whatever the
Activity's outer theme says. Overriding `colorSurface`/`popupMenuBackground`
on the outer theme never reaches it.

**Fix**: define a `ThemeOverlay.CalmOtter.<Palette>.PopupMenu` per palette
(extending the library's own default overlay for that theme family — the
same distinction as light vs. dark below), and point `actionBarPopupTheme`
at it from each `.WithActionBar` style:
- `values/themes.xml` (light, Material3-based): `ThemeOverlay.CalmOtter.Sage.PopupMenu`
  etc. extend `ThemeOverlay.Material3.Light`.
- `values-night/themes.xml` (dark, still MaterialComponents-based — see
  "Two parallel systems"): the same 3 overlays extend
  `ThemeOverlay.MaterialComponents.Dark` instead, and use their own
  `sage_dark_surface`/`dusk_sand_dark_surface`/`dawn_clay_dark_surface`
  color names (new, in `values-night/colors.xml`, mirroring the palette's
  existing inline hex `colorSurface`) rather than a `values/colors.xml`
  name, consistent with this file's established pattern of not touching
  `@color/*` names owned by the light redesign.

Each overlay sets **both** `colorSurface` (for consistency/ripples/text)
**and** `popupMenuBackground` directly (not relying on `colorSurface`
alone): `Widget.Material3.PopupMenu.Overflow`'s actual background drawable
resolves through an M3 "macro" token
(`@macro/m3_comp_menu_container_color`), which isn't guaranteed to key off
`colorSurface` specifically — setting `popupMenuBackground` directly on
the overlay bypasses that ambiguity entirely and is what the widget style
actually consults.

**`popupMenuBackground` is `format="reference"` only** — a raw `#hex`
literal fails AAPT2 linking ("expected reference but got raw string"); it
must be a `@color/name`. This is why `values-night/colors.xml` gained 3
new dark-surface color names instead of inlining hex directly in the
`ThemeOverlay` (consistent with the "narrow, separate override" pattern
already described above for the 3 `*_primary` names) — and why
`values/colors.xml` needed matching (unused-in-practice, lint-required)
fallback declarations for those same 3 names, since a `values-night`-only
color resource without a base-`values` declaration is a lint error
(`MissingDefaultResource`).

## Material 3 Expressive

`CalmOtterTheme` wraps content in `MaterialExpressiveTheme` (not plain
`MaterialTheme`), giving every Material3 component in the app the
Expressive motion scheme, shapes, and typography defaults automatically —
one theme-level switch, not a per-screen/per-component change. This
requires `material3` 1.5.0-alpha, pulled in via
`androidx.compose:compose-bom-alpha:2026.09.00` in `app/build.gradle.kts`
instead of the stable `compose-bom`. That in turn required bumping
compileSdk/targetSdk to 37 (`compileSdkMinor = 1`), AGP to 9.4.0, and the
Gradle wrapper to 9.7.0 — the alpha Compose artifacts compile against a
newer Android API surface and require a newer AGP than the stable channel
did.

**This is a deliberate alpha dependency, not an oversight.** As of this
writing, stable `material3` (1.4.0) does **not** expose
`MaterialExpressiveTheme` or `MotionScheme.expressive()`/`standard()` to
app code — verified by decompiling the actual resolved jar: the methods
exist in the bytecode but are Kotlin `internal`, inaccessible outside the
material3 module. They only become public starting in the 1.5.0 alpha
line. Revisit this once material3 1.5.0 (or whichever version stabilizes
Expressive) ships stable, and consider moving back to the stable
`compose-bom` (and re-evaluating whether the AGP/Gradle/compileSdk bump is
still needed) at that point.

## Applying a new theme without `recreate()`: `MainActivity`

`SettingsActivity.pickTheme()` calls `recreate()` on itself after
`ThemeManager.setTheme()`, so Settings always shows the new palette
immediately — but `MainActivity` is never recreated when the user backs out
of Settings, it's only resumed. That used to mean Home kept showing the
*old* palette (Compose content **and** the status bar) until the whole
process was killed and relaunched — a real bug, not just a cosmetic delay,
since `CalmOtterTheme(appTheme = ThemeManager.getTheme(this))` was a plain
function call evaluated once when `setContent {}`'s composition tree was
first built; nothing downstream of it read a state that would cause it to
re-run on resume.

Fixed with two independent pieces, since the Compose content and the status
bar are the two-parallel-systems split described above and neither one
fixes the other:

1. **Compose content**: `MainActivity` now holds `currentTheme` as
   `mutableStateOf(AppTheme)` instead of calling `ThemeManager.getTheme(this)`
   inline inside `setContent {}`. It's set once in `onCreate()` and
   reassigned in `onResume()` (alongside the existing `resumeSignal++`).
   Because `CalmOtterTheme(appTheme = currentTheme)` reads this state
   directly at its own call site, reassigning it now correctly invalidates
   and recomposes that scope with the new `ColorScheme` — no `recreate()`
   needed, just a normal Compose recomposition.
2. **Status bar**: `window.statusBarColor` is a plain Android window
   property applied once, by the system, when `super.onCreate()` builds the
   window (from the XML theme `BaseActivity`/`ThemeManager.applyTheme()`
   set right before it) — it does not update on its own afterward.
   `MainActivity.onResume()` now fixes this in two steps, both required:
   first it calls `ThemeManager.applyTheme(this, themeVariant)` again (the
   same call `BaseActivity.onCreate()` made, just re-run) — this alone has
   no visible effect since it only updates which style the Activity's
   `Resources.Theme` object will resolve attributes against *going
   forward*, it doesn't retroactively touch the already-created window.
   Then `window.statusBarColor = MaterialColors.getColor(this,
   com.google.android.material.R.attr.colorPrimary, Color.BLACK)` reads
   `colorPrimary` back out of that just-updated theme object and applies it
   to the window explicitly. Skipping the `applyTheme()` re-call was an
   earlier, incomplete version of this fix: `MaterialColors.getColor()` was
   still reading the *previous* theme's resolved `colorPrimary`, since
   nothing had told the Activity's theme object to move on.

   Reading `colorPrimary` this way (rather than a fixed
   `@color/{sage,lavender,terracotta}_primary` resource, which was this
   fix's very first attempt) is also what makes it dark-mode-correct for
   free: `values-night/themes.xml` gives each palette its own distinct
   dark `colorPrimary` values (`#6BBFA0`/`#A99ED0`/`#C4927A` — not tinted
   variants of the light `colors.xml` values, see "Two parallel systems"
   above), and `MaterialColors.getColor()` resolves whichever one is
   currently active automatically. A `colors.xml`-resource-based version
   would have needed its own light/dark branching to get this right, and
   would still have been wrong if it had reused
   `ThemeManager.accentColor()` — that function's hardcoded
   Dusk Sand/Dawn Clay hex values have drifted from `colors.xml` and don't
   match either; it's currently unused anywhere else, so the drift had
   gone unnoticed.

   This status-bar line only matters on Android <15 — targetSdk 37's
   mandatory edge-to-edge makes the status bar transparent (no strip to
   color) on 15+ regardless, per CLAUDE.md.

**Related, separately fixed**: the old theme-picker's three dots
(`ThemeDot`/`theme_dot_*.xml`, since replaced — see "Theme picker UI"
below) always showed each palette's *light* `colorPrimary` swatch (from
`@color/*_primary` in `colors.xml`, which had no night variant), even when
the app was in dark mode and that palette's actual dark `colorPrimary` is a
different color (e.g. Sage's dot showed `#0F5238` in dark mode, but
selecting it applies `#6BBFA0`) — noticed while verifying the fix above
across both light and dark mode. Fixed the same way Android resource
qualifiers are meant to be used: a new `values-night/colors.xml` overrides
just the 3 `*_primary` color names with their dark-mode values
(`#6BBFA0`/`#A99ED0`/`#C4927A`, matching `values-night/themes.xml`'s own
`colorPrimary` for each palette). At the time, the dot drawables already
referenced `@color/{sage,lavender,terracotta}_primary`, so they
automatically resolved against whichever `colors.xml` (default or
`-night`) matched the current mode; today the same 3 names are read
directly in Compose via `colorResource()` (see "Theme picker UI" below),
which resolves day/night the same automatic way. No other consumer of
these 3 color names exists (checked directly), so this override remains
safe — it doesn't touch `values/themes.xml` (light mode; unaffected) or
`values-night/themes.xml` (doesn't read `@color/*` at all, uses its own
inline hex — see "Two parallel systems" above).

Every other Activity this app has is either recreated on the one path that
changes the theme (`SettingsActivity`) or is always freshly `startActivity`'d
after any theme change rather than resumed from the back stack
(`HistoryActivity`, `ChangePasswordActivity`, `AllowedAppsActivity`) — so
`MainActivity` was the only place this bug could actually manifest.

## Theme picker UI

`SettingsScreen`'s theme section was originally `ThemePicker`/`ThemeDot`
(moved there from `MainScreen` by the Home/Settings split, see
`specs/home-and-settings/design.md`), which wrapped the `theme_dot_*.xml`
`StateListDrawable`s (selection ring) via `AndroidView` rather than
reproducing the ring in Compose — a deliberate choice at the time to stay
pixel-identical to the pre-Compose version rather than risk a subtly
different visual.

A later "full list-card redesign" pass (see
`specs/home-and-settings/design.md`) replaced this outright with
`ThemeListCard`/`ThemeListRow`, a plain Compose list matching the visual
style used everywhere else in Settings: each row is a `colorResource(R.color.*_primary)`
swatch circle (reactive to day/night, same 3 names as above — no
`AndroidView`, no `StateListDrawable`) plus a label plus a "Selected" text
on the active row, in place of the dot's selection-ring styling.
`theme_dot_*.xml` and the `ThemeDot`/`ThemePicker` composables were deleted
outright once nothing referenced them, rather than left orphaned — the
selection-ring visual (dot-based) is gone; the active row is now indicated
by the "Selected" label alone.


## The four palettes come from the redesign, and two replaced older ones

The palettes are no longer hand-picked: their values are imported from the
design systems generated in Stitch for the app redesign (project
`3158702940609906617`) — "Sage Sanctuary", "Calm Otter Sanctuary" (Deep
Forest), "Dusk Sand Sanctuary" and "Dawn Clay". Dusk Sand and Dawn Clay took
the place of Lavender and Terracotta, which no longer exist.

**Stored preferences are not migrated, they are re-read.** `AppTheme.fromKey`
maps the historical keys `"lavender"` and `"terracotta"` onto the palettes
that replaced them, so someone who had picked one of them lands on its
successor rather than being bounced back to the default. The old string stays
on disk until they pick something else: there is nothing to rewrite, only
something to read.

**Sage keeps its own green, on purpose.** The generated "Sage Sanctuary" and
"Calm Otter Sanctuary" systems produce the *same* primary (`#1b3b2b`), which
would have made Sage and Deep Forest two identical entries in a list of four.
Sage therefore keeps `#0f5238` — lighter and more saturated — and takes from
the redesign only its surfaces, neutrals and containers.

**Dark mode is derived, not copied.** The redesign also ships a dark system,
"Nocturnal Sanctuary" (`#0e1510` ground, light desaturated green accent).
Adopting it as-is would have made all four palettes look the same at night,
so what is reused is its *structure* — near-black ground, surface a step
lighter, light accent — with each palette's own hue for the accent and the
ground. That also finally replaces the hand-written dark values this document
used to describe as "never updated by the redesign".

**Typography is now part of the theme.** `CalmOtterTheme` passes a
`CalmOtterTypography` built on Plus Jakarta Sans (`ui/theme/Type.kt`), the
typeface the design system prescribes. The font ships inside the APK as two
variable files (upright and italic, ~360KB together, SIL OFL — see
THIRD_PARTY_LICENSES.md); Downloadable Fonts would have routed through Google
Play Services, which this app does not do. Sizes stay at Material 3's
defaults — the screens set their own `fontSize` almost everywhere, so
rescaling here would only have moved library components out of step with
them; what changes is the typeface, the heavier headline weights and their
tighter tracking.


## `secondary` and `tertiary` are real roles now

Reported while reviewing the Home: "those aren't Stitch's colours". They were
not — everything tinted was `primary` faded with alpha, which on a green
palette gives grey-greens, not the greens of the design.

In the design system those two are not variations of `primary`, they have
jobs: **`secondary`** (`#8fa693` in the green palettes) is the mascot and the
secondary controls, **`tertiary`** (`#d5e0d5`) is the pond rings, the ripple
borders and the quiet fills. Both are now set explicitly in all eight colour
schemes, and mirrored in `values/colors.xml` as `*_accent` / `*_veil` for the
places that read resources rather than the Compose theme.

So: the otter's fur is `secondary` with a gradient toward `primary`, its halo
is `secondary` at 22%, the pond rings and ripples are `tertiary` at nearly
full strength (they are already a pale colour — fading them with alpha was
what greyed them out), and unselected duration pills are `tertiary` at 55%.

This is the same trap CLAUDE.md documents for `surfaceVariant` and
`primaryContainer`, seen from the other side: those roles fall back to
Material's stock purple when unset, while these two were being *avoided*
altogether and replaced with translucent `primary`.
