## 2024-05-24 - [Keyboard Accessibility for Tkinter Tooltips]
**Learning:** In Tkinter, custom tooltips triggered only by mouse hover (`<Enter>`/`<Leave>`) are inaccessible to keyboard-only users who navigate via the `Tab` key.
**Action:** Always bind `<FocusIn>` and `<FocusOut>` events alongside hover events on interactive elements to ensure screen readers and keyboard users can trigger tooltips.
