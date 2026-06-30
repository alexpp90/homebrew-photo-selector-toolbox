🎯 **What:**
Added a missing error path test to cover the scenario where `Image.thumbnail()` raises an exception inside `load_images_background` (specifically line 281 in `src/photo_selector_toolbox/image_panels.py`). This occurs if the background worker successfully gets/loads an image, successfully calls `.copy()`, but fails during `.thumbnail()`.

📊 **Coverage:**
This covers the exception handler that executes `logger.error(f"Error preparing {path}: {e}")` and returns `None` on line 281/282/283. Before this, the test coverage skipped these lines. Now coverage metrics properly report 100% on the happy path execution (line 280) and 100% on the exception flow.

✨ **Result:**
The automated testing suite now accurately verifies that when dynamic image resizing operations fail (which GUI-bound image operations sometimes do), the application safely handles the `Exception`, logs it properly, and returns `None` rather than crashing the background thread.
