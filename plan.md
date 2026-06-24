1. **Add visual click affordance (cursor="hand2") to interactive overlay labels.**
   - In `src/photo_selector_toolbox/sharpness_gui.py`, `self.focus_prev_overlay` and `self.focus_next_overlay` overlay labels capture clicks (`<Button-1>`) to navigate images.
   - However, unlike their underlying image containers, they are missing the `cursor="hand2"` configuration. This causes the cursor to revert to the default arrow when hovering over the overlay text, breaking the interactive affordance.
   - I will add `cursor="hand2"` to both `ttk.Label` overlay declarations.
2. **Add a journal entry to `.Jules/palette.md` for this UX learning.**
   - Document that layered interactive UI elements (like overlays) in Tkinter must inherit or explicitly define `cursor` properties to maintain consistent click affordance across the entire interactive area.
3. **Run the full test suite.**
   - Execute `poetry run xvfb-run pytest tests/` to ensure no regressions are introduced.
4. **Complete pre-commit steps to ensure proper testing, verification, review, and reflection are done.**
5. **Submit the PR.**
   - Create a PR using the required Palette format: `🎨 Palette: Add interactive hand cursor to focus mode overlay labels`.
