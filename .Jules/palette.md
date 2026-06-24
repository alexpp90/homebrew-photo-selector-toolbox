## 2024-05-15 - Tkinter Theming and Accessibility
**Learning:** In Tkinter using the 'clam' theme via `ttk.Style`, focus states (which provide critical visual feedback for keyboard navigation and screen readers) can be entirely disabled if `focuscolor` is set to an empty string `""` or if state mappings don't account for the `focus` state.
**Action:** Always explicitly define `focuscolor` (e.g., to an accent color) and map the `focus` state to a visible visual change (like `bordercolor`) when creating or modifying custom `ttk` styles.

## 2024-06-17 - Keyboard Navigation in Tkinter's Clam Theme
**Learning:** Tkinter's 'clam' theme disables focus states on certain components (like `TNotebook.Tab` and `TCombobox`) by default, leading to accessibility issues as users navigating via keyboard lose track of their position.
**Action:** Always explicitly map/configure focus states (`focuscolor`, `bordercolor`, etc.) for keyboard navigation support when using the 'clam' theme to ensure interactive elements have clear visual focus indicators.

## 2024-06-24 - Layered Interactive Elements Click Affordance
**Learning:** In Tkinter, when layering interactive labels or overlays (like `ttk.Label` with text overlays) on top of larger interactive image containers, the overlay element intercepts mouse hover events. If the overlay doesn't explicitly have the correct cursor set (e.g., `cursor="hand2"`), the user's cursor will revert to the default arrow while hovering over the text, breaking the click affordance of the overall clickable region.
**Action:** Always explicitly define `cursor="hand2"` on all layered sub-elements (like text overlays) that are part of a larger clickable container to ensure a consistent, uninterrupted hover state across the entire interactive area.
