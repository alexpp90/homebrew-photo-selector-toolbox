## 2024-07-05 - Missing Drag Cursors in FullscreenViewer
**Learning:** Adding a click-and-drag interaction without visual cursor changes (like `hand2` and `fleur`) leaves users unsure if dragging is active or possible.
**Action:** Always set `cursor="hand2"` on interactive canvases and toggle to `cursor="fleur"` on `<ButtonPress-1>` to provide clear visual feedback during drag operations.
