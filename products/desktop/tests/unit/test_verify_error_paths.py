from pathlib import Path
import unittest
from unittest.mock import MagicMock, patch

from photo_selector_toolbox.core.analyzer import analyze_data, analyze_data_json
from photo_selector_toolbox.core.duplicates import find_duplicates, get_file_hash, move_to_trash
from photo_selector_toolbox.core.sharpness import (
    calculate_all_scores,
    calculate_highlight_clipping,
    calculate_noise,
    calculate_shadow_clipping,
    calculate_sharpness,
)


class TestErrorPaths(unittest.TestCase):
    """[REQ-DESK-CULL.01] [REQ-DESK-CULL.02] [REQ-DESK-CULL.03] [REQ-DESK-CULL.04] [REQ-DESK-DUPE.03]"""

    @patch("photo_selector_toolbox.core.sharpness.cv2.cvtColor")
    @patch("photo_selector_toolbox.core.sharpness.get_image_data")
    def test_calculate_sharpness_exception(self, mock_get_data, mock_cvt_color):
        mock_get_data.return_value = MagicMock()
        mock_cvt_color.side_effect = Exception("Mocked sharpness error")

        score = calculate_sharpness(Path("error.jpg"))
        self.assertEqual(score, 0.0)

    @patch("photo_selector_toolbox.core.sharpness.cv2.cvtColor")
    @patch("photo_selector_toolbox.core.sharpness.get_image_data")
    def test_calculate_noise_exception(self, mock_get_data, mock_cvt_color):
        mock_get_data.return_value = MagicMock()
        mock_cvt_color.side_effect = Exception("Mocked noise error")

        score = calculate_noise(Path("error.jpg"))
        self.assertEqual(score, 0.0)

    @patch("photo_selector_toolbox.core.sharpness.cv2.cvtColor")
    @patch("photo_selector_toolbox.core.sharpness.get_image_data")
    def test_calculate_clipping_exceptions(self, mock_get_data, mock_cvt_color):
        mock_get_data.return_value = MagicMock()
        mock_cvt_color.side_effect = Exception("Mocked clipping error")

        self.assertEqual(calculate_highlight_clipping(Path("error.jpg")), 0.0)
        self.assertEqual(calculate_shadow_clipping(Path("error.jpg")), 0.0)

    @patch("photo_selector_toolbox.core.sharpness.cv2.cvtColor")
    @patch("photo_selector_toolbox.core.sharpness.get_image_data")
    def test_calculate_all_scores_grayscale_exception(self, mock_get_data, mock_cvt_color):
        mock_get_data.return_value = MagicMock()
        mock_cvt_color.side_effect = Exception("Mocked grayscale conversion error")

        res = calculate_all_scores(
            Path("error.jpg"),
            grid_size=8,
            tools={
                "sharpness": True,
                "noise": True,
                "highlight_clipping": True,
                "shadow_clipping": True,
            },
        )
        self.assertEqual(
            res,
            {
                "sharpness": 0.0,
                "noise": 0.0,
                "highlight_clipping": 0.0,
                "shadow_clipping": 0.0,
            },
        )

    def test_duplicates_error_paths(self):
        # Non-existent file hash returns None
        self.assertIsNone(get_file_hash(Path("/nonexistent/file_12345.jpg")))
        # Non-existent folder returns empty list
        self.assertEqual(find_duplicates(Path("/nonexistent/dir_12345")), [])

        with patch("photo_selector_toolbox.core.duplicates.send2trash", side_effect=OSError("Trash error")):
            with self.assertRaises(OSError):
                move_to_trash(Path("error.jpg"))

    def test_analyzer_empty_data_error_paths(self):
        # Empty input must not raise ZeroDivisionError or StatisticsError
        analyze_data([])
        self.assertEqual(analyze_data_json([]), {"total_images": 0})


if __name__ == "__main__":
    unittest.main()
