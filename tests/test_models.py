from pathlib import Path

from photo_selector_toolbox.models import ExifData, ScanResult


def test_scan_result_initialization():
    result = ScanResult(path=Path("/tmp/foo.jpg"))
    assert result.path == Path("/tmp/foo.jpg")
    assert result.score == "N/A"
    assert result.noise_score == "N/A"
    assert result.exif is None


def test_scan_result_custom_values():
    exif_data = ExifData(aperture=2.8)
    result = ScanResult(
        path=Path("/tmp/bar.jpg"), score=8.5, noise_score=2.1, exif=exif_data
    )
    assert result.score == 8.5
    assert result.noise_score == 2.1
    assert result.exif == exif_data
