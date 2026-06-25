## 2024-05-15 - Tkinter Theming and Accessibility
**Learning:** In Tkinter using the 'clam' theme via `ttk.Style`, focus states (which provide critical visual feedback for keyboard navigation and screen readers) can be entirely disabled if `focuscolor` is set to an empty string `""` or if state mappings don't account for the `focus` state.
**Action:** Always explicitly define `focuscolor` (e.g., to an accent color) and map the `focus` state to a visible visual change (like `bordercolor`) when creating or modifying custom `ttk` styles.

## 2024-06-17 - Keyboard Navigation in Tkinter's Clam Theme
**Learning:** Tkinter's 'clam' theme disables focus states on certain components (like `TNotebook.Tab` and `TCombobox`) by default, leading to accessibility issues as users navigating via keyboard lose track of their position.
**Action:** Always explicitly map/configure focus states (`focuscolor`, `bordercolor`, etc.) for keyboard navigation support when using the 'clam' theme to ensure interactive elements have clear visual focus indicators.
## 2026-06-25 - Tkinter Clickable Overlays
**Learning:** In Tkinter, when layering interactive elements (e.g., `ttk.Label` text overlays on top of clickable image containers), explicitly define `cursor="hand2"` on all layered sub-elements. Otherwise, the top element intercepts hover events and reverts the cursor to the default arrow, breaking the visual click affordance of the overall interactive region.
**Action:** Always apply `cursor="hand2"` to text labels or overlays that are bound to click events (`<Button-1>`) and sit above interactive containers.
