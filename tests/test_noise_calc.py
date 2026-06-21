from pathlib import Path
from unittest.mock import patch

import cv2
import numpy as np

from photo_selector_toolbox.sharpness import calculate_noise


def test_noise_calc(tmp_path):
    img_path = tmp_path / "test.jpg"
    img = np.random.randint(0, 255, (100, 100, 3), dtype=np.uint8)
    cv2.imwrite(str(img_path), img)

    score = calculate_noise(img_path)
    print(score)
    assert isinstance(score, float)
    assert score > 0


@patch("photo_selector_toolbox.sharpness.get_image_data")
@patch("photo_selector_toolbox.sharpness.cv2")
def test_calculate_noise_exception(mock_cv2, mock_get_data):
    # Setup mock to return a valid dummy image
    mock_get_data.return_value = np.zeros((100, 100, 3), dtype=np.uint8)
    # Mock cvtColor to raise an exception
    mock_cv2.cvtColor.side_effect = Exception("Mocked noise error")

    # The function should catch the exception and return 0.0
    score = calculate_noise(Path("error.jpg"))
    assert score == 0.0
