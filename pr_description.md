🎯 **What:**
Added an automated test (`tests/test_image_panels.py`) to cover the `try/except` error path in `ImagePanelsMixin.load_images_background` (line 269). This ensures that if the background thread fails to load an image preview using `load_image_preview` (e.g., due to corrupt files or missing dependencies), the application correctly catches the exception, logs it, and continues executing without crashing.

📊 **Coverage:**
- Added test class `TestImagePanelsErrorPaths` with test `test_load_images_background_load_exception`.
- Now correctly tests the `Exception` path where `logger.error` is called when an image load fails.
- Verifies that the UI main thread is still safely notified (`self.parent.after(0, ...)`) even when the image fails to load.

✨ **Result:**
The line 269 `except Exception` block in `src/photo_selector_toolbox/image_panels.py` is now fully covered, preventing potential regressions where the UI thread might get blocked or unhandled exceptions crash the background process during image navigation.
