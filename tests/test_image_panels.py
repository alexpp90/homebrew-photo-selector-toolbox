import unittest
from unittest.mock import MagicMock, patch
from pathlib import Path

from photo_selector_toolbox.image_panels import ImagePanelsMixin

class DummyImagePanels(ImagePanelsMixin):
    def __init__(self):
        # Mock the cache manager
        self.cache_manager = MagicMock()
        self.cache_manager.get_preview.return_value = None
        # Mock the parent to intercept the final UI update call
        self.parent = MagicMock()

class TestImagePanelsErrorPaths(unittest.TestCase):
    @patch("photo_selector_toolbox.image_panels.logger")
    @patch("photo_selector_toolbox.image_panels.load_image_preview")
    def test_load_images_background_load_exception(self, mock_load_image, mock_logger):
        panels = DummyImagePanels()

        # Make load_image_preview raise an exception
        mock_load_image.side_effect = Exception("Test loading error")

        # Dummy paths and sizes
        prev_path = Path("prev.jpg")
        curr_path = Path("curr.jpg")
        next_path = Path("next.jpg")
        size = (100, 100)

        # Execute the method
        panels.load_images_background(
            prev_path, curr_path, next_path,
            size, size, size
        )

        # Verify that the exception was caught and logged for each file
        self.assertEqual(mock_logger.error.call_count, 3)
        mock_logger.error.assert_any_call(f"Error loading {prev_path}: Test loading error")
        mock_logger.error.assert_any_call(f"Error loading {curr_path}: Test loading error")
        mock_logger.error.assert_any_call(f"Error loading {next_path}: Test loading error")

        # Verify that the final UI update was queued
        self.assertTrue(panels.parent.after.called)

if __name__ == "__main__":
    unittest.main()
