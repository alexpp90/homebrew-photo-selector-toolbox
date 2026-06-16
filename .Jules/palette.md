## 2024-05-15 - Tkinter Theming and Accessibility
**Learning:** In Tkinter using the 'clam' theme via `ttk.Style`, focus states (which provide critical visual feedback for keyboard navigation and screen readers) can be entirely disabled if `focuscolor` is set to an empty string `""` or if state mappings don't account for the `focus` state.
**Action:** Always explicitly define `focuscolor` (e.g., to an accent color) and map the `focus` state to a visible visual change (like `bordercolor`) when creating or modifying custom `ttk` styles.
