# Multi-Theme System — Design

## Key files

| File | Role |
|---|---|
| `ThemeManager.kt` | Reads/writes the chosen `AppTheme` (plain `SharedPreferences`); resolves the correct `@style` for an Activity+variant combination |
| `BaseActivity.kt` | Calls `ThemeManager.applyTheme()` before `super.onCreate()` |
| `res/values/themes.xml` (+ `values-night/themes.xml`) | 3 palettes × 3 variants of AppCompat/Material3 XML themes |
| `res/values/colors.xml` | Color values referenced by `themes.xml` (`sage_*`, `lavender_*`, `terracotta_*`, plus shared `m3_*` structural colors) |
| `ui/theme/CalmOtterTheme.kt` | Independent Compose `MaterialTheme` color schemes, one per palette × light/dark |

## Two parallel systems — this is intentional, keep them in sync manually

Every screen is Compose content (see CLAUDE.md), but each Activity is still
an `AppCompatActivity` with a real Android window/status bar/splash that
needs a theme applied *before* Compose ever renders — that's what
`BaseActivity.onCreate()` → `ThemeManager.applyTheme()` → `setTheme(styleId)`
does, using the XML `themes.xml`/`colors.xml` system. Once inside Compose,
`CalmOtterTheme(appTheme = ...)` wraps content in a `MaterialTheme` built
from **hardcoded** `Color(...)` literals in `CalmOtterTheme.kt` — it does
**not** read `@color/*` resources. The values were originally copied 1:1
from the XML palette (see the doc comment in `CalmOtterTheme.kt`), but nothing
enforces they stay that way.

**Consequence for future changes**: editing a palette color in
`colors.xml` alone will change the window chrome but not the Compose
content (or vice versa). Any palette color change must be mirrored in both
places by hand.

## `ThemeVariant`

Three variants per palette, chosen per-Activity via
`override val themeVariant`:
- `BASE` — plain `NoActionBar` theme (most Compose screens draw their own top bar or none)
- `WITH_ACTION_BAR` — settings-style screens (`HistoryActivity`,
  `ChangePasswordActivity`, `AllowedAppsActivity`, `OnboardingActivity`) that
  use the native `supportActionBar` for a title + Up button
- `BLOCK` — the full-bleed block screen (`BlockOverlayActivity`,
  `HomeActivity`)

## Theme picker UI

`MainScreen`'s `ThemePicker`/`ThemeDot` wrap the existing
`theme_dot_*.xml` `StateListDrawable`s (selection ring) via `AndroidView`
rather than reproducing the ring in Compose — see the doc comment in
`MainScreen.kt`: this was a deliberate choice to stay pixel-identical to the
pre-Compose version rather than risk a subtly different visual.
