from pathlib import Path
from unittest.mock import patch
import numpy as np
import cv2
from photo_selector_toolbox.sharpness import (
    calculate_highlight_clipping,
    calculate_shadow_clipping,
    HighlightClippingTool,
    ShadowClippingTool,
)
from photo_selector_toolbox.tools import ToolRegistry


def test_exposure_clipping_calculators(tmp_path):
    img_path = tmp_path / "exposure_test.jpg"

    # Create an image where:
    # - 10% of pixels are blown highlights (intensity 255)
    # - 20% of pixels are crushed shadows (intensity 0)
    # - 70% of pixels are midtones (intensity 128)
    img = np.ones((100, 100, 3), dtype=np.uint8) * 128

    # 100x100 = 10,000 total pixels.
    # Blown highlights: 1,000 pixels (10 rows of 100 pixels)
    img[0:10, :] = 255
    # Crushed shadows: 2,000 pixels (20 rows of 100 pixels)
    img[10:30, :] = 0

    cv2.imwrite(str(img_path), img)

    hl_score = calculate_highlight_clipping(img_path)
    sd_score = calculate_shadow_clipping(img_path)

    assert isinstance(hl_score, float)
    assert isinstance(sd_score, float)
    # Verify exact calculations
    # Highlight threshold >= 254 (our pixels are 255, so they match)
    assert abs(hl_score - 10.0) < 0.01
    # Shadow threshold <= 2 (our pixels are 0, so they match)
    assert abs(sd_score - 20.0) < 0.01


@patch("photo_selector_toolbox.sharpness.get_image_data")
def test_calculators_missing_image(mock_get_data):
    mock_get_data.return_value = None
    assert calculate_highlight_clipping(Path("missing.jpg")) == 0.0
    assert calculate_shadow_clipping(Path("missing.jpg")) == 0.0


@patch("photo_selector_toolbox.sharpness.get_image_data")
@patch("photo_selector_toolbox.sharpness.cv2")
def test_calculators_exception(mock_cv2, mock_get_data):
    mock_get_data.return_value = np.zeros((10, 10, 3), dtype=np.uint8)
    mock_cv2.cvtColor.side_effect = Exception("Mocked processing error")

    assert calculate_highlight_clipping(Path("error.jpg")) == 0.0
    assert calculate_shadow_clipping(Path("error.jpg")) == 0.0


def test_tool_registry():
    # Verify they are correctly registered
    hl_tool_class = ToolRegistry.get("highlight_clipping")
    sd_tool_class = ToolRegistry.get("shadow_clipping")

    assert hl_tool_class is HighlightClippingTool
    assert sd_tool_class is ShadowClippingTool

    # Verify analyze method
    with patch(
        "photo_selector_toolbox.sharpness.calculate_highlight_clipping"
    ) as mock_hl:
        mock_hl.return_value = 5.5
        hl_tool = HighlightClippingTool()
        assert hl_tool.analyze(Path("dummy.jpg")) == 5.5
        mock_hl.assert_called_once_with(Path("dummy.jpg"))

    with patch("photo_selector_toolbox.sharpness.calculate_shadow_clipping") as mock_sd:
        mock_sd.return_value = 12.3
        sd_tool = ShadowClippingTool()
        assert sd_tool.analyze(Path("dummy.jpg")) == 12.3
        mock_sd.assert_called_once_with(Path("dummy.jpg"))
