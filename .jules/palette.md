## 2024-05-24 - Tkinter Tooltip Keyboard Accessibility
**Learning:** In Tkinter, custom interactive elements like tooltips triggered only by mouse hover (`<Enter>`/`<Leave>`) are inaccessible to keyboard-only users navigating via the `Tab` key.
**Action:** Always bind `<FocusIn>` and `<FocusOut>` events alongside hover events to ensure keyboard accessibility.
