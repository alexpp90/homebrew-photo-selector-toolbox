## 2026-07-27 - "Same Size" in a Comparison Layout Is an aspectRatio Constraint-Order Bug Waiting to Happen (Compose)
**Learning:** The Android Desktop selector's core promise is that the current frame and its two neighbours render at *identical* size, with the active one marked only by a border — a smaller or dimmed neighbour cannot be judged for sharpness or exposure, which is the whole task. Expressing that as `Modifier.fillMaxHeight().aspectRatio(4f/3f)` inside a weighted `Row` silently breaks it: `aspectRatio` defaults to `matchHeightConstraintsFirst = false`, so it resolves the *width* constraint first and lets each tile derive its own height. A wide frame beside a narrow one then ends up taller, and the difference is small enough to pass code review and eyeballing while defeating the comparison.
**Action:** Whenever several image tiles must share a dimension, make that dimension the driver explicitly — `aspectRatio(ratio, matchHeightConstraintsFirst = true)` when height is shared — and assert it: compare `getUnclippedBoundsInRoot()` heights of the tiles in a UI test, allowing only the border-width delta (2dp active vs 1dp resting). "Looks the same" is not a test.

## 2026-07-27 - Normalise Metrics to a Common "Goodness" Before Asking a User to Compare Them
**Learning:** The scan produces sharpness (5–100, higher better), noise (0.2–7, lower better), clipping percentages (0–10 / 0–14, lower better) and an aesthetic score (1–10, higher better). Showing those as five formatted numbers per frame across three frames asks the user to hold five different scales and two different directions in their head, per comparison, hundreds of times a session. They cannot, so they ignore the numbers. Mapping each metric onto a single 0..1 "goodness" where 1 is always good — and rendering it as a fixed-length bar tinted by threshold — turns "which of these three is best" from arithmetic into a glance, without hiding the raw value.
**Action:** Put the normalisation range and direction on the metric definition itself (`ScoreMetric.goodness(value)`), never in the composable, so it is unit-testable and every call site agrees. Give the bar a small non-zero floor (~6%) so a worst-case value still reads as a bar rather than as missing data. Compute "best of the visible frames" across only what is on screen — "best of 842" answers a question nobody asked — return no marker when fewer than two frames carry a value, and resolve ties to the earliest frame so the marker cannot flicker between identical neighbours.

## 2026-07-24 - Cross-Window Focus Loss Silently Kills Toplevel Key Bindings (Tkinter)
**Learning:** In the desktop fullscreen viewer, `M` (move to selection) delegates to the parent `SharpnessTool`, which updates its candidate listbox selection. That parent-side work can pull keyboard focus out of the fullscreen `Toplevel`, after which the viewer's own `bind("<n>")`/`bind("<p>")` handlers stop firing — the user sees "N does nothing after M" even though every index is correct. Mocked unit tests cannot reproduce this: the index arithmetic passes while the real app fails, so reading the navigation logic alone will never find it.
**Action:** Any secondary `Toplevel` that delegates an action back to its parent window must re-assert its own focus afterwards (`lift()` + `focus_set()`, guarded by `winfo_exists()` and try/except). Treat "shortcut stopped working after an action" as a focus-ownership bug first and an index bug second.

## 2026-07-24 - Never Trust Stored Indices Across a Parent Mutation
**Learning:** `open_fullscreen` passes the parent's *live* `candidates` list (or a slice of it) to `FullscreenViewer`. The parent mutates that list in place when grouping is off, but **rebinds** it to a brand-new list inside `apply_grouping_and_refresh` when grouping is on. The viewer's `file_list` therefore either aliases the parent's list or silently goes stale, and any `current_idx` arithmetic that spans an action is guesswork.
**Action:** After any action that mutates a shared list, re-derive the index from the item actually displayed (`file_list.index(self.path)`) instead of adjusting a stored integer, and prune the local list against the parent's current one. Deriving state from the visible item is robust to both aliasing and rebinding.

## 2026-07-24 - Long-Running Work Should Queue UI Requests, Not Disable the Control
**Learning:** Disabling the "Group Similar Series" toggle for the whole duration of a scan reads to the user as an unresponsive, frozen UI — they click and nothing happens, with no explanation. The codebase already had the correct pattern for the opposite direction (`_pending_scan`: a scan requested during grouping is queued and auto-started), but the mirror case was implemented as a hard disable.
**Action:** When two long-running passes conflict, keep both controls enabled and queue the second request with a visible "queued" state and a cancel affordance, instead of disabling the control. Implement conflict pairs symmetrically — if A-during-B queues, B-during-A must queue too.

## 2026-07-24 - Put the Layout Toggle Inside a Control Cluster, Not Floating Over the Screen
**Learning:** A control positioned with `Modifier.align(Alignment.TopEnd)` on an outer `Box` that wraps *several alternative layouts* will collide with whatever any of those layouts also puts in its top-right corner — here the focused layout's Fullscreen button sat directly under the layout toggle. The overlap is invisible in code review because the two controls live in different composables.
**Action:** Free-floating overlay controls must be owned by the layout that renders them, inside that layout's own control group. When a control is shared across alternative layouts, pass a callback down and let each layout place it. Add a UI test asserting the two controls' `getUnclippedBoundsInRoot()` do not intersect — overlap regressions are otherwise only caught by eye.

## 2026-07-24 - Letterbox Space Is Where Tablet Controls Belong
**Learning:** On a 16:10 tablet, a 4:3 photo fitted into a full-width region is height-limited and leaves a wide empty column on each side. Stacking controls *above* or *below* the image (or overlaying them on it) either wastes vertical space — the scarce dimension — or covers the photo being judged. Two narrow vertical rails in the letterbox columns cost zero vertical space and never obstruct the image.
**Action:** For image-comparison layouts, derive control placement from the aspect-ratio mismatch between the photo and the viewport: put controls in the axis where the letterbox is, and spend the scarce axis entirely on the image. Make secondary chrome (filmstrip, details overlay) collapsible from that rail.

## 2026-07-24 - An Icon Plus a Number Is Not a Label
**Learning:** The Toolbox showed post-scan metrics as an unlabelled icon and a bare value ("512", "3.2"). Users cannot tell which metric it is, what unit it is in, or whether a high number is good. The metric definitions were also duplicated across five call sites, so the same score was formatted `%.0f%%` in one view and `%.1f%%` in another.
**Action:** Define each metric once in a pure domain enum carrying its short label, display name, description, value format and direction (higher/lower is better), and render it through one shared chip component. Always state the direction somewhere reachable (legend sheet plus the accessibility description) — a number with no direction is not interpretable. Format with an explicit `Locale.US` so decimal separators do not shift with device locale.

## 2026-07-24 - Explain the Effect of a Gesture the First Time It Happens
**Learning:** In a gesture-first UI the *effect* of an action is invisible: a swipe right that copies a file to a folder looks identical to one that moves it. A one-off explanation at first use ("You copied this photo to Keepers … change this in Settings") is far more useful than a permanent hint, and far more useful than a generic tutorial, because it can name the actual configured verb and folder.
**Action:** Build first-run explanations from the user's *current* settings via a pure text builder, persist a seen-key set in DataStore so each fires exactly once, suppress them while a full-screen tutorial is up, and offer a "Reset Tutorials" setting. Keep the wording logic Android-free so the copy is unit-testable.

## 2026-07-11 - Jetpack Compose Gesture Conflict Resolution in Scrollable Views
**Learning:** When implementing custom zoom and pan gestures on an image inside a scrollable parent (like a `VerticalPager` or `HorizontalPager`) and adjacent to drag-triggered swipe actions, standard `pointerInput(Unit)` handlers will conflict unless conditionally intercepted. By checking if the view is zoomed (`scale > 1.05f`) or if there is multi-touch (`pointerCount > 1`), we can selectively consume drag gestures (via `change.consume()`), preventing them from propagating to the pager or sibling drag listeners.
**Action:** Always selectively consume pointer change events based on the view's current transformation state (`isZoomed`) in scrollable feeds. Use `change.consume()` for single-finger panning when zoomed to prevent accidental page navigation or swipe curation triggers.

## 2024-05-15 - Tkinter Theming and Accessibility
**Learning:** In Tkinter using the 'clam' theme via `ttk.Style`, focus states (which provide critical visual feedback for keyboard navigation and screen readers) can be entirely disabled if `focuscolor` is set to an empty string `""` or if state mappings don't account for the `focus` state.
**Action:** Always explicitly define `focuscolor` (e.g., to an accent color) and map the `focus` state to a visible visual change (like `bordercolor`) when creating or modifying custom `ttk` styles.

## 2024-06-17 - Keyboard Navigation in Tkinter's Clam Theme
**Learning:** Tkinter's 'clam' theme disables focus states on certain components (like `TNotebook.Tab` and `TCombobox`) by default, leading to accessibility issues as users navigating via keyboard lose track of their position.
**Action:** Always explicitly map/configure focus states (`focuscolor`, `bordercolor`, etc.) for keyboard navigation support when using the 'clam' theme to ensure interactive elements have clear visual focus indicators.

## 2024-05-18 - Keyboard Navigation in Tkinter's Clam Theme
**Learning:** In Tkinter using the 'clam' theme via `ttk.Style`, focus states can be completely hidden for `TCheckbutton` (and potentially other elements) even if `focuscolor` is set in `style.configure`, unless the `focus` state is explicitly mapped to a color using `style.map('TCheckbutton', focuscolor=[('focus', color)])`. This leads to broken keyboard navigation.
**Action:** Always verify that both `configure` and `map` methods properly declare focus colors for keyboard-accessible UI elements in Tkinter.

## 2024-05-18 - Discoverability of Global Shortcuts in Desktop App
**Learning:** Users often miss that actions like Move (M), Copy (C), and Delete (Del) have global keyboard shortcuts unless those shortcuts are explicitly hinted in the corresponding UI button text.
**Action:** Always append keyboard shortcut hints in parentheses (e.g., "Copy to Selection (C)") directly onto the action buttons themselves.
## 2024-07-11 - Focus Colors in Tkinter Theme Mapping
**Learning:** Even when `focuscolor` is configured on a `ttk.Style` for a specific widget, it may still not correctly highlight during keyboard navigation in themes like 'clam' unless `focuscolor` is also explicitly mapped using `style.map('Widget', focuscolor=[('focus', color)])`. This was observed on multiple widgets (`TButton`, `Primary.TButton`, `TNotebook.Tab`, `TCombobox`, `TEntry`).
**Action:** Always verify keyboard accessibility by checking `ttk.Style().map('Widget').get('focuscolor')` and explicitly include `focuscolor` in the `style.map` function alongside other state-driven properties.

## 2026-07-10 - Interactive Cursor for Canvas Panning
**Learning:** When implementing click-and-drag functionality on a custom element like a Tkinter Canvas, the default cursor provides no affordance. Users may not realize the area is draggable.
**Action:** Always set the default cursor to a recognizable interactive state (e.g., `hand2` in Tkinter) and explicitly change it to a dragging state (e.g., `fleur`) during active drag operations (`<ButtonPress-1>` to `<ButtonRelease-1>`) to provide continuous visual feedback.


## 2026-07-23 - One-Time On-Image Affordances vs. Permanent Overlay Clutter (Compose)
**Learning:** Drawing Previous/Next arrows permanently on top of comparison images (Toolbox Expanded selector) obstructs the photo being judged. Users only need the hint once to learn the tiles are tappable. Persisting a `hasSeenNavHint` flag (DataStore) and rendering the arrows only while it is false gives discoverability without permanent clutter. Also: the layout view-toggle and Move/Copy/Delete controls were sub-40dp — below the 48dp accessibility touch-target minimum — and sat over the image.
**Action:** For image-review UIs, treat on-image directional arrows as a first-run-only coach affordance (persist a "seen" flag, then suppress). Keep interactive controls at ≥48dp targets and positioned around (not over) the image; add a scrim behind controls that float over variable-brightness photos so they stay legible.

## 2026-07-27 - Scope Semantics Assertions for Dialogs/Sheets to Avoid Duplicate Node Matches (Compose)
**Learning:** When testing Compose modal bottom sheets or overlays over screens containing metrics or labels, `composeRule.onNode(hasText("..."))` can fail with `AssertionError: Expected exactly '1' node but found '2' nodes` if both the background screen and the sheet display identical labels (such as `ScoreMetric.NOISE`'s `shortLabel` and `displayName` both being `"Noise"`).
**Action:** Always scope semantics assertions for content inside sheets or dialogs using `hasAnyAncestor(hasTestTag("sheet_tag"))` (e.g. `hasText("Noise") and hasAnyAncestor(hasTestTag("score_legend_sheet"))`) to ensure queries specifically match the intended container element.


## 2026-07-28 - Contextual Hints in Tkinter LabelFrames
**Learning:** To add rich elements like contextual hints alongside the title of a Tkinter `ttk.LabelFrame`, construct a `ttk.Frame` containing the desired layout (e.g., labels for title and hint), and assign it to the frame's `labelwidget` configuration property.
**Action:** Use `labelwidget` instead of the `text` property when appending muted instructional text (like "Double-click to expand") to section titles to improve discoverability without cluttering the primary UI.
