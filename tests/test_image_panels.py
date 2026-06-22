from unittest.mock import MagicMock, patch
from photo_selector_toolbox.image_panels import ImagePanelsMixin


class DummyApp(ImagePanelsMixin):
    pass


@patch("photo_selector_toolbox.image_panels.logger")
@patch("photo_selector_toolbox.image_panels.ImageTk")
def test_scale_image_to_panel_exception(mock_imagetk, mock_logger):
    app = DummyApp()

    panel = MagicMock()
    # Mock container size
    panel.img_container.winfo_width.return_value = 100
    panel.img_container.winfo_height.return_value = 100

    # Mock pil_image and its size
    panel.pil_image = MagicMock()
    panel.pil_image.size = (200, 200)

    # Mock copy to return an image that raises an exception on thumbnail
    img_copy = MagicMock()
    panel.pil_image.copy.return_value = img_copy
    img_copy.thumbnail.side_effect = Exception("Mocked scaling error")

    # Call the method
    app.scale_image_to_panel(panel)

    # Verify exception is logged
    mock_logger.error.assert_called_once()
    args = mock_logger.error.call_args[0]
    assert "Error scaling panel image" in args[0]
    assert "Mocked scaling error" in args[0]


@patch("photo_selector_toolbox.image_panels.logger")
@patch("photo_selector_toolbox.image_panels.ImageTk")
def test_scale_image_to_focus_label_exception(mock_imagetk, mock_logger):
    app = DummyApp()

    lbl = MagicMock()

    # Mock container size
    lbl.container.winfo_width.return_value = 100
    lbl.container.winfo_height.return_value = 100

    # Mock pil_image and its size
    lbl.pil_image = MagicMock()
    lbl.pil_image.size = (200, 200)

    # Mock copy to return an image that raises an exception on thumbnail
    img_copy = MagicMock()
    lbl.pil_image.copy.return_value = img_copy
    img_copy.thumbnail.side_effect = Exception("Mocked scaling focus label error")

    # Call the method
    app.scale_image_to_focus_label(lbl)

    # Verify exception is logged
    mock_logger.error.assert_called_once()
    args = mock_logger.error.call_args[0]
    assert "Error scaling focus label image" in args[0]
    assert "Mocked scaling focus label error" in args[0]
