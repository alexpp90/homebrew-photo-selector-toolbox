## 2024-05-24 - Global pointer cursors for Tkinter
**Learning:** In Tkinter, setting the `cursor` property via `ttk.Style().configure('TButton', cursor='hand2')` is silently ignored because it is a widget-level option, not a style option. Additionally, macOS requires `pointinghand` while Linux/Windows require `hand2`.
**Action:** To apply a cursor globally to `ttk` widgets, use the Tk option database (e.g., `root.option_add('*TButton.cursor', cursor)`) and dynamically select the cursor string based on `platform.system()`.
