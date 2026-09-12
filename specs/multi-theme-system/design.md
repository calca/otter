# Multi-Theme System — Design

## Key files

| File | Role |
|---|---|
| `ThemeManager.kt` | Reads/writes the chosen `AppTheme` (plain `SharedPreferences`); resolves the correct `@style` for an Activity+variant combination |
| `BaseActivity.kt` | Calls `ThemeManager.applyTheme()` before `super.onCreate()` |
| `res/values/themes.xml` (+ `values-night/themes.xml`) | 3 palettes × 3 variants of AppCompat/Material3 XML themes |
| `res/values/colors.xml` | Color values referenced by `themes.xml` (`sage_*`, `lavender_*`, `terracotta_*`, plus shared `m3_*` structural colors) |
| `res/values-night/colors.xml` | Overrides only `sage_primary`/`lavender_primary`/`terracotta_primary` with their dark-mode values — the one place any of `colors.xml` gets a night variant, added so the theme-list swatches (`ThemeListRow`, which reads these 3 names via `colorResource()`) show the right color in dark mode |
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
- `BLOCK` — the full-bleed block screen (`BlockOverlayActivity`,
  `HomeActivity`)

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
   Lavender/Terracotta hex values have drifted from `colors.xml` and don't
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
