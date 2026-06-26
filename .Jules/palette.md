## 2024-05-24 - Interactive Overlays in Tkinter Need Explicit Cursor Hints
**Learning:** When layering interactive elements (e.g., `ttk.Label` text overlays) on top of clickable image containers in Tkinter, the overlays intercept mouse hover events. If they don't have an explicit `cursor="hand2"` property, they revert the cursor to the default arrow, breaking the visual affordance of the underlying interactive component.
**Action:** Always explicitly define `cursor="hand2"` on all layered sub-elements within an interactive region to ensure consistent hover feedback across the entire area.
