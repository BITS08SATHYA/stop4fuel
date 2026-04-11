# StopForFuel — Brand Kit v1.0

## What's Inside

```
stopforfuel-brand/
│
├── icons/
│   ├── svg/                          ← Vector logos (scalable to any size)
│   │   ├── logo-full-dark.svg        ← Full logo for dark backgrounds ★ PRIMARY
│   │   ├── logo-full-light.svg       ← Full logo for light backgrounds
│   │   ├── logo-full-amber.svg       ← Full logo for amber/brand backgrounds
│   │   ├── icon-shield-dark.svg      ← Shield only, dark bg
│   │   ├── icon-shield-light.svg     ← Shield only, light bg
│   │   ├── icon-shield-mono-white.svg← White silhouette (printing on colors)
│   │   └── icon-shield-mono-dark.svg ← Dark silhouette (printing on white)
│   │
│   ├── android/                      ← Android adaptive icon layers
│   │   ├── ic_launcher_foreground.svg      ← Foreground (dark theme)
│   │   ├── ic_launcher_foreground_light.svg← Foreground (light theme)
│   │   ├── ic_launcher_background.svg      ← Background layer (solid #0D1117)
│   │   └── ic_notification.svg             ← Status bar icon (monochrome)
│   │
│   └── web/                          ← Web favicons & PWA icons
│       ├── favicon.svg               ← Browser tab icon
│       ├── apple-touch-icon.svg      ← iOS home screen (180×180)
│       ├── icon-192.svg              ← PWA manifest icon
│       └── icon-512.svg              ← PWA splash / store listing
│
├── css/
│   ├── stopforfuel-tokens.css        ← All CSS variables (colors, fonts, spacing)
│   ├── tailwind-theme.css            ← Tailwind v4 @theme + v3 JS config
│   └── SffColors.kt                  ← Kotlin color constants (Jetpack Compose)
│
└── docs/
    ├── head-setup.html               ← HTML <head> snippet for favicons + fonts
    └── BRAND-KIT-README.md           ← This file
```

---

## Quick Start

### 1. Web (Next.js Frontend)

**Icons** — Copy `icons/web/*` into your `/public` folder:
```
frontend/public/
├── favicon.svg
├── apple-touch-icon.svg    ← Convert to .png (see below)
├── icon-192.png            ← Convert from icon-192.svg
├── icon-512.png            ← Convert from icon-512.svg
└── site.webmanifest        ← See docs/head-setup.html
```

**Fonts + Favicon HTML** — Copy the `<link>` tags from `docs/head-setup.html` into your `app/layout.tsx` `<head>`.

**Colors & Tokens** — Either:
- Import `css/stopforfuel-tokens.css` in your `globals.css`
- OR paste the `@theme` block from `css/tailwind-theme.css` into your Tailwind v4 CSS

Then use:
```css
background: var(--sff-deep-900);
color: var(--sff-amber-600);
```
or Tailwind classes:
```html
<div class="bg-sff-deep-900 text-sff-amber-600">
```

**Logo in header** — Use `icons/svg/logo-full-dark.svg` or `logo-full-light.svg`:
```tsx
import LogoDark from '@/public/logo-full-dark.svg';
// or
<img src="/logo-full-dark.svg" alt="StopForFuel" height={36} />
```

### 2. Android (Kotlin App)

**Adaptive Icon:**
1. Open Android Studio → right-click `res` → New → Image Asset
2. Choose "Adaptive and Legacy"
3. Foreground: import `icons/android/ic_launcher_foreground.svg`
4. Background: set color `#0D1117` (or import the background SVG)
5. Android Studio auto-generates all density variants (mdpi → xxxhdpi)

**Notification Icon:**
1. New → Image Asset → "Notification"
2. Import `icons/android/ic_notification.svg`

**Colors:**
Copy `css/SffColors.kt` into your `ui/theme/` package. Use:
```kotlin
Text(
    text = "₹4,82,350",
    color = SffColors.Amber600
)
```

**Fonts:**
1. Download TTF files from Google Fonts (links in SffColors.kt)
2. Place in `res/font/`
3. Uncomment the FontFamily blocks in SffColors.kt

### 3. Converting SVG → PNG

The SVG files are vector — they scale infinitely. But some places need PNGs.

**Option A — Command line (requires Inkscape):**
```bash
# Favicon 32px
inkscape favicon.svg -w 32 -h 32 -o favicon-32x32.png

# Apple touch icon
inkscape apple-touch-icon.svg -w 180 -h 180 -o apple-touch-icon.png

# PWA icons
inkscape icon-192.svg -w 192 -h 192 -o icon-192.png
inkscape icon-512.svg -w 512 -h 512 -o icon-512.png
```

**Option B — Online:**
Upload SVGs to https://cloudconvert.com/svg-to-png and set the target dimensions.

**Option C — Figma:**
Import SVGs → export as PNG at 1x, 2x, 3x.

---

## Color Reference (Quick Copy)

| Token          | Hex       | Usage                                  |
|----------------|-----------|----------------------------------------|
| Amber 600      | `#FFB300` | Primary brand, CTAs, active nav        |
| Amber 800      | `#E65100` | Gradient end, hover states             |
| Deep 900       | `#0D1117` | Dark mode background                   |
| Deep 700       | `#1C2333` | Dark mode cards/surfaces               |
| Teal 500       | `#14B8A6` | Secondary actions, success, links      |
| Success        | `#22C55E` | Shift closed, payment cleared          |
| Danger         | `#EF4444` | Discrepancies, overdue, delete         |
| Gray 50        | `#F8FAFC` | Light mode background                  |

## Font Reference

| Role     | Font              | Weights      | Used For                          |
|----------|-------------------|--------------|-----------------------------------|
| Display  | Outfit            | 600–800      | Logo, headings, KPI numbers       |
| Body     | Plus Jakarta Sans | 400–700      | Text, labels, nav, table data     |
| Mono     | JetBrains Mono    | 400–500      | Invoice IDs, shift codes, amounts |

---

## Brand Rules (TL;DR)

✓ Shield mark on dark backgrounds = maximum contrast
✓ Minimum clear space around logo = 1× shield width
✓ "StopForFuel" is one word, capital S-F-F
✓ Amber gradient always flows top (light) → bottom (dark)

✗ Never rotate, skew, or stretch the shield
✗ Never put amber shield on yellow/orange backgrounds
✗ Never add shadows, glows, or 3D effects to the logo
✗ Never use below 16px — switch to simplified favicon
