## 2024-05-24 - Initial Palette Note
**Learning:** Checking memory.
**Action:** Created palette.md.
## 2024-05-24 - Targeted Cursor Application
**Learning:** Applying a global cursor in Tkinter to containers like frames or notebooks causes child widgets to incorrectly inherit it. Cursors must be specifically targeted to interactive leaf widgets like TButton or TCheckbutton via the option database. Also, macOS uses "pointinghand" while Linux/Windows use "hand2".
**Action:** Always target specific widgets when applying hover cursors instead of setting them on top-level elements or containers.
