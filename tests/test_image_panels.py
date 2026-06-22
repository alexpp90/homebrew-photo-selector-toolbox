import pytest
from unittest.mock import MagicMock, patch
from pathlib import Path
from PIL import Image

from photo_selector_toolbox.image_panels import ImagePanelsMixin


class DummyTool(ImagePanelsMixin):
    def __init__(self):
        self.parent = MagicMock()
        self.cache_manager = MagicMock()
        self.cache_manager.preview_cache = {}
        self.cache_manager.preview_lock = MagicMock()


@pytest.fixture
def dummy_tool():
    return DummyTool()


def test_load_images_background_none_paths(dummy_tool):
    """Test that load_images_background handles None paths correctly."""

    dummy_tool.load_images_background(
        prev_path=None,
        curr_path=None,
        next_path=None,
        size_curr=(100, 100),
        size_prev=(100, 100),
        size_next=(100, 100)
    )

    # Should call parent.after with 0 and a lambda.
    # We need to extract the lambda and call it to verify its behavior.
    dummy_tool.parent.after.assert_called_once()
    args, kwargs = dummy_tool.parent.after.call_args
    assert args[0] == 0

    # Extract the lambda callback
    callback = args[1]

    # Mock update_panels_final to verify it's called correctly by the lambda
    dummy_tool.update_panels_final = MagicMock()
    callback()

    # Verify update_panels_final is called with Nones for images and paths
    dummy_tool.update_panels_final.assert_called_once_with(
        None, None, None, None, None, None
    )


@patch("photo_selector_toolbox.image_panels.load_image_preview")
def test_load_images_background_cache_miss(mock_load_image_preview, dummy_tool):
    """Test cache miss scenario: loads image, updates cache, and scales."""
    path = Path("test.jpg")

    # Setup cache miss
    dummy_tool.cache_manager.get_preview.return_value = None

    # Setup mock image from load_image_preview
    mock_img = MagicMock(spec=Image.Image)
    mock_copy = MagicMock(spec=Image.Image)
    mock_img.copy.return_value = mock_copy
    mock_load_image_preview.return_value = mock_img

    dummy_tool.load_images_background(
        prev_path=None,
        curr_path=path,
        next_path=None,
        size_curr=(100, 100),
        size_prev=(100, 100),
        size_next=(100, 100)
    )

    # Verify cache manager gets queried
    dummy_tool.cache_manager.get_preview.assert_called_with(path)

    # Verify load_image_preview is called
    mock_load_image_preview.assert_called_once_with(path, max_size=(1200, 900))

    # Verify lock was acquired and cache updated
    dummy_tool.cache_manager.preview_lock.__enter__.assert_called()
    assert dummy_tool.cache_manager.preview_cache[path] == mock_img

    # Verify image scaling
    mock_img.copy.assert_called_once()
    mock_copy.thumbnail.assert_called_once_with((100, 100), Image.Resampling.LANCZOS)

    # Verify callback dispatch
    dummy_tool.parent.after.assert_called_once()
    callback = dummy_tool.parent.after.call_args[0][1]
    dummy_tool.update_panels_final = MagicMock()
    callback()
    dummy_tool.update_panels_final.assert_called_once_with(
        None, mock_copy, None, None, path, None
    )


@patch("photo_selector_toolbox.image_panels.load_image_preview")
def test_load_images_background_cache_hit(mock_load_image_preview, dummy_tool):
    """Test cache hit scenario: bypasses load, uses cache, and scales."""
    path = Path("test.jpg")

    # Setup mock image in cache
    mock_img = MagicMock(spec=Image.Image)
    mock_copy = MagicMock(spec=Image.Image)
    mock_img.copy.return_value = mock_copy

    # Setup cache hit
    dummy_tool.cache_manager.get_preview.return_value = mock_img

    dummy_tool.load_images_background(
        prev_path=None,
        curr_path=path,
        next_path=None,
        size_curr=(100, 100),
        size_prev=(100, 100),
        size_next=(100, 100)
    )

    # Verify cache manager gets queried
    dummy_tool.cache_manager.get_preview.assert_called_once_with(path)

    # Verify load_image_preview is NOT called
    mock_load_image_preview.assert_not_called()

    # Verify lock is NOT acquired (no cache update)
    dummy_tool.cache_manager.preview_lock.__enter__.assert_not_called()

    # Verify image scaling
    mock_img.copy.assert_called_once()
    mock_copy.thumbnail.assert_called_once_with((100, 100), Image.Resampling.LANCZOS)

    # Verify callback dispatch
    dummy_tool.parent.after.assert_called_once()
    callback = dummy_tool.parent.after.call_args[0][1]
    dummy_tool.update_panels_final = MagicMock()
    callback()
    dummy_tool.update_panels_final.assert_called_once_with(
        None, mock_copy, None, None, path, None
    )


@patch("photo_selector_toolbox.image_panels.logger")
@patch("photo_selector_toolbox.image_panels.load_image_preview")
def test_load_images_background_load_exception(mock_load_image_preview, mock_logger, dummy_tool):
    """Test handling of exception during load_image_preview."""
    path = Path("test.jpg")

    # Setup cache miss
    dummy_tool.cache_manager.get_preview.return_value = None

    # Make load_image_preview raise an exception
    mock_load_image_preview.side_effect = Exception("Mock load error")

    dummy_tool.load_images_background(
        prev_path=None,
        curr_path=path,
        next_path=None,
        size_curr=(100, 100),
        size_prev=(100, 100),
        size_next=(100, 100)
    )

    # Verify error is logged
    mock_logger.error.assert_called_with(f"Error loading {path}: Mock load error")

    # Verify callback dispatch with None for the failed image
    dummy_tool.parent.after.assert_called_once()
    callback = dummy_tool.parent.after.call_args[0][1]
    dummy_tool.update_panels_final = MagicMock()
    callback()
    dummy_tool.update_panels_final.assert_called_once_with(
        None, None, None, None, path, None
    )


@patch("photo_selector_toolbox.image_panels.logger")
@patch("photo_selector_toolbox.image_panels.load_image_preview")
def test_load_images_background_thumbnail_exception(mock_load_image_preview, mock_logger, dummy_tool):
    """Test handling of exception during image copy/thumbnail generation."""
    path = Path("test.jpg")

    # Setup cache hit with broken image
    mock_img = MagicMock(spec=Image.Image)
    # Raising error on copy
    mock_img.copy.side_effect = Exception("Mock copy error")
    dummy_tool.cache_manager.get_preview.return_value = mock_img

    dummy_tool.load_images_background(
        prev_path=None,
        curr_path=path,
        next_path=None,
        size_curr=(100, 100),
        size_prev=(100, 100),
        size_next=(100, 100)
    )

    # Verify error is logged
    mock_logger.error.assert_called_with(f"Error preparing {path}: Mock copy error")

    # Verify callback dispatch with None for the failed image
    dummy_tool.parent.after.assert_called_once()
    callback = dummy_tool.parent.after.call_args[0][1]
    dummy_tool.update_panels_final = MagicMock()
    callback()
    dummy_tool.update_panels_final.assert_called_once_with(
        None, None, None, None, path, None
    )
