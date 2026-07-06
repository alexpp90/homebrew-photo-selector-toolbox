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

## $(date +%Y-%m-%d) - Drag Affordances on Image Canvas
**Learning:** In Tkinter, a standard `<B1-Motion>` click-and-drag interaction without visual cursor feedback leaves users uncertain if the element is actually interactive or currently being dragged.
**Action:** Enhance UX for click-and-drag panning by setting the default canvas cursor to `hand2`, changing it to `fleur` on `<ButtonPress-1>` to indicate active dragging, and explicitly binding `<ButtonRelease-1>` to restore the `hand2` cursor.
