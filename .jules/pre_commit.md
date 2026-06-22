## Testing Improvements
- Created `tests/test_image_panels.py` to cover exception paths in `src/photo_selector_toolbox/image_panels.py`.
- Wrote two new tests: `test_scale_image_to_panel_exception` and `test_scale_image_to_focus_label_exception`.
- The tests verify that exceptions during `img_copy.thumbnail` are properly caught and logged as expected.
