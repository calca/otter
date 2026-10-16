# Third-party licenses

## Plus Jakarta Sans

`app/src/main/res/font/plus_jakarta_sans.ttf` and
`plus_jakarta_sans_italic.ttf` are the variable-weight builds of **Plus
Jakarta Sans** by Tokotype, taken from Google Fonts
(`ofl/plusjakartasans` in <https://github.com/google/fonts>).

Copyright 2020 The Plus Jakarta Sans Project Authors
(<https://github.com/tokotype/PlusJakartaSans>), licensed under the **SIL
Open Font License, Version 1.1** — see <https://openfontlicense.org>.

The fonts are bundled inside the APK on purpose: Downloadable Fonts would
route through Google Play Services, and this app makes no network calls of
any kind (see CONTRIBUTING.md).

## Material Icons (single paths)

A few icons missing from `material-icons-core` are copied as their SVG path
data instead of pulling in `material-icons-extended`: "visibility" /
"visibility_off" (`EyeGlyph`, password field), "history" (`HistoryIcon`,
Home header), and the outlined "file_download" and "delete" in
`res/drawable/ic_history_*.xml` (History's action bar). They come from **Material Icons** by Google
(<https://github.com/google/material-design-icons>), licensed under the
**Apache License, Version 2.0** — see
<https://www.apache.org/licenses/LICENSE-2.0>.
