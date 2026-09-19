## 2023-10-27 - [Global cursor setting causing regression]
**Learning:** In Tkinter, setting a global cursor on container widgets (like `root.option_add('*TNotebook.cursor', 'hand2')`) introduces severe UX regressions because child widgets (like application pages/frames) inherit the cursor if they don't explicitly define one, leading to the entire page incorrectly displaying a hand cursor.
**Action:** Only apply cursors to specific interactive leaf widgets (e.g., `TButton`, `TCheckbutton`).
