"""Unit tests for Linux Desktop data models and optical formatting.

Adheres to:
- R-LINUX-META-02: ExposureMetadata model
- R-LINUX-META-03: Exposure formatting and Zero-Placeholder Invariant
- R-LINUX-SCORE-03: Sharpness categories
- R-LINUX-CULL-06: CullingRecord audit tracking
"""

from pathlib import Path

from photo_selector_linux.core.models import (
    CandidatePhoto,
    CullingActionType,
    CullingRecord,
    ExposureMetadata,
    PhotoStatus,
    QualityScores,
    SharpnessCategory,
)


def test_exposure_metadata_defaults():
    """Verify default values and optionality (R-LINUX-META-02)."""
    meta = ExposureMetadata()
    assert meta.shutter_speed is None
    assert meta.aperture is None
    assert meta.iso is None
    assert meta.focal_length is None
    assert meta.lens is None
    assert meta.camera_make is None
    assert meta.camera_model is None
    assert meta.is_fallback is False
    assert meta.formatted_summary == ""


def test_shutter_speed_formatting_fractions():
    """Verify fractional shutter speed formatting for durations < 1.0s (R-LINUX-META-03)."""
    assert ExposureMetadata(shutter_speed=0.004).formatted_shutter_speed == "1/250s"
    assert ExposureMetadata(shutter_speed=0.0005).formatted_shutter_speed == "1/2000s"
    assert ExposureMetadata(shutter_speed=0.001).formatted_shutter_speed == "1/1000s"
    assert ExposureMetadata(shutter_speed=0.5).formatted_shutter_speed == "1/2s"
    assert ExposureMetadata(shutter_speed=0.25).formatted_shutter_speed == "1/4s"


def test_shutter_speed_formatting_integers_and_decimals():
    """Verify shutter speed formatting for durations >= 1.0s (R-LINUX-META-03)."""
    assert ExposureMetadata(shutter_speed=1.0).formatted_shutter_speed == "1s"
    assert ExposureMetadata(shutter_speed=2.0).formatted_shutter_speed == "2s"
    assert ExposureMetadata(shutter_speed=1.5).formatted_shutter_speed == "1.5s"
    assert ExposureMetadata(shutter_speed=30.0).formatted_shutter_speed == "30s"


def test_shutter_speed_division_by_zero_and_bulb_protection():
    """Verify division-by-zero, non-finite, and bulb mode protections (R-LINUX-META-03)."""
    assert ExposureMetadata(shutter_speed=0.0).formatted_shutter_speed is None
    assert ExposureMetadata(shutter_speed=-1.0).formatted_shutter_speed is None
    assert ExposureMetadata(shutter_speed=float("nan")).formatted_shutter_speed is None
    assert ExposureMetadata(shutter_speed=float("inf")).formatted_shutter_speed is None
    assert ExposureMetadata(shutter_speed=float("-inf")).formatted_shutter_speed is None
    assert ExposureMetadata(shutter_speed=None).formatted_shutter_speed is None


def test_zero_placeholder_invariant():
    """Verify zero-placeholder invariant: missing tags are completely omitted (R-LINUX-META-03)."""
    meta_empty = ExposureMetadata()
    assert meta_empty.formatted_summary == ""
    assert "Unknown" not in meta_empty.formatted_summary
    assert "N/A" not in meta_empty.formatted_summary

    meta_partial = ExposureMetadata(shutter_speed=0.004, iso=400)
    summary = meta_partial.formatted_summary
    assert summary == "1/250s · ISO 400"
    assert "f/" not in summary
    assert "mm" not in summary

    meta_full = ExposureMetadata(
        shutter_speed=0.0005,
        aperture=1.4,
        iso=100,
        focal_length=85.0,
    )
    assert meta_full.formatted_summary == "1/2000s · f/1.4 · ISO 100 · 85mm"


def test_sharpness_categories():
    """Verify sharpness thresholds and classification categories (R-LINUX-SCORE-03)."""
    assert SharpnessCategory.from_score(0.0) == SharpnessCategory.BLURRY
    assert SharpnessCategory.from_score(34.9) == SharpnessCategory.BLURRY
    assert SharpnessCategory.from_score(35.0) == SharpnessCategory.ACCEPTABLE
    assert SharpnessCategory.from_score(69.9) == SharpnessCategory.ACCEPTABLE
    assert SharpnessCategory.from_score(70.0) == SharpnessCategory.SHARP
    assert SharpnessCategory.from_score(100.0) == SharpnessCategory.SHARP

    scores = QualityScores(
        sharpness=82.5,
        noise=1.2,
        highlight_clipping=0.5,
        shadow_clipping=1.1,
    )
    assert scores.focus_category == SharpnessCategory.SHARP
    assert scores.formatted_sharpness == "82.5"
    assert scores.formatted_noise == "1.2"
    assert scores.formatted_highlight_clipping == "0.5%"
    assert scores.formatted_shadow_clipping == "1.1%"


def test_candidate_photo_properties():
    """Verify CandidatePhoto stem, filename, and companion binding resolution."""
    primary = Path("/media/sdcard/DCIM/DSC0001.ARW")
    comp_jpeg = Path("/media/sdcard/DCIM/DSC0001.JPG")
    comp_xmp = Path("/media/sdcard/DCIM/DSC0001.xmp")

    photo = CandidatePhoto(
        primary_path=primary,
        companion_paths=[comp_jpeg, comp_xmp],
    )

    assert photo.filename == "DSC0001.ARW"
    assert photo.stem == "DSC0001"
    assert len(photo.all_paths) == 3
    assert photo.primary_path in photo.all_paths
    assert photo.companion_path == comp_jpeg
    assert photo.sidecar_path == comp_xmp
    assert photo.status == PhotoStatus.CANDIDATE


def test_culling_record_creation():
    """Verify CullingRecord audit record properties (R-LINUX-CULL-06/07)."""
    src = Path("/media/sdcard/DCIM/DSC0001.ARW")
    dst = Path("/media/sdcard/DCIM/Selection/DSC0001.ARW")

    record = CullingRecord(
        photo_id="test-123",
        action_type=CullingActionType.MOVE,
        original_primary_path=src,
        affected_paths=[(src, dst)],
        previous_status=PhotoStatus.CANDIDATE,
    )

    assert record.photo_id == "test-123"
    assert record.action_type == CullingActionType.MOVE
    assert record.original_primary_path == src
    assert record.affected_paths == [(src, dst)]
    assert record.previous_status == PhotoStatus.CANDIDATE
    assert record.timestamp > 0.0
