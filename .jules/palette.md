## 2025-02-25 - Pointer Cursor Issue on Tkinter Buttons
**Learning:** Tkinter `ttk` widgets don't respond to `cursor` styles configured through `ttk.Style().configure()`. To globally enable pointer cursors on buttons, it must be applied via the `tk.Tk` option database `root.option_add('*TButton.cursor', 'hand2')`.
**Action:** When asked to add pointer cursors to Tkinter apps on hover, modify the root options during theme setup (`apply_dark_theme` function) rather than touching `ttk.Style()`.
