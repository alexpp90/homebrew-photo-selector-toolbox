## 2026-06-22 - Fullscreen Viewer Error Path Testing
**What:** Added testing for the exception handling path in the `redraw` method of `FullscreenViewer` by mocking `PIL.Image.crop` to raise an `Exception`.
**Result:** Increased line coverage by verifying that errors during redrawing are gracefully caught and logged, instead of crashing the Tkinter event loop.
