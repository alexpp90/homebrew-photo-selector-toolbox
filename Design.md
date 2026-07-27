# Design.md — Photo Selector Toolbox, Android Desktop (Tablet / DeX)

Complete design instruction set for a visual redesign of the **Android Desktop** solution
(`android/app/`, package `com.photoselectortoolbox`). Self-contained: everything a design
tool or a human designer needs, in one file.

Derived from `REQUIREMENTS.md` §7, `ANDROID_DESIGN.md`, and the current Compose sources
under `android/app/src/main/java/com/photoselectortoolbox/ui/`.

**Scope note:** this file covers the Android Desktop solution only. The Desktop (Python /
Tkinter) app and the Android Phone client (PhotoTok, `android/phototok/`) are independent
solutions with their own UX models and their own themes — do not apply these instructions
to them.

---

## Table of contents

1. [Product context](#1-product-context)
2. [Target devices](#2-target-devices)
3. [Design principles](#3-design-principles)
4. [Visual language](#4-visual-language)
5. [Information architecture](#5-information-architecture)
6. [Data the UI displays](#6-data-the-ui-displays)
7. [Screen: Selector — three-column comparison (hero)](#7-screen-selector--three-column-comparison-hero)
8. [Screen: Selector — focused layout (default)](#8-screen-selector--focused-layout-default)
9. [Screen: Fullscreen viewer](#9-screen-fullscreen-viewer)
10. [Surface: Scan configuration](#10-surface-scan-configuration)
11. [Surface: Score legend](#11-surface-score-legend)
12. [Screen: Statistics](#12-screen-statistics)
13. [Screen: Duplicate finder](#13-screen-duplicate-finder)
14. [Screen: Settings](#14-screen-settings)
15. [Breakpoint adaptations](#15-breakpoint-adaptations)
16. [Component sheet](#16-component-sheet)
17. [Accessibility](#17-accessibility)
18. [Keyboard and pointer](#18-keyboard-and-pointer)
19. [Motion](#19-motion)
20. [Using this file with a design tool](#20-using-this-file-with-a-design-tool)

---

## 1. Product context

**One sentence.** A photographer dumps a shoot folder onto the device, the app analyses
every image for technical quality — sharpness, noise, highlight/shadow clipping, and an
optional on-device AI aesthetic score — and the photographer rapidly compares neighbouring
frames side by side to decide which to keep, copy, move, or delete.

**Who uses it.** Enthusiast and professional photographers reviewing 200–2000 frames from a
single shoot, mostly bursts of near-identical images. Seated, holding a large tablet in
landscape, or at a desk in Samsung DeX with keyboard and mouse.

**The core loop.** Look at three consecutive frames → decide which is sharpest / best →
act → advance. This repeats hundreds of times per session. Every millimetre of image area
and every avoided tap compounds across the session. Optimise for the loop, not for the
first impression.

**What this product is not.** Not a photo editor. Not a gallery. Not a consumer social
app. No filters, no stickers, no reactions, no decorative colour. The nearest reference
points are Lightroom's Library module and Photo Mechanic — not Google Photos.

---

## 2. Target devices

| Priority | Device / mode | Logical size | Notes |
|---|---|---|---|
| Primary | Samsung Galaxy Tab S11 Ultra, landscape | ~1480 × 924 dp (2960 × 1848 px @2x), 16:10 | The reference frame. Design here first. |
| Primary | Samsung DeX / Chromebook window | default 1024 × 768 dp, min 800 × 600 dp | Resizable. Hardware keyboard + mouse present. |
| Secondary | Tablet portrait | 600–840 dp wide | Medium breakpoint. |
| Tertiary | Phone | < 600 dp wide | Compact breakpoint, reduced feature set. |

**The content is 4:3 photographs.** Almost every image is 4:3, landscape or portrait
orientation. The devices are 16:10. This mismatch is the single most important layout fact
in the product: a 4:3 image fitted to a 16:10 frame always leaves empty side columns.
Those columns are where chrome belongs.

---

## 3. Design principles

Non-negotiable. Every layout decision is checked against these.

1. **The photograph is the interface.** Maximise the pixel area given to images. Chrome
   lives in the letterbox dead space the aspect-ratio mismatch creates. Chrome must never
   steal vertical space from the photograph.
2. **No overlays on images.** No floating action buttons, no scrims, no gradients, no
   controls on top of a photo. The single exception is the one-time first-run navigation
   hint. Two interactive controls may never share screen bounds.
3. **One control, one home.** Each control belongs to exactly one control group. Nothing
   free-floats between layouts. The layout toggle in particular appears exactly once, inside
   the control group of the layout currently shown.
4. **Touch first, keyboard fast.** Every interactive target ≥ 48 dp. Every frequent action
   also has a keyboard shortcut, surfaced as a subtle key hint on hover.
5. **Dark, quiet, neutral.** The UI must not colour-cast the photographs. Greys plus a
   single indigo accent. No gradients beside images, no coloured glows, no translucent blur
   panels over image areas.
6. **Numbers must be readable.** A quality score is never a bare number: it is icon + short
   label + formatted value, with its direction encoded visually and its legend one tap away.
7. **Density without clutter.** This is a professional tool. Prefer compact type and tight
   spacing over generous consumer padding — while keeping touch targets full size.

**Creative latitude.** Layout composition, hierarchy, information density, empty states and
visual polish are open. The dark Zinc/Indigo palette, principles 1–3, and the four-destination
navigation model are fixed. The three-image comparison view is the heart of the product and
deserves the most design effort.

---

## 4. Visual language

### 4.1 Colour

Dark theme only. Material 3 `darkColorScheme`. Zinc greys plus an Indigo accent.

| Role | Hex | Name | Use |
|---|---|---|---|
| `background` / `surface` | `#18181B` | Zinc-900 | Base canvas |
| `surfaceVariant` | `#27272A` | Zinc-800 | Cards, panels, rails, chips |
| `outline` | `#3F3F46` | Zinc-700 | Borders, dividers, chart grid lines |
| `onSurface` / `onBackground` | `#FAFAFA` | Zinc-50 | Primary text |
| `onSurfaceVariant` | `#A1A1AA` | Zinc-400 | Secondary text, icons at rest |
| `primary` | `#6366F1` | Indigo-500 | Accent, active nav, current-image border |
| `onPrimary` | `#FFFFFF` | — | Text on accent |
| `primaryContainer` | `#4F46E5` | Indigo-600 | Pressed / hovered accent |
| `secondary` | `#A1A1AA` | Zinc-400 | Muted controls |

Semantic accents — used **only** for score direction and destructive actions, never
decoratively:

| Role | Hex | Name |
|---|---|---|
| Good / pass | `#34D399` | Emerald-400 |
| Warning | `#FBBF24` | Amber-400 |
| Bad / destructive | `#F87171` | Red-400 |

### 4.2 Type

Roboto or Inter, Material 3 type scale.

| Element | Style | Size |
|---|---|---|
| Filenames | bodyMedium, middle-ellipsis, one line | 14 sp |
| EXIF lines, score chips | labelSmall–labelMedium | 11–13 sp |
| Column headers ("Previous"/"Current"/"Next") | labelSmall, uppercase tracking | 11 sp |
| Card and section titles | titleSmall / labelLarge | 14–16 sp |

Numeric values use **tabular figures** so columns of scores align vertically across the
three comparison columns. Decimal separator is always a period (`Locale.US`) — never
locale-dependent, or the same score renders differently on different devices.

### 4.3 Shape, elevation, spacing

- Corner radius: 8 dp on cards and chips, 12 dp on sheets and dialogs, **4 dp or 0 dp on
  images**. Rounded photos read as consumer styling — never exceed 4 dp.
- **No drop shadows.** Separate surfaces with a 1 dp `#3F3F46` outline plus the
  `#18181B` → `#27272A` value step. Dark themes read better with borders than shadows.
- Spacing scale: 4 / 6 / 8 / 12 / 16 / 24 dp. In the comparison layouts, outer padding is
  ≤ 8 dp and column gaps are 8 dp — space is not decoration here.
- All screens are edge-to-edge and must respect `WindowInsets` for system bars.

### 4.4 Iconography

Material Symbols, Rounded, weight 400.

| Concept | Icon |
|---|---|
| Selector | `photo_camera` |
| Statistics | `bar_chart` |
| Duplicates | `content_copy` |
| Settings | `settings` |
| Move to Selection | `drive_file_move` |
| Copy to Selection | `content_copy` |
| Delete | `delete` |
| Fullscreen | `fullscreen` |
| Photo details | `info` |
| Filmstrip | `view_carousel` |
| Layout toggle | `view_column` / `view_agenda` |
| Sharpness | `center_focus_strong` |
| Noise | `grain` |
| Highlight clipping | `brightness_high` |
| Shadow clipping | `brightness_low` |
| Aesthetic score | `auto_awesome` |

---

## 5. Information architecture

Four top-level destinations, reached from a persistent **NavigationRail** on the left edge
(Expanded and Medium) or a **BottomNavigation** bar (Compact).

| # | Destination | Purpose | Share of session |
|---|---|---|---|
| 1 | **Selector** | The culling workspace. Default destination. | ~95% |
| 2 | **Statistics** | EXIF distribution charts for the loaded folder. | small |
| 3 | **Duplicates** | Byte-identical file finder (SHA-256) with batch delete. | small |
| 4 | **Settings** | Storage, analysis, display, cache, about. | rare |

### Session-level actions

Open Folder (system folder picker) · Open from Google Drive · Scan Images · Cancel Scan ·
Clear Scores · Group Similar Series · Score legend.

### Per-image actions

Move to Selection · Copy to Selection · Delete · Open Fullscreen.

"Selection" is a destination subfolder — default name `Selection`, configurable — created
inside the source folder. Deletion goes to the Android trash and is undoable via a
30-second Snackbar.

### Persistent state the design must reflect

- The chosen comparison layout is remembered across launches. Default is **focused**.
- Filmstrip visible/hidden is a user toggle, remembered.
- Photo-details panel visible/hidden is a user toggle, remembered.
- The first-run navigation hint shows exactly once, then never again.

---

## 6. Data the UI displays

### 6.1 Image item

Filename · thumbnail · full-resolution image · orientation (4:3 landscape or portrait) ·
position in folder (`127 / 842`) · selection state.

### 6.2 EXIF data

Loaded asynchronously for the current image and its two neighbours; the UI updates
reactively as each arrives.

| Field | Rendered as |
|---|---|
| Shutter speed | `1/250s` (sub-second) or `2s` |
| Aperture | `f/2.8` |
| Focal length | `35mm` |
| 35mm equivalent | `52mm` |
| ISO | `ISO 400` |
| Lens | free text |

Any field may be `Unknown`. A fallback flag exists when full EXIF could not be read — show
it as a subtle "limited metadata" marker, not an error.

Compact one-line form used in comparison columns:
`1/250s · f/2.8 · 35mm · ISO 400`

### 6.3 Quality scores

Produced by a scan. Any subset may be absent — an image may have no scores at all.

| Chip label | Full name | Format | Direction | Description (shown in legend) |
|---|---|---|---|---|
| `Sharp` | Sharpness | `%.1f` | higher is better | Edge contrast in the sharpest part of the frame. Low values usually mean motion blur or a missed focus. |
| `Noise` | Noise | `%.1f` | lower is better | Estimated sensor noise in flat areas of the image. Rises with high ISO. |
| `Highl` | Highlight clipping | `%.1f%%` | lower is better | Share of pixels blown out to pure white — detail that cannot be recovered. |
| `Shad` | Shadow clipping | `%.1f%%` | lower is better | Share of pixels crushed to pure black — detail that cannot be recovered. |
| `Aesth` | Aesthetic score | `%.1f` | higher is better | On-device AI rating of overall appeal, on a 1–10 scale. |

**Rendering rule.** A score chip is **icon + short label + value**. The label may be dropped
only on thumbnail overlays where space genuinely does not exist — and there the accessibility
label must still carry the full name, value and direction.

**Direction indicator.** Each chip carries a 2 dp bar beneath it that fills toward "good",
tinted emerald → amber → red. This lets the user compare three frames at a glance without
reading digits, and satisfies the requirement that direction not be conveyed by colour alone.

---

## 7. Screen: Selector — three-column comparison (hero)

**Frame:** 1480 × 924 dp landscape tablet. This is the most important screen in the product.

### Goal

Show three consecutive photographs — Previous, Current, Next — simultaneously, each 4:3,
each rendered as large as the display physically allows, so the photographer can judge which
of three near-identical frames is the keeper.

### The layout maths — this drives everything

```
usable width   = 1480 − nav rail − outer padding − (2 × column gap)
image width    = usable width / 3          ≈ 450–470 dp
image height   = image width × 3/4         ≈ 340–350 dp
usable height  ≈ 850 dp after system insets and app bar
```

Three 4:3 images in a row are **width-constrained, not height-constrained**. Horizontal
pixels are the scarce resource; vertical pixels are abundant. Therefore:

- **Strip horizontal chrome to the bone.** The navigation rail collapses to its narrowest
  form (72–80 dp) or hides entirely behind a single menu affordance. Outer horizontal
  padding ≤ 8 dp. Column gaps 8 dp. **No side panel, metadata rail, or inspector column
  beside the three images** — every dp of width taken is a dp taken off all three photos.
- **Spend the leftover vertical space generously.** Each image's filename, EXIF line and
  score chips go in a caption block directly **below its own image**, not in a shared side
  panel. The action row sits below that.
- **All three images are exactly the same size.** Previous, Current and Next must be visually
  identical in dimensions — the entire point is a fair comparison. Never enlarge the current
  image relative to its neighbours in this layout.

### Column anatomy — identical for all three columns

1. **Column header** — `Previous` / `Current` / `Next`, labelSmall, uppercase tracking,
   Zinc-400. The Current column's header is Indigo-500.
2. **The image** — 4:3, letterboxed inside a Zinc-800 tile with a 1 dp Zinc-700 border.
   The Current column's tile gets a 2 dp Indigo-500 border. **That border is the only thing
   marking which column is active.** No scrim, no glow, and no dimming of the neighbours —
   the neighbours must render at full brightness or the comparison is worthless.
3. **Filename** — bodyMedium, middle-ellipsis, one line.
4. **Compact EXIF line** — labelSmall, Zinc-400, dot-separated.
5. **Score chip row** — up to five chips, wrapping to a second line if needed. Each chip is
   a Zinc-800 pill with a 1 dp Zinc-700 border, carrying icon + short label + value + the
   direction bar.
6. **Portrait-orientation images** — keep the tile footprint identical and pillarbox the
   image inside it. The grid must never reflow when orientation changes mid-sequence.

### Interaction

- Tapping the Previous or Next **column** advances the whole triptych by one frame. The
  neighbour columns *are* the navigation — there are no arrow buttons over the images.
  Show a hand cursor on hover (mouse / DeX).
- Long-press any image → context menu: Move, Copy, Delete, Info, Fullscreen.
- No hover-dependent interactions — everything must be reachable by touch.

### Action row — one shared row below the three columns, centred

```
[Move to Selection] [Copy to Selection] [Delete]  |  [Fullscreen]
      ─────────────── divider ───────────────
[Layout toggle → focused] [Details] [Filmstrip]
```

- Acts on the **Current** image.
- Move and Copy are filled-tonal buttons; Delete is outlined with the red accent reserved
  for its icon; the view controls are icon toggle buttons with distinct on/off states.
- All controls ≥ 48 dp.
- **The layout toggle lives here in this layout, and nowhere else.**
- Keyboard hints (`M`, `C`, `Del`, `F`) render as small Zinc-400 key-caps on hover.

### Top app bar — compact, ≤ 56 dp

- Left: folder name, then the position counter `127 / 842`.
- Right: `Scan Images` · `Group Similar Series` · score legend (info icon, shown once any
  image has scores) · overflow.
- **Scan in progress:** replace the bar's lower edge with a 2 dp Indigo progress line plus
  an inline `Scanning 412 / 842 · Cancel`.

### Filmstrip — collapsible, bottom edge, ~72 dp tall when shown

Horizontally scrolling row of thumbnails for the whole folder. The current item is outlined
in Indigo. Each thumbnail may carry one tiny score dot; no text. Visibility is remembered.

### Do not

Add a side inspector panel · shrink or dim the neighbour images · overlay arrows, buttons or
gradients on the photographs · use drop shadows · round image corners beyond 4 dp.

---

## 8. Screen: Selector — focused layout (default)

**Frame:** 1480 × 924 dp landscape tablet. This is the **default** comparison layout; the
three-column layout is the alternative, reached via the layout toggle.

### Goal

One large current image with its two neighbours as smaller reference tiles beneath it — for
when the user wants detail on one frame rather than an even three-way comparison.

### The layout maths

A 4:3 image fitted into a full-width 16:10 region is **height-limited** and leaves a wide
empty column on each side. Put the controls in those dead columns:

- **Left vertical rail, 56 dp wide:** Previous, Next, and the `127 / 842` position counter,
  stacked vertically.
- **Right vertical rail, 56 dp wide:** Move, Copy, Delete, Fullscreen, then a divider, then
  Layout toggle, Details toggle, Filmstrip toggle.

Both rails are flush with the image region's vertical extent. They consume **zero vertical
space** and never cover the photo. Outer padding ≤ 6 dp.

### Vertical budget

| Region | Share |
|---|---|
| Current image | ~72% of free height (~80% on a Compact-height window) |
| Previous / Next tiles | remaining height, side by side, each half-width, each 4:3 |
| Filmstrip | below that, if shown |

### Current image block

4:3 image in a Zinc-800 tile, 1 dp Zinc-700 border, 4 dp radius. Directly beneath it — or
in a collapsible details panel that docks **below** the image, never on top of it —
filename, full EXIF line, and the score chip row with direction bars.

### First-run navigation hint (the only permitted image overlay)

On the very first launch only, show translucent left/right chevron affordances on the image
edges with a one-line caption: *"Tap either side to browse."* Persist a seen-flag and never
show them again. Navigation thereafter relies on tapping the Previous/Next tiles, the rails,
hardware keys, and drag gestures.

### Empty and intermediate states

| State | Design |
|---|---|
| No folder | Centred card: camera icon, **"Select a Folder"**, *"Select a folder to start reviewing and culling your photos."*, primary `[Open Folder]`, secondary `[Open from Google Drive]`. |
| Folder open, unscanned | Images visible; score chip rows replaced by a single ghost chip reading **"Not scanned"**; `Scan Images` in the app bar visually emphasised. |
| Scanning | Inline progress in the app bar; scores populate progressively per image, no full-screen blocker. |
| Folder empty | Centred card: **"No Photos Loaded"** with `[Open Folder]`. |

### Placement rule

**The layout toggle lives in the right rail here, and nowhere else.** It must never render
as a free-floating overlay on top of the other layout's control cluster.

---

## 9. Screen: Fullscreen viewer

**Frame:** 1480 × 924 dp landscape tablet.

Pure black or Zinc-900 canvas, image fitted to the window. Chrome auto-hides after 3 seconds
of inactivity and returns on tap or pointer movement.

**When chrome is visible:**

- **Top bar** — close (`X`), filename, `127 / 842`, and a single-line EXIF summary
  (`1/250s | f/2.8 | 35mm | ISO 400`). Render as a minimal Zinc-900 bar at ~90% opacity —
  a bar, not a gradient scrim.
- **Bottom** — only when *Show Fullscreen Action Buttons* is enabled in Settings:
  `[Move to Selection] [Copy to Selection] [Delete]`, ≥ 48 dp, centred.
- **Score chip row** — bottom-left, compact variant.

**Gestures**, communicated through a dismissible one-time hint card:

| Gesture | Action |
|---|---|
| Pinch | Zoom in / out |
| Double-tap | Toggle fit ↔ 100% |
| Horizontal swipe | Previous / next image |
| Swipe down | Dismiss |
| `Esc` | Exit fullscreen |
| `← / →` | Previous / next image |

When zoomed beyond fit, show a small navigator thumbnail in the bottom-right corner
indicating which part of the frame is visible.

---

## 10. Surface: Scan configuration

Rendered as a **right-side sheet, 400 dp wide** on tablet and DeX — not a centred modal, so
the user can still see the photos. On Compact, a bottom sheet. Zinc-800 surface, 12 dp radius.

**Title:** `Scan Configuration`

**Section — Analysis Types.** Material 3 switches, each with a name and a one-line
plain-language explanation:

| Switch | Explanation |
|---|---|
| Sharpness | Detect focus quality via Laplacian variance |
| Noise Level | Estimate sensor noise via median absolute deviation |
| Highlight Clipping | Detect overexposed (blown-out) areas |
| Shadow Clipping | Detect underexposed (crushed black) areas |
| AI Aesthetic Score (beta) | On-device aesthetic rating; runs only on sharp images |

The aesthetic score carries a `BETA` pill and a small battery-impact note. It is opt-in and
gated behind the cheap sharpness metric.

**Estimated duration line**, updating live with the selected options:
*"842 images · about 3 min · 612 already cached and will be skipped."*

**Footer:** `[Cancel]` (text) and `[Start Scan]` (filled Indigo).

---

## 11. Surface: Score legend

Opened from the info icon in the Selector app bar — shown once any image has scores.

**Title:** `What the scan icons mean`

One row per metric: the actual chip as it appears in the UI, the full display name, a
direction badge, and the plain-language description from [§6.3](#63-quality-scores).

Close with a small worked example — three chips side by side with their direction bars,
captioned *"Compare the bars, not the numbers."*

---

## 12. Screen: Statistics

**Frame:** 1480 × 924 dp landscape tablet. EXIF distribution charts for the loaded folder.

**Header summary strip** — four stat tiles:

| Tile | Example value |
|---|---|
| Images | `842` |
| Lenses | `4` |
| Focal Range | `24mm - 200mm` |
| (one more, your choice, derived from the data below) | — |

**Chart cards** — responsive grid, 2–3 per row on Expanded, 1 per row on Compact:

- Focal Length Distribution (histogram)
- Aperture Distribution (histogram)
- ISO Distribution (histogram)
- Shutter Speed Distribution (histogram)
- Lens Usage (horizontal bar / ranked list)

**Chart styling:** Indigo-500 bars, Zinc-400 axis labels, Zinc-700 grid lines, Zinc-800 card
background, 1 dp Zinc-700 border, 8 dp radius, title in titleSmall. Omit legends where a
single series makes them redundant.

**Empty state:** **"No Folder Selected"** / *"Select a folder containing photos to view EXIF
statistics."* / `[Select Folder]`.

---

## 13. Screen: Duplicate finder

**Frame:** 1480 × 924 dp landscape tablet. Finds byte-identical files by SHA-256 hash and
lets the user batch-delete copies.

**States:**

| State | Design |
|---|---|
| No folder | **"No Folder Selected"** / *"Select a folder to scan for duplicate images."* / `[Select Folder]` |
| Ready | **"Ready to Scan"**, folder name, `[Start Scan]` |
| Scanning | Determinate progress with file counter and `[Cancel]` |
| Results | See below |

**Results** — a vertical list of duplicate **groups**, each a card:

- **Header:** `Group (3 files)` plus a `Select All But First` text action.
- **Body:** a horizontal row of thumbnails, one per file. The first file carries an
  `Original` badge with an Indigo outline. Files marked for deletion get a red-tinted border,
  a checked checkbox, and a *"Selected for deletion"* caption. Below each thumbnail: filename,
  file size, and the folder path, ellipsised from the **left** so the leaf folder stays visible.

**Persistent bottom action bar**, shown whenever anything is selected:

```
12 selected  ....................................  [Delete Selected (12)]
```

**Confirmation dialog:** **"Delete Selected Files"** / *"Delete 12 file(s)? This action
cannot be undone."* / `[Cancel]` `[Delete]`.

---

## 14. Screen: Settings

**Frame:** 1480 × 924 dp landscape tablet. Two-pane on Expanded — section list on the left
(280 dp), section content on the right. Single scrolling list on Compact.

Labels below are final — use them verbatim.

### Storage

| Item | Control | Supporting text |
|---|---|---|
| Selection Subfolder Name | editable text, value `Selection` | Created inside the source folder by default |
| Custom Selection Location | folder picker | `Default: subfolder in source folder` → `Using custom folder` when set |
| File Sorting | switch | Sort RAW and JPEG files into separate subfolders |

### Analysis

| Item | Control | Supporting text |
|---|---|---|
| Analysis Threads | slider 1–4, value shown numerically | — |
| Group Similar Series | switch | Group photos shot in close succession |
| Grouping Level | segmented control, 3 options | see below |

Grouping Level options:

- **Time & Filename** — Group by capture time and filename similarity
- **Time + Fast Similarity** — Group by time with fast perceptual hash comparison
- **Detailed Similarity** — Full similarity analysis (slower, more accurate)

### Fullscreen Viewer

| Item | Control | Supporting text |
|---|---|---|
| Double-Tap Gesture Action | choice: `Move to Selection` / `Copy to Selection` | — |
| Show Fullscreen Action Buttons | switch | Display delete, copy, and move buttons in fullscreen mode |

### Cache

| Item | Control | Supporting text |
|---|---|---|
| Cached Scores | read-only | `1,204 analysis results stored` |
| Clear Cache | destructive action + confirm dialog | Remove all cached analysis scores |

### About

Photo Selector Toolbox · `Version 1.4.2` · Source Code — *View on GitHub*.

### Style

Zinc-800 grouped cards on a Zinc-900 canvas, 1 dp Zinc-700 borders. Section headers in
labelLarge Indigo-500; item titles bodyLarge Zinc-50; supporting text bodySmall Zinc-400;
controls right-aligned. Destructive items use the red accent for their icon and label only —
never a red-filled button.

---

## 15. Breakpoint adaptations

### 15.1 Medium — 600–840 dp, tablet portrait

Reference frame: 720 × 1100 dp portrait.

- NavigationRail persists, icon-only, 72 dp.
- Defaults to the **focused** layout. The three-column layout remains available via the
  toggle; at this width each image is ~200 dp wide — render it honestly with the same
  equal-thirds rule rather than blocking it.
- The 56 dp side rails still apply. Below ~700 dp width, collapse the left rail's controls
  into the right rail and rely on tapping the Previous/Next tiles for navigation.
- Metadata and score chips collapse below the current image; the details panel becomes a
  collapsible expander rather than an always-visible block.
- Filmstrip remains a horizontal scrolling row.

### 15.2 Compact — < 600 dp, phone

Reference frame: 400 × 880 dp portrait.

- **BottomNavigation** replaces the NavigationRail: Selector, Statistics, Duplicates, Settings.
- Single full-width swipeable image viewer (horizontal pager). **The comparison layouts are
  omitted at this width.**
- Below the image: single-line filename, then a horizontally scrollable score chip row
  (compact chips — icon + value, labels dropped).
- Detailed EXIF sits behind a tap-to-expand row, not always visible.
- Two always-visible quick actions: `[Select]` and `[Delete]`, full-width, ≥ 48 dp.
- Statistics: single-column scrollable charts.
- Duplicates: compact grid with checkboxes instead of the detailed group rows.

---

## 16. Component sheet

Produce these as a design-system page, dark theme, Zinc/Indigo.

1. **ScoreChip** — five variants (Sharp, Noise, Highl, Shad, Aesth) × three states (good,
   neutral, poor) × two sizes (full = icon + label + value; compact = icon + value for
   thumbnail overlays). Show the direction-bar treatment in every variant.
2. **ScoreChipRow** — wrapping row of up to five chips at column width ~450 dp.
3. **ImageTile** — 4:3 tile with letterbox and pillarbox behaviour. States: resting,
   current/active (2 dp Indigo border), hovered (Zinc-600 border), loading (Zinc-800 with a
   subtle shimmer — no spinner).
4. **Vertical control rail** — 56 dp wide, 48 dp icon buttons with 4 dp gaps, divider
   treatment, and a disabled state (`No Previous` / `No Next`).
5. **Action row buttons** — filled-tonal (Move, Copy), outlined destructive (Delete), icon
   toggle (layout, details, filmstrip) in on and off states, each with its keyboard-hint
   key-cap.
6. **Top app bar** — resting, scan-in-progress (inline progress + Cancel), and
   selection-count variants.
7. **Filmstrip thumbnail** — resting, current, and marked states, 64 dp tall.
8. **Snackbar with Undo** — `Moved to Selection` / `Copied to Selection` / `1 image deleted`,
   each with a 30 s Undo action and a thin countdown line.
9. **Empty-state card** — icon, title, one-line body, primary + secondary button.
10. **Confirmation dialog** — standard and destructive variants.
11. **Metadata panel** — labelled key/value rows for Shutter Speed, Aperture, Focal Length,
    35mm Equiv., ISO, Lens; the `Unknown` treatment for missing fields; a "limited metadata"
    marker for fallback reads.
12. **Keyboard shortcut hint** — the small key-cap style used on hover.
13. **Context menu** — long-press menu with Move, Copy, Delete, Info, Fullscreen.

---

## 17. Accessibility

- **Contrast.** All text ≥ 4.5:1 against its surface. Zinc-400 (`#A1A1AA`) on Zinc-900
  passes; Zinc-500 does not. Do not go darker than `#A1A1AA` for any body text.
- **Direction is not colour-only.** Score direction is conveyed by bar length *and* the
  written direction hint, not by the emerald/amber/red tint alone.
- **Icon-only controls** need a visible tooltip on hover and a full accessibility label
  carrying name, value and direction — e.g. *"Sharpness 512.3, higher is better."*
- **Focus rings.** 2 dp Indigo-500 with a 2 dp offset, visible on every interactive element,
  for keyboard and DeX navigation.
- **Touch targets.** Minimum 48 dp for every interactive element, everywhere, at every
  breakpoint.
- **No hover-dependent interactions.** Hover may reveal affordances (cursors, key hints,
  tooltips) but must never be the only route to a function.

---

## 18. Keyboard and pointer

Active whenever a hardware keyboard is present — DeX, Chromebook, tablet with keyboard case.
Shortcuts work in both the standard and fullscreen views.

| Key | Action |
|---|---|
| `←` / `→` | Previous / next image |
| `Delete` / `Backspace` | Delete (with confirmation) |
| `M` | Move to Selection |
| `C` | Copy to Selection |
| `F` | Enter fullscreen |
| `Esc` | Exit fullscreen / comparison mode |
| `Space` | Toggle comparison layout |

**Pointer.** Interactive components show the hand cursor (`PointerIcon.Hand`) on hover —
buttons, clickable images, filmstrip thumbnails. Dragging a folder from an external file
manager into the app window loads it; design a drop-target state (Indigo dashed outline over
the content area, caption *"Drop a folder to load it"*).

---

## 19. Motion

- Duration 150–200 ms, standard easing.
- **Image transitions crossfade only.** No slide, no scale, no parallax. The user is judging
  sharpness — movement lies about it.
- Panel and rail toggles: fade plus a small size change, never a bounce.
- Progress is always determinate where a total is known.
- Respect the system reduced-motion setting: fall back to instant swaps.

---

## 20. Using this file with a design tool

**Google Stitch.** Paste sections 1–6 once to set context, then paste one screen section
(7–15) per generation. Stitch works best with a single screen plus explicit device framing —
each screen section opens with its frame size.

**Claude Design or any conversational tool.** Paste sections 1–6 as the first message, then
work through screens 7–15 in order, starting with §7 (the hero) and iterating on it before
moving on.

**Human designer.** Read start to finish; §7 and §8 are where the product lives.

### Iteration prompts

Short corrections to steer a generated design back on track.

| Problem in output | Correction |
|---|---|
| Controls floating on the photo | "Remove every control that sits on top of an image. Move them into the 56 dp side rails or the action row below." |
| Current image enlarged in three-column view | "All three images must be identical in size. The active image is marked only by a 2 dp Indigo border." |
| Neighbours dimmed or scrimmed | "Render Previous and Next at full brightness. Dimming defeats the comparison." |
| Images too small in three-column view | "Reduce horizontal chrome: collapse the nav rail, drop outer padding to 8 dp, remove any side panel. Each image should be roughly one third of the full window width." |
| Consumer-app styling | "This is a professional tool, closer to Lightroom's Library module than Google Photos. Remove gradients, large radii, drop shadows, and decorative colour." |
| Bare numeric scores | "Every score is icon + short label + value, with a direction bar. Never a bare number." |
| Light theme or coloured background | "Dark theme only. Canvas #18181B, panels #27272A. Nothing may colour-cast the photographs." |
| Layout toggle in two places | "The layout toggle appears exactly once, inside the current layout's own control group — the right rail in focused view, the action row in three-column view." |
| Vertical space wasted in three-column view | "Vertical space is abundant here. Put each image's filename, EXIF line and score chips directly below its own image." |

---

## Related documents

- [`REQUIREMENTS.md`](REQUIREMENTS.md) §7 — Android application requirements (authoritative)
- [`ANDROID_DESIGN.md`](ANDROID_DESIGN.md) — Android architecture and layout strategy
- [`docs/design/ANDROID_DESKTOP_DESIGN_PROMPT.md`](docs/design/ANDROID_DESKTOP_DESIGN_PROMPT.md) — the same material packaged as copy-paste prompt blocks
- [`AGENTS.md`](AGENTS.md) — agent roster and delegation rules

> **Keeping this in sync:** if a design decision here changes app behaviour, update
> `REQUIREMENTS.md` §7 in the same change — it remains the single source of truth.
