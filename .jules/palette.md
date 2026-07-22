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


## 2026-07-11 - Double-click hints on Image Panels
**Learning:** Users may not realize that image preview panels support a double-click to zoom action because standard Tkinter/ttk components do not provide native hover indicators for this specific interaction.
**Action:** Always provide explicit textual hints for hidden or non-standard interactions (like double-click to zoom) next to the relevant element, ensuring they are visually distinct (e.g., using ) to maintain a clear visual hierarchy.

## 2026-07-11 - Double-click hints on Image Panels
**Learning:** Users may not realize that image preview panels support a double-click to zoom action because standard Tkinter/ttk components do not provide native hover indicators for this specific interaction.
**Action:** Always provide explicit textual hints for hidden or non-standard interactions (like double-click to zoom) next to the relevant element, ensuring they are visually distinct (e.g., using a muted label style) to maintain a clear visual hierarchy.
