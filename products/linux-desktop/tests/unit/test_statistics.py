"""Unit tests for Library Statistics aggregation engine and optical histograms.

Adheres to:
- R-LINUX-TOOLS-02: Library Statistics Engine
- R-LINUX-META-03: Formatted Optical Histograms
"""

from pathlib import Path

from photo_selector_linux.core.models import (
    CandidatePhoto,
    ExposureMetadata,
    PhotoStatus,
    QualityScores,
)
from photo_selector_linux.core.statistics import (
    calculate_library_statistics,
    normalize_extension,
)


def test_library_statistics_empty():
    """Verify empty photo list returns safe zeroed statistics (R-LINUX-TOOLS-02)."""
    stats = calculate_library_statistics([])
    assert stats.total_photo_count == 0
    assert stats.candidate_count == 0
    assert stats.selected_count == 0
    assert stats.total_storage_bytes == 0
    assert stats.average_sharpness is None
    assert len(stats.format_statistics) == 0


def test_status_and_storage_aggregation():
    """Verify status breakdown and storage byte accumulation (R-LINUX-TOOLS-02)."""
    c1 = CandidatePhoto(primary_path=Path("/tmp/1.jpg"), status=PhotoStatus.CANDIDATE)
    c2 = CandidatePhoto(primary_path=Path("/tmp/2.jpg"), status=PhotoStatus.SELECTED)
    c3 = CandidatePhoto(primary_path=Path("/tmp/3.jpg"), status=PhotoStatus.COPIED)
    c4 = CandidatePhoto(primary_path=Path("/tmp/4.jpg"), status=PhotoStatus.TRASHED)

    # Attach fake sizes
    c1.file_size = 1000
    c2.file_size = 2000
    c3.file_size = 3000
    c4.file_size = 4000

    stats = calculate_library_statistics([c1, c2, c3, c4])
    assert stats.total_photo_count == 4
    assert stats.candidate_count == 1
    assert stats.selected_count == 1
    assert stats.copied_count == 1
    assert stats.trashed_count == 1
    assert stats.total_storage_bytes == 10000
    assert stats.selected_storage_bytes == 5000  # selected (2000) + copied (3000)
    assert stats.trashed_storage_bytes == 4000


def test_format_normalization_and_categorization():
    """Verify extension normalization and canonical display naming (R-LINUX-TOOLS-02)."""
    assert normalize_extension(".jpeg") == "jpg"
    assert normalize_extension(".JPG") == "jpg"
    assert normalize_extension(".heif") == "heic"
    assert normalize_extension(".TIFF") == "tif"
    assert normalize_extension(".ARW") == "arw"

    photos = [
        CandidatePhoto(primary_path=Path("/tmp/1.jpeg")),
        CandidatePhoto(primary_path=Path("/tmp/2.jpg")),
        CandidatePhoto(primary_path=Path("/tmp/3.arw")),
        CandidatePhoto(primary_path=Path("/tmp/4.heic")),
    ]
    for p in photos:
        p.file_size = 100

    stats = calculate_library_statistics(photos)
    assert stats.total_photo_count == 4

    names = {f.canonical_name: f.count for f in stats.format_statistics}
    assert names["JPEG"] == 2
    assert names["Sony ARW"] == 1
    assert names["Apple HEIC"] == 1

    pct_sum = sum(f.percentage_of_total for f in stats.format_statistics)
    assert round(pct_sum, 1) == 100.0


def test_quality_and_focus_distribution():
    """Verify focus category distribution and top sharpest ranking (R-LINUX-TOOLS-02)."""
    def make_scored(name: str, sharp: float):
        p = CandidatePhoto(primary_path=Path(f"/tmp/{name}"))
        p.scores = QualityScores(sharpness=sharp, noise=1.0, highlight_clipping=0.0, shadow_clipping=0.0)
        return p

    p1 = make_scored("blurry1.jpg", 20.0)    # Blurry
    p2 = make_scored("accept1.jpg", 50.0)    # Acceptable
    p3 = make_scored("sharp1.jpg", 80.0)     # Sharp
    p4 = make_scored("sharp2.jpg", 95.0)     # Sharp
    p5 = CandidatePhoto(primary_path=Path("/tmp/uncomputed.jpg"))  # Omitted from score average!

    stats = calculate_library_statistics([p1, p2, p3, p4, p5])
    assert stats.focus_distribution["blurry"] == 1
    assert stats.focus_distribution["acceptable"] == 1
    assert stats.focus_distribution["sharp"] == 2

    # Mean of (20 + 50 + 80 + 95) / 4 = 245 / 4 = 61.25 -> 61.2
    assert stats.average_sharpness == 61.2

    # Top sharpest list ordered descending
    top = stats.top_sharpest_photos
    assert len(top) == 4
    assert top[0].scores.sharpness == 95.0
    assert top[1].scores.sharpness == 80.0


def test_optical_exif_histograms():
    """Verify histogram binning for optical parameters (R-LINUX-TOOLS-02, R-LINUX-META-03)."""
    def make_with_exif(fl=None, ap=None, iso=None, ss=None):
        p = CandidatePhoto(primary_path=Path("/tmp/photo.jpg"))
        p.exif = ExposureMetadata(focal_length=fl, aperture=ap, iso=iso, shutter_speed=ss)
        return p

    photos = [
        make_with_exif(fl=16.0, ap=1.4, iso=100, ss=0.0005),   # Ultrawide, <f/2, Base ISO, Action SS (1/2000s)
        make_with_exif(fl=28.0, ap=2.8, iso=400, ss=0.002),    # Wide, f/2.8-f/4, Handheld fast (1/500s)
        make_with_exif(fl=50.0, ap=4.0, iso=1600, ss=0.01),    # Standard, f/4-f/5.6, Handheld normal (1/100s)
        make_with_exif(fl=85.0, ap=8.0, iso=6400, ss=0.1),     # Telephoto, >=f/8.0, High ISO, Slow handheld (1/10s)
        make_with_exif(fl=300.0, ap=11.0, iso=12800, ss=2.0),  # Super-tele, >=f/8.0, >6400, Long exp (2s)
    ]

    stats = calculate_library_statistics(photos)

    # Focal length histogram
    assert stats.focal_length_histogram["< 24mm"] == 1
    assert stats.focal_length_histogram["24-34mm"] == 1
    assert stats.focal_length_histogram["35-69mm"] == 1
    assert stats.focal_length_histogram["70-199mm"] == 1
    assert stats.focal_length_histogram[">= 200mm"] == 1

    # Aperture histogram
    assert stats.aperture_histogram["< f/2.0"] == 1
    assert stats.aperture_histogram["f/2.8-f/4.0"] == 1
    assert stats.aperture_histogram["f/4.0-f/5.6"] == 1
    assert stats.aperture_histogram[">= f/8.0"] == 2

    # ISO histogram
    assert stats.iso_histogram["ISO 50-100"] == 1
    assert stats.iso_histogram["ISO 101-400"] == 1
    assert stats.iso_histogram["ISO 401-1600"] == 1
    assert stats.iso_histogram["ISO 1601-6400"] == 1
    assert stats.iso_histogram["> ISO 6400"] == 1

    # Shutter speed histogram
    assert stats.shutter_speed_histogram["< 1/1000s"] == 1
    assert stats.shutter_speed_histogram["1/1000s-1/250s"] == 1
    assert stats.shutter_speed_histogram["1/250s-1/60s"] == 1
    assert stats.shutter_speed_histogram["1/60s-1/2s"] == 1
    assert stats.shutter_speed_histogram[">= 1/2s"] == 1
