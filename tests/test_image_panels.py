from unittest.mock import MagicMock, patch
from PIL import Image

from photo_selector_toolbox.image_panels import ImagePanelsMixin

class DummyApp(ImagePanelsMixin):
    def __init__(self):
        self.parent = MagicMock()
        self.cache_manager = MagicMock()
        self.panel_prev = MagicMock()
        self.panel_curr = MagicMock()
        self.panel_next = MagicMock()

def test_load_images_background_exception():
    app = DummyApp()

    # Mock cache_manager to not return anything, forcing it to load via load_image_preview
    app.cache_manager.get_preview.return_value = None

    # Mock load_image_preview to return a valid dummy PIL Image
    dummy_img = Image.new('RGB', (100, 100))

    # In order to reach line 281, we need to test the inner try-except block
    # of `get_image` inside `load_images_background`
    # That block occurs around line 277-283

    with patch('photo_selector_toolbox.image_panels.load_image_preview', return_value=dummy_img):
        # We can patch thumbnail on the PIL Image object to raise an exception
        # This will trigger the exception block at line 281
        with patch.object(Image.Image, 'thumbnail', side_effect=Exception("Test mock exception")):
            with patch('photo_selector_toolbox.image_panels.logger') as mock_logger:
                app.load_images_background(
                    prev_path="prev.jpg",
                    curr_path="curr.jpg",
                    next_path="next.jpg",
                    size_curr=(100, 100),
                    size_prev=(100, 100),
                    size_next=(100, 100)
                )

                # Check if logger.error was called with the correct message prefix
                # (which confirms we hit the exception block at line 281)
                assert mock_logger.error.called

                error_calls = mock_logger.error.call_args_list
                for call in error_calls:
                    assert "Error preparing" in call[0][0]

                # Verify that it handled it safely and proceeded to update the main thread with None images
                app.parent.after.assert_called_once()
                args, _ = app.parent.after.call_args

                # Ensure the delay is 0 and the callback is passed
                assert args[0] == 0
