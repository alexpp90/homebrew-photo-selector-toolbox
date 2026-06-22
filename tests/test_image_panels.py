import pytest
from unittest.mock import MagicMock, patch
import tkinter as tk
from tkinter import ttk
from photo_selector_toolbox.image_panels import ImagePanelsMixin


class DummyApp(tk.Tk, ImagePanelsMixin):
    def __init__(self):
        super().__init__()


@pytest.fixture
def dummy_app():
    app = DummyApp()
    yield app
    app.destroy()


def test_scale_image_to_focus_label_exception(dummy_app):
    lbl = ttk.Label(dummy_app)
    lbl.container = ttk.Frame(dummy_app)

    mock_pil_image = MagicMock()
    mock_pil_image.size = (100, 100)
    mock_pil_image.copy.side_effect = Exception("Mock scaling error")

    lbl.pil_image = mock_pil_image

    lbl.container.winfo_width = MagicMock(return_value=200)
    lbl.container.winfo_height = MagicMock(return_value=200)

    with patch("photo_selector_toolbox.image_panels.logger") as mock_logger:
        dummy_app.scale_image_to_focus_label(lbl)

        mock_logger.error.assert_called_once()
        assert (
            "Error scaling focus label image: Mock scaling error"
            in mock_logger.error.call_args[0][0]
        )


def test_scale_image_to_panel_exception(dummy_app):
    panel = MagicMock()

    mock_pil_image = MagicMock()
    mock_pil_image.size = (100, 100)
    mock_pil_image.copy.side_effect = Exception("Mock scaling error")
    panel.pil_image = mock_pil_image

    panel.img_container = MagicMock()
    panel.img_container.winfo_width.return_value = 200
    panel.img_container.winfo_height.return_value = 200

    with patch("photo_selector_toolbox.image_panels.logger") as mock_logger:
        dummy_app.scale_image_to_panel(panel)

        mock_logger.error.assert_called_once()
        assert (
            "Error scaling panel image: Mock scaling error"
            in mock_logger.error.call_args[0][0]
        )
