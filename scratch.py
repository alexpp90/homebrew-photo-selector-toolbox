from unittest.mock import patch, MagicMock
from pathlib import Path

def test_fullscreen_viewer_update_metadata_error_path():
    from photo_selector_toolbox.fullscreen_viewer import FullscreenViewer

    parent = MagicMock()
    path = Path("test_image.jpg")
    file_list = [path]
    parent.files_map = {}

    with (
        patch("photo_selector_toolbox.fullscreen_viewer.FullscreenViewer.load_image"),
        patch("photo_selector_toolbox.fullscreen_viewer.FullscreenViewer._update_basic_labels") as mock_update,
        patch("photo_selector_toolbox.fullscreen_viewer.logger.debug") as mock_logger,
    ):
        viewer = FullscreenViewer(parent, path, file_list=file_list)
        # Mock meta_panel so it exists, but make lift() raise the exception
        viewer.meta_panel = MagicMock()
        viewer.meta_panel.lift.side_effect = Exception("Test lift error")
        viewer.update_metadata()
        mock_logger.assert_any_call("Error updating metadata overlay in fullscreen: Test lift error")
