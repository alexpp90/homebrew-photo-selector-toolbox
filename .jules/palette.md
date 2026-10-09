## 2024-05-20 - Accessible Tooltips
**Learning:** In Tkinter, custom interactive elements like tooltips triggered only by mouse hover are inaccessible to keyboard-only users navigating via the Tab key.
**Action:** Always bind <FocusIn> and <FocusOut> events alongside hover events to ensure keyboard accessibility.
