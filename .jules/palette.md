## 2026-09-28 - Add tooltip for ttk.Button
**Learning:** In Tkinter, tooltip is not a built-in feature of ttk.Button. Attempting to call `bbox('insert')` on a ttk.Button will result in an error. Use `winfo_rootx()`, `winfo_width()`, `winfo_rooty()`, `winfo_height()` instead.
**Action:** Add tooltips to icon-only buttons using a custom ToolTip class.
