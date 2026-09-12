## 2024-05-24 - Tkinter global cursor issue
**Learning:** Applying a cursor via `ttk.Style().configure()` is silently ignored because it is a widget-level option, not a style option. Also, applying cursors to container widgets like `TNotebook` makes child widgets inherit the cursor.
**Action:** Apply cursors to specific interactive leaf widgets like `TButton`, `TCheckbutton` using the Tk option database (e.g., `root.option_add('*TButton.cursor', 'hand2')`). Use `pointinghand` for macOS, `hand2` for others.
