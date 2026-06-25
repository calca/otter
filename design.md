---
name: Calm Otter
colors:
  surface: '#f9f9f8'
  surface-dim: '#d9dad9'
  surface-bright: '#f9f9f8'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f3f4f3'
  surface-container: '#edeeed'
  surface-container-high: '#e7e8e7'
  surface-container-highest: '#e1e3e2'
  on-surface: '#191c1c'
  on-surface-variant: '#404943'
  inverse-surface: '#2e3131'
  inverse-on-surface: '#f0f1f0'
  outline: '#707973'
  outline-variant: '#bfc9c1'
  surface-tint: '#2c694e'
  primary: '#0f5238'
  on-primary: '#ffffff'
  primary-container: '#2d6a4f'
  on-primary-container: '#a8e7c5'
  inverse-primary: '#95d4b3'
  secondary: '#57615c'
  on-secondary: '#ffffff'
  secondary-container: '#d8e2dc'
  on-secondary-container: '#5b6560'
  tertiary: '#384c43'
  on-tertiary: '#ffffff'
  tertiary-container: '#4f645b'
  on-tertiary-container: '#c9dfd4'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#b1f0ce'
  primary-fixed-dim: '#95d4b3'
  on-primary-fixed: '#002114'
  on-primary-fixed-variant: '#0e5138'
  secondary-fixed: '#dbe5df'
  secondary-fixed-dim: '#bfc9c3'
  on-secondary-fixed: '#151d1a'
  on-secondary-fixed-variant: '#3f4945'
  tertiary-fixed: '#d1e8dc'
  tertiary-fixed-dim: '#b5ccc0'
  on-tertiary-fixed: '#0b1f18'
  on-tertiary-fixed-variant: '#374b42'
  background: '#f9f9f8'
  on-background: '#191c1c'
  surface-variant: '#e1e3e2'
typography:
  display-lg:
    fontFamily: Inter
    fontSize: 57px
    fontWeight: '400'
    lineHeight: 64px
    letterSpacing: -0.25px
  headline-lg:
    fontFamily: Inter
    fontSize: 32px
    fontWeight: '600'
    lineHeight: 40px
  headline-lg-mobile:
    fontFamily: Inter
    fontSize: 28px
    fontWeight: '600'
    lineHeight: 36px
  title-lg:
    fontFamily: Inter
    fontSize: 22px
    fontWeight: '500'
    lineHeight: 28px
  body-lg:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
    letterSpacing: 0.5px
  body-md:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
    letterSpacing: 0.25px
  label-lg:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '500'
    lineHeight: 20px
    letterSpacing: 0.1px
  label-sm:
    fontFamily: Inter
    fontSize: 11px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.5px
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  base: 8px
  margin-mobile: 16px
  margin-tablet: 24px
  gutter: 16px
  stack-sm: 4px
  stack-md: 12px
  stack-lg: 24px
---

## Brand & Style
The design system is centered on the concept of "Digital Respite." It targets users seeking to reduce screen time through an experience that feels quiet, intentional, and non-intrusive.

The visual style is **Modern Corporate with a Soft-Humanist twist**, heavily leveraging Material 3 (M3) principles. It prioritizes high legibility and low cognitive load. By combining the systematic reliability of M3 with a nature-inspired palette, the interface avoids the coldness of typical utility apps, fostering a sense of safety and focus. The emotional response should be one of immediate relief—like exhaling upon entering a quiet room.

## Colors
The palette is designed to minimize eye strain and suppress the "urgency" typically associated with mobile notifications.

- **Primary (Soft Teal):** Used for key actions and active states. It provides a grounded, organic focal point.
- **Secondary (Sage Green):** Used for container backgrounds, tonal fills, and low-emphasis accents.
- **Tertiary/On-Surface (Deep Navy):** Reserved for high-contrast text and critical iconography to ensure AA/AAA accessibility.
- **Surface (Off-White):** A warm neutral base (#F8F9F8) that prevents the harsh glare of pure white (#FFFFFF), essential for a detox environment.

## Typography
The design system utilizes **Inter** for its exceptional legibility and neutral, modern character.

- **Headlines:** Use a slightly tighter letter-spacing and semi-bold weights to create a clear structural hierarchy.
- **Body:** Standardized at 16px for primary reading to ensure comfort for all age groups.
- **Labels:** Used for secondary metadata and button text, employing a medium weight to maintain visibility against colored backgrounds.

## Layout & Spacing
Following Material 3's adaptive guidance, this design system uses an **8dp spacing grid**.

- **Grid:** On mobile, use a 4-column fluid grid with 16dp margins. On tablets, transition to an 8-column grid with 24dp margins.
- **Padding:** Internal component padding should be generous. Avoid "cramping" text.
- **Rhythm:** Use "Stack" spacing (vertical) to group related items. A 24dp gap should exist between distinct logical sections, while 12dp is used for elements within a group (e.g., a card title and its body text).

## Elevation & Depth
Depth is communicated through **Tonal Layers** rather than heavy shadows, adhering to the "Flat-Plus" aesthetic of Material 3.

- **Level 0 (Surface):** Off-white background.
- **Level 1 (Cards/Containers):** Uses the Secondary color (Sage Green) or a slight tonal shift from the background with a very soft, diffused shadow (Blur 8dp, Spread 0, Opacity 4%).
- **Level 2+ (Dialogs/FABs):** Increased tonal prominence. High-elevation components should use a subtle primary-tinted shadow to maintain warmth.

## Shapes
The shape language is defined by **High Circularity**.

In accordance with Material 3’s "Extra Large" shape family, primary containers like Cards and Bottom Sheets must use a corner radius of **28dp**. This extreme roundedness removes "sharpness" from the UI, reinforcing the calm and friendly brand personality. Small components like buttons use a fully rounded (pill) shape.

## Components
- **Buttons:** Use pill-shaped (fully rounded) contours. The Primary Button uses the Soft Teal fill with Off-White text. Outlined buttons use a 1.5dp stroke in Soft Teal.
- **Cards:** Use a 28dp corner radius. Surfaces are filled with Sage Green at 30% opacity or a solid Off-White with a Level 1 shadow.
- **Chips:** Used for filter categories (e.g., "Meditation", "Focus Mode"). These should be pill-shaped with a 1dp Sage Green border.
- **Input Fields:** Filled style (M3 standard) with a 28dp top-corner radius. The active indicator (underline) uses the Primary Soft Teal.
- **Navigation Bar:** Use the M3 Navigation Bar (Bottom Navigation) with a transparent background and a Sage Green tonal indicator for the active state.
- **Floating Action Button (FAB):** Large, rounded-square (28dp) using the Primary Soft Teal to signify the most important "Start Session" action.