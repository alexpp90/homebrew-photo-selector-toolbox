## 2024-05-24 - Interactive Widget Cursors in Tkinter
**Learning:** In Tkinter applications, standard buttons (`TButton`), checkbuttons (`TCheckbutton`), and radio buttons (`TRadiobutton`) do not automatically change the cursor to a pointer when hovered. Setting cursor individually on widgets can be cumbersome.
**Action:** Always add interactive widget cursors globally via the Tk option database (`root.option_add`), using conditional platform checks (`pointinghand` on macOS, `hand2` on others) to avoid TclError crashes.
