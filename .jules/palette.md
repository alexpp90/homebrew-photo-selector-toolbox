## 2024-03-24 - Added hover pointer cursors to buttons
**Learning:** Interactive widgets in Tkinter lack native pointer changes on hover out-of-the-box, unlike the web. This reduces intuitive affordance. Setting styling properties on `.configure(cursor=...)` silently fails; it must be applied via `root.option_add` for global `ttk` widget impact. Using unconditionally `pointinghand` on Linux crashes Tk.
**Action:** When working on Tkinter UX, unconditionally add `root.option_add("*TButton.cursor", "hand2" if not macOS else "pointinghand")` for clear interaction cues.
