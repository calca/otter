# Multi-Theme System — Design

> **Stato attuale.** I colori hanno **una sola fonte**: le risorse
> `<palette>_<ruolo>` di `values/colors.xml` (chiaro) e `values-night/colors.xml`
> (scuro). Le sezioni da "Material 3 Expressive" in giù sono la storia di come
> si è arrivati qui, scritta quando esistevano ancora due sistemi paralleli, la
> palette Foresta profonda e i temi XML per palette: dove citano
> `values-night/themes.xml`, i letterali di `CalmOtterTheme.kt`,
> `CalmOtterThemeColorSyncTest` o Deep Forest, descrivono il passato. Il
> comportamento vigente è quello di queste prime sezioni.

## Key files

| File | Role |
|---|---|
| `res/values/colors.xml` | **Unica fonte dei colori**: nove ruoli per palette (`sage_*`, `still_water_*`, `dusk_sand_*`, `dawn_clay_*`) più i `m3_*` strutturali, valori chiari |
| `res/values-night/colors.xml` | Gli stessi nomi con i valori scuri (e `m3_error` scuro). Android sceglie da solo il file in base alla modalità |
| `res/values/themes.xml` | Due basi strutturali (`Theme.CalmOtter.Base`, `.Base.WithActionBar`, entrambe `Theme.Material3.DayNight`) e **un overlay per palette** (`ThemeOverlay.CalmOtter.<Palette>`) che porta solo colori, come riferimenti a `colors.xml`. Non esiste più un `themes.xml` notturno |
| `ThemeManager.kt` | Legge/scrive l'`AppTheme` scelto (`SharedPreferences`); `applyTheme()` applica base + overlay |
| `BaseActivity.kt` | Chiama `ThemeManager.applyTheme()` prima di `super.onCreate()` |
| `ui/theme/CalmOtterTheme.kt` | Compose: `PaletteColors` (id di risorsa per palette) e `CalmOtterTheme`, che costruisce lo `ColorScheme` con `colorResource` — nessun letterale di colore |
| `test/.../CalmOtterPaletteTest.kt` | Ogni palette completa in chiaro e scuro, contrasto di testo e CTA, tonalità distinte |

## One source, two readers

Ogni schermata è Compose, ma ogni Activity è ancora una `AppCompatActivity`
con una finestra reale (sfondo, barra di stato, ActionBar nativa) che ha
bisogno di un tema prima che Compose disegni. Prima c'erano due copie dei
colori da tenere allineate a mano (`colors.xml`/`themes.xml` e i letterali
`Color(...)` di `CalmOtterTheme.kt`), con un test che confrontava solo il
chiaro; il deriva aveva già causato bug veri. Ora i colori stanno in un
posto solo e i due lettori puntano allo stesso:

- **Finestra XML:** gli overlay in `themes.xml` (`colorPrimary`,
  `windowBackground`, `colorSurface`, ...) sono riferimenti a `@color/...`.
- **Compose:** `CalmOtterTheme` legge le stesse risorse con `colorResource`,
  quindi prende il valore scuro in dark mode senza `if` suoi.

Cambiare un colore significa cambiare **una riga** (due per chiaro e scuro).
`CalmOtterPaletteTest` difende ciò che resta: che una palette abbia tutti i
ruoli in entrambe le modalità (una risorsa mancante in `values-night/`
ricadrebbe in silenzio sul valore chiaro), che il contrasto di testo e CTA
regga (>= 4.5:1), e che due palette non abbiano la stessa tonalità.

I nove ruoli: `background`, `surface`, `surface_bright` (il disco centrale
dello stagno), `on_background`, `on_surface`, `primary`, `on_primary`,
`accent` (mascotte e controlli, `secondary` di Compose) e `veil` (anelli e
riempimenti tenui, `tertiary` di Compose). Gli altri ruoli Material3
(`surfaceVariant`, `primaryContainer`, ...) restano ai default della libreria,
come prima.

## Applying a palette

`applyTheme()` fa due `setTheme()` in sequenza: la base (con o senza
ActionBar) e poi l'overlay della palette, che sovrascrive i colori. Il
tema di partenza del manifest (`Theme.CalmOtter`, `.WithActionBar`) è sempre
Sage: è la finestra iniziale, prima che l'Activity applichi la scelta. Per
un cambio palette al volo (vedi "Applying a new theme without `recreate()`")
basta richiamare `applyTheme()`: l'overlay sovrascrive i valori precedenti.

Nell'overlay `colorOnSurface` punta a `on_background` e non a `on_surface`:
l'unico testo XML visibile è il titolo della ActionBar nativa, e in scuro
`on_surface` è più smorzato del testo pieno (titolo spento, verificato).
Compose non ne risente, ha i propri ruoli.

## `ThemeVariant`

Due varianti, scelte per Activity con `override val themeVariant`:
- `BASE` — tema `NoActionBar` (la maggior parte delle schermate Compose
  disegna la propria barra o nessuna), compresa la schermata di blocco
  (`BlockOverlayActivity`, e `MainActivity` quando la mostra: vedi
  `app-blocking-and-home-lock/design.md`, "One Activity, two roles").
- `WITH_ACTION_BAR` — schermate stile impostazioni (`HistoryActivity`,
  `ChangePasswordActivity`, `AllowedAppsActivity`, `OnboardingActivity`,
  `SettingsActivity`) che usano la `supportActionBar` nativa per titolo e
  freccia indietro.

La variante `BLOCK` e gli alias `.Block` sono stati tolti: erano identici a
`BASE`. Non c'è più nemmeno il tema del popup dell'overflow della
ActionBar (`actionBarPopupTheme`): l'app non ha menu a comparsa, le azioni
di Cronologia sono sempre visibili (`SHOW_AS_ACTION_ALWAYS`). Se un giorno
ne comparisse uno, andrebbe aggiunto un overlay per il popup.

## The four palettes

Salvia (verde), **Acqua ferma** (blu-petrolio, l'unica palette fredda), Sabbia
al tramonto (marrone caldo) e Argilla all'alba (terracotta). Acqua ferma ha
preso il posto di Foresta profonda: era un secondo verde (primary chiaro
`#1B3B2B` contro `#0F5238` di Salvia, stesso accento e stesso velo; in scuro
`#639786` contro `#6B9E7C`, quasi indistinguibili), cioè due voci quasi
identiche in una lista di quattro. La chiave salvata è `still_water`; non c'è
retrocompatibilità con `deep_forest` né con le chiavi storiche `lavender`/
`terracotta` (l'app non era pubblica): una chiave sconosciuta ricade su
Salvia. Con la stessa modifica il testo sul CTA scuro di Salvia è passato da
`#02391A` a `#002E17` (contrasto 4.25 → 4.85): il test di contrasto ha
mostrato che era sotto la soglia di 4.5:1 dichiarata dalla storia qui sotto.

Valori Acqua ferma — chiaro: primary `#1F5A6B`, sfondo `#F6F9FA`, accento
`#8FA9B3`, velo `#D5E2E6`; scuro: primary `#5E94A6` (smorzato come gli altri,
vedi "CTA troppo brillanti" più sotto), sfondo `#0C1417`, velo `#263339`.

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


## The Lavender→Dusk Sand and Terracotta→Dawn Clay renames never fully reached `CalmOtterTheme.kt`

Found writing a test (`TODO.md` "2.3": "nothing enforces the two-palette
sync rule"), not reported directly — but it's exactly the class of bug that
rule exists to catch.

`CalmOtterThemeColorSyncTest` resolves every `@color/*_surface`, `*_primary`,
`*_on_surface`, `*_on_primary`, `*_accent`, `*_veil` (plus the shared
`m3_error`/`m3_on_error`) for all four palettes and compares them against
`CalmOtterTheme.kt`'s `light*` `ColorScheme`s — the correspondence the
file's own `// @color/xxx` comments already claimed, turned into an
assertion. First run failed immediately: `DuskSandLight`'s `surface` was
`0xFFFDF7FF` (`@color/lavender_surface`, the *old* palette's value) against
`values/colors.xml`'s actual `dusk_sand_surface` of `#fcf9f4` — a real, live
mismatch, not a test bug.

Full extent, once checked by eye: the Lavender→Dusk Sand and
Terracotta→Dawn Clay renames (see `AppTheme.kt`'s `renamed` map) had updated
`secondary`/`tertiary` (`*_accent`/`*_veil`) in `CalmOtterTheme.kt` but not
`surface`/`primary`/`onSurface`/`onPrimary` — both `DuskSandLight` and
`DawnClayLight` were rendering their *old* palette's core colors (Lavender's
purple, Terracotta's orange-red) while their pond rings/mascot tint
(secondary/tertiary) had already moved to the new tan/brown and clay tones.
Anyone on either theme was seeing a color scheme that never fully existed in
either the old or new design — half-migrated, not merely stale. Only the
light schemes were affected; dark schemes have never been tied to
`values/colors.xml` in the first place (see the class doc — they come from
`values-night/themes.xml`'s pre-redesign MaterialComponents colors), so
there was nothing to drift there.

Fixed by replacing the three stale values in both `DuskSandLight` and
`DawnClayLight` with the real `dusk_sand_*`/`dawn_clay_*` hex values from
`values/colors.xml`, correcting the comments to match, and adding an
explicit note on why this took two commits (or would have, without the
test) to actually finish.

Also fixed in the same pass: `lightSchemeFor()` changed from `private` to
`@VisibleForTesting internal` so the test can call it directly — same
pattern as `CalmOtterDatabase.MIGRATION_1_2`/`MIGRATION_2_3` (TODO.md "1.4").

Verified: `assembleDebug`/`lintStableDebug`/`lintBetaDebug`/
`testStableDebugUnitTest`/`testBetaDebugUnitTest` all green, the sync test
passes for all four palettes; installed on the emulator, switched Settings
to Dusk Sand then Dawn Clay, screenshot confirmed both now render their
correct tan/brown and clay tones (not the old purple/orange-red) with no
crash, then switched back to Sage.


## A dependency bump broke `MainActivity`'s status bar color — `com.google.android.material.R.attr.colorPrimary` stopped resolving

Found bumping `material:material` 1.12.0 → 1.14.0 as part of clearing the
dependency drift in `TODO.md` "6.2" — a straight version-number edit, not
a code change, immediately failed the Kotlin compile with `Unresolved
reference 'colorPrimary'` at `MainActivity.kt`'s `MaterialColors.getColor(
this, com.google.android.material.R.attr.colorPrimary, ...)` call, which
sets `window.statusBarColor` to match the active theme (see "DND priority
categories" — no, wrong file; this call reads the *already-applied* theme
attribute via `MaterialColors`, so it automatically picks up whichever of
the four palettes × light/dark is active, the same way `values-night/
themes.xml`'s own `colorPrimary` differs from `values/colors.xml`'s).

Material had been re-declaring its own `colorPrimary` attr as a
pre-Lollipop compatibility shim since long before this project started;
1.14.0 apparently stopped doing that. Fixed by pointing at
`android.R.attr.colorPrimary` instead — the platform's own attribute,
present since API 21, which this project's `minSdk 26` has always
satisfied anyway. `MaterialColors.getColor()` itself is untouched;
only which `R.attr` constant gets passed to it changed.

Verified: compiles clean again on `material:material:1.14.0`, full
`assembleDebug`/`lintStableDebug`/`lintBetaDebug`/
`testStableDebugUnitTest`/`testBetaDebugUnitTest` pass. Not screenshot-
diffed specifically for status bar color (targetSdk 37 makes the status
bar transparent under edge-to-edge on the Android 15+ emulator this
session used anyway, per `CLAUDE.md` — this code path is mostly inert on
that OS version, and was already documented as "still visible on older
versions" before this fix). Confirmed instead via a live Home → pause →
unlock round trip on the emulator (this exact code runs on every one of
those transitions) with no crash and no exception in `adb logcat`.


## `ThemeManager.accentColor()`, a fourth undocumented copy of the palette values, deleted

`TODO.md` "7": found while mapping every place a theme's colors are hand-
copied for "2.3" above — `accentColor()` (`ThemeManager.kt`) was a third
(really fourth, counting this file's own two) hardcoded copy of the four
palettes' primary colors, called from nowhere in the app, and already
wrong for `DUSK_SAND` (`0xFF61462B` against the real `#61462d` — the same
class of rename-left-behind bug fixed elsewhere in this file, just in code
nothing ever executed). Deleted rather than fixed: fixing a value nothing
reads doesn't remove the risk, it just makes the dead copy *currently*
correct until the next rename drifts it again. One fewer place this
project's own convention ("if you touch one palette, mirror the change in
the other file") has to be remembered.


## Sage dark-mode `primary`, `#9ED3A8`, too bright/saturated for a "calm" theme — dimmed to `#6B9E7C`

Reported directly: the mint-green used as Sage's dark-mode primary read as
an energetic brand accent, not something relaxing to look at at night —
fair, at ~72% lightness it was closer to a highlighter than to real sage
foliage. Replaced with a darker, more desaturated `#6B9E7C` across all four
places this project keeps a copy of it (see "if you touch one palette,
mirror the change in the other file" above, this palette needed the XML
side *and* the Compose side touched in the same pass):

- `values-night/colors.xml`: `sage_primary` and `sage_accent` (the popup-
  menu/swatch copy, see that file's own header comment for why `accent`
  duplicates `primary` here).
- `values-night/themes.xml`: `colorPrimary`/`colorPrimaryVariant` in both
  `Theme.CalmOtter.Sage` and `Theme.CalmOtter.Sage.WithActionBar`.
- `CalmOtterTheme.kt`'s `SageDark`: `primary` and `secondary`.

`colorOnPrimary`/`onPrimary` (`#02391A`) was left untouched — contrast
against the new, darker primary computes to ~4.5:1 (WCAG AA for normal
text), actually *better* than against the old, brighter primary it was
originally paired with, so no follow-up needed there.

Verified: `testStableDebugUnitTest`/`testBetaDebugUnitTest` (including the
sync test that cross-checks this file against `CalmOtterTheme.kt`),
`lintStableDebug`/`lintBetaDebug`, `assembleDebug` all green; installed on
the emulator, forced dark mode (`adb shell cmd uimode night yes`),
screenshotted Home in Sage — otter mark, "1h" chip, and the settings gear
all render the new muted green with no readability loss, then switched
back to light mode.


### Follow-up: the other three dark-mode primaries had the exact same problem

Reported directly right after the Sage fix above ("anche gli altri temi,
in dark hanno problemi con il colore delle CTA"). Checked the other three
dark-mode primaries and found the same pattern: `Deep Forest`'s `#ABCFB8`,
`Dusk Sand`'s `#E3C39A`, and `Dawn Clay`'s `#F0B3A2` all sat at ~72–79%
lightness — the same pastel-highlighter territory as Sage's original
`#9ED3A8`, just in a different hue per palette. Deep Forest's value had
extra history: it was never invented for this app, it's the literal
`inverse-primary` token from the Stitch design system that seeded this
palette (see that color's own comment in `values-night/colors.xml`) —
correct for whatever context Stitch designed it for, not necessarily for
this app's "calm at night" requirement.

Fixed the same way as Sage, computed rather than eyeballed: took each
palette's own *light-mode* primary hue (`values/colors.xml`:
`deep_forest_primary` `#1B3B2B`, `dusk_sand_primary` `#61462D`,
`dawn_clay_primary` `#6E352B`) as the hue anchor, then applied the same
lightness/saturation reduction ratio Sage's fix used (roughly ×0.72 on L,
×0.55 on S, derived by comparing Sage's own before/after HSL) to each
palette's *old* bright dark-mode primary. Deep Forest's hue was nudged a
few degrees cooler/bluer than Sage's new `#6B9E7C` specifically so the two
greens stay visually distinct from each other, matching the "Deep Forest
is cooler, Sage is warmer" relationship the light-mode primaries already
have. Resulting values, each re-verified by computing WCAG contrast
against that palette's existing (untouched) `colorOnPrimary`/`onPrimary`:

| Palette    | Old (dark)  | New (dark)  | Contrast vs `onPrimary` |
|------------|-------------|-------------|--------------------------|
| Deep Forest| `#ABCFB8`   | `#639786`   | 4.78:1 vs `#00281A`      |
| Dusk Sand  | `#E3C39A`   | `#B59473`   | 4.89:1 vs `#3A2A16`      |
| Dawn Clay  | `#F0B3A2`   | `#BF7969`   | 4.52:1 vs `#3E1A12`      |

All three clear WCAG AA for normal text (≥4.5:1) — Sage's own `#6B9E7C`
computes to ~4.55:1 against `#02391A` for reference, so the whole set is
now consistently at or above that bar (better than before the fix in
every case, since the old bright primaries were paired with the same dark
`onPrimary` values and never had contrast checked against them).

Same four places touched per palette as Sage's fix, all three in the same
pass:

- `values-night/colors.xml`: `deep_forest_primary`, `dusk_sand_primary`,
  `dawn_clay_primary`, plus `deep_forest_accent` (mirrors `primary` here
  exactly like `sage_accent` does — `dusk_sand_accent`/`dawn_clay_accent`
  were *not* touched, they already held a distinct, separately-muted
  value in both colors.xml and `CalmOtterTheme.kt`'s `secondary`, unlike
  Sage/Deep Forest where accent duplicates primary).
- `values-night/themes.xml`: `colorPrimary`/`colorPrimaryVariant` in all
  three palettes' `Base`/`.Block` and `.WithActionBar` styles (8 lines
  total, replaced with `sed` across the three old hex values and diffed
  afterward to confirm only those 8 lines changed).
- `CalmOtterTheme.kt`: `primary` in `DeepForestDark`/`DuskSandDark`/
  `DawnClayDark`, plus `secondary` in `DeepForestDark` (mirrors `primary`
  like Sage's `secondary` does; `DuskSandDark`/`DawnClayDark`'s
  `secondary` already held their own distinct accent value, left alone).

Verified: full `testStableDebugUnitTest`/`testBetaDebugUnitTest`
(including the XML/Compose sync test)/`lintStableDebug`/
`lintBetaDebug`/`assembleDebug` green. On the emulator: forced dark mode,
switched through all four palettes via direct `SharedPreferences`
injection (`calm_otter_theme`/`selected_theme`, same technique as the
session-state injection used elsewhere in this project's testing
history), screenshotted each — Deep Forest reads as a cooler muted
teal-green distinguishable from Sage's warmer muted green, Dusk Sand
as a caramel/toffee tan instead of a pale biscuit, Dawn Clay as a muted
terracotta instead of a bright salmon; none of the four reads as
"neon"/highlighter-bright anymore, none lost text/icon legibility on top.
Switched back to light mode afterward.
