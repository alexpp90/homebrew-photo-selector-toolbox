from unittest.mock import MagicMock, patch
from PIL import Image
from photo_selector_toolbox.image_panels import ImagePanelsMixin


class DummyImagePanels(ImagePanelsMixin):
    def __init__(self):
        self.cache_manager = MagicMock()
        self.parent = MagicMock()
        self.update_panels_final = MagicMock()


def test_load_images_background_all_none():
    panels = DummyImagePanels()
    panels.load_images_background(None, None, None, (100, 100), (100, 100), (100, 100))
    panels.parent.after.assert_called_once()

    # Extract the lambda and call it
    call_args = panels.parent.after.call_args
    assert call_args[0][0] == 0
    func = call_args[0][1]
    func()

    panels.update_panels_final.assert_called_once_with(
        None, None, None, None, None, None
    )


@patch("photo_selector_toolbox.image_panels.load_image_preview")
def test_load_images_background_cache_hit(mock_load_image_preview):
    panels = DummyImagePanels()

    mock_img = MagicMock(spec=Image.Image)
    mock_img_copy = MagicMock(spec=Image.Image)
    mock_img.copy.return_value = mock_img_copy

    panels.cache_manager.get_preview.return_value = mock_img

    panels.load_images_background("path1", None, None, (100, 100), (50, 50), (100, 100))

    mock_load_image_preview.assert_not_called()
    mock_img.copy.assert_called_once()
    mock_img_copy.thumbnail.assert_called_once_with((50, 50), Image.Resampling.LANCZOS)

    panels.parent.after.assert_called_once()
    call_args = panels.parent.after.call_args
    func = call_args[0][1]
    func()
    panels.update_panels_final.assert_called_once_with(
        mock_img_copy, None, None, "path1", None, None
    )


@patch("photo_selector_toolbox.image_panels.load_image_preview")
def test_load_images_background_cache_miss(mock_load_image_preview):
    panels = DummyImagePanels()
    panels.cache_manager.get_preview.return_value = None

    mock_img = MagicMock(spec=Image.Image)
    mock_img_copy = MagicMock(spec=Image.Image)
    mock_img.copy.return_value = mock_img_copy
    mock_load_image_preview.return_value = mock_img

    # We also need a preview_lock and preview_cache dict in cache_manager
    lock_mock = MagicMock()
    panels.cache_manager.preview_lock = lock_mock
    panels.cache_manager.preview_lock.__enter__ = MagicMock()
    panels.cache_manager.preview_lock.__exit__ = MagicMock()
    panels.cache_manager.preview_cache = {}

    panels.load_images_background(
        None, "path2", None, (200, 200), (100, 100), (100, 100)
    )

    mock_load_image_preview.assert_called_once_with("path2", max_size=(1200, 900))
    assert panels.cache_manager.preview_cache["path2"] == mock_img

    mock_img.copy.assert_called_once()
    mock_img_copy.thumbnail.assert_called_once_with(
        (200, 200), Image.Resampling.LANCZOS
    )

    panels.parent.after.assert_called_once()
    call_args = panels.parent.after.call_args
    func = call_args[0][1]
    func()
    panels.update_panels_final.assert_called_once_with(
        None, mock_img_copy, None, None, "path2", None
    )


@patch("photo_selector_toolbox.image_panels.logger")
@patch("photo_selector_toolbox.image_panels.load_image_preview")
def test_load_images_background_load_exception(mock_load_image_preview, mock_logger):
    panels = DummyImagePanels()
    panels.cache_manager.get_preview.return_value = None

    mock_load_image_preview.side_effect = Exception("Load failed")

    panels.load_images_background(
        None, "path3", None, (100, 100), (100, 100), (100, 100)
    )

    mock_logger.error.assert_called_with("Error loading path3: Load failed")

    panels.parent.after.assert_called_once()
    call_args = panels.parent.after.call_args
    func = call_args[0][1]
    func()
    panels.update_panels_final.assert_called_once_with(
        None, None, None, None, "path3", None
    )


@patch("photo_selector_toolbox.image_panels.logger")
@patch("photo_selector_toolbox.image_panels.load_image_preview")
def test_load_images_background_scale_exception(mock_load_image_preview, mock_logger):
    panels = DummyImagePanels()

    mock_img = MagicMock(spec=Image.Image)
    mock_img.copy.side_effect = Exception("Scale failed")
    panels.cache_manager.get_preview.return_value = mock_img

    panels.load_images_background(
        None, "path4", None, (100, 100), (100, 100), (100, 100)
    )

    mock_logger.error.assert_called_with("Error preparing path4: Scale failed")

    panels.parent.after.assert_called_once()
    call_args = panels.parent.after.call_args
    func = call_args[0][1]
    func()
    panels.update_panels_final.assert_called_once_with(
        None, None, None, None, "path4", None
    )
