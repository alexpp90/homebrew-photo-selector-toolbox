## 2026-09-26 - Tkinter Cursors
**Learning:** In Tkinter, setting the cursor property via ttk.Style().configure is silently ignored. The Tk option database must be used via root.option_add. Furthermore, macOS requires 'pointinghand' while Linux/Windows use 'hand2' to avoid TclError.
**Action:** Always use root.option_add('*TButton.cursor', cursor_name) and conditionally check platform.system() == 'Darwin' when adding pointer cursors to ttk widgets.
