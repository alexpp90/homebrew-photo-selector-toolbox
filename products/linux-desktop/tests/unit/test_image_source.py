"""Unit tests for RAW display sources, orientation and texture sizing.

Adheres to:
- R-LINUX-META-01: Supported File Formats (RAW display through the embedded preview)
"""

import io
from pathlib import Path

import pytest
from PIL import Image, ImageOps

from photo_selector_linux.core import image_source
from photo_selector_linux.core.image_source import (
    is_raw_name,
    orientation_ops,
    read_display_source,
)
from photo_selector_linux.ui.photo_loader import fit_size

needs_gexiv2 = pytest.mark.skipif(not image_source.HAS_GEXIV2, reason="GExiv2 typelib not available")


def _jpeg_bytes(size, color) -> bytes:
    buf = io.BytesIO()
    Image.new("RGB", size, color).save(buf, format="JPEG")
    return buf.getvalue()


def _write_synthetic_raw(path: Path, orientation: int = 1) -> bytes:
    """A TIFF-structured RAW stand-in carrying an embedded 40x30 JPEG preview."""
    from gi.repository import GExiv2

    Image.new("RGB", (64, 48), (200, 10, 10)).save(path, format="TIFF")
    preview = _jpeg_bytes((40, 30), (0, 0, 255))
    meta = GExiv2.Metadata()
    meta.open_path(str(path))
    setter = getattr(meta, "try_set_exif_thumbnail_from_buffer", None) or meta.set_exif_thumbnail_from_buffer
    setter(preview)
    meta.try_set_tag_long("Exif.Image.Orientation", orientation)
    meta.save_file(str(path))
    return preview


def test_raw_names_are_case_insensitive():
    assert is_raw_name("DSC01100.ARW")
    assert is_raw_name("img.cr3")
    assert not is_raw_name("DSC01100.JPG")
    assert not is_raw_name("1aW85wHUJbUsFMmlvGW9QR4IMNtWJGmpv")


@pytest.mark.parametrize("orientation", range(1, 9))
def test_orientation_ops_agree_with_pillow_exif_transpose(orientation: int):
    """Oracle agreement: our (flip, rotate) decomposition matches Pillow for all 8 EXIF values."""
    base = Image.new("RGB", (3, 2))
    base.putdata([(i * 40, 0, 0) for i in range(6)])  # asymmetric so every transform differs

    exif = base.getexif()
    exif[0x0112] = orientation
    tagged = io.BytesIO()
    base.save(tagged, format="PNG", exif=exif.tobytes())
    expected = ImageOps.exif_transpose(Image.open(io.BytesIO(tagged.getvalue())))

    flip, degrees = orientation_ops(orientation)
    actual = base.transpose(Image.Transpose.FLIP_LEFT_RIGHT) if flip else base
    actual = actual.rotate(degrees, expand=True) if degrees else actual  # Pillow rotates CCW

    assert actual.tobytes() == expected.tobytes()
    assert actual.size == expected.size


def test_orientation_ops_unknown_values_are_identity():
    assert orientation_ops(None) == (False, 0)
    assert orientation_ops(0) == (False, 0)
    assert orientation_ops(42) == (False, 0)


@needs_gexiv2
def test_raw_displays_its_embedded_preview_with_raw_orientation(tmp_path: Path):
    """A RAW neither GdkPixbuf nor Pillow can decode is shown through its JPEG preview."""
    raw = tmp_path / "DSC0001.ARW"
    preview = _write_synthetic_raw(raw, orientation=6)

    source = read_display_source(raw)

    assert source is not None
    assert source.is_raw_preview
    assert source.data == preview
    assert source.orientation == 6


@needs_gexiv2
def test_display_name_decides_format_for_opaque_cloud_names(tmp_path: Path):
    """On Google Drive via GVFS the on-disk name is an ID without extension."""
    raw = tmp_path / "13b8tSL4K8o-WBRrrmoddsrvhCtUgq0lw"
    preview = _write_synthetic_raw(raw)

    source = read_display_source(raw, "DSC02905.ARW")

    assert source is not None and source.is_raw_preview
    assert source.data == preview


@needs_gexiv2
def test_in_memory_buffer_gives_the_same_preview_as_the_path(tmp_path: Path):
    raw = tmp_path / "DSC0002.ARW"
    _write_synthetic_raw(raw, orientation=8)

    from_path = read_display_source(raw)
    from_buffer = read_display_source(raw, data=raw.read_bytes())

    assert from_buffer == from_path


def test_bitmaps_pass_through_with_embedded_orientation(tmp_path: Path):
    jpg = tmp_path / "DSC0003.JPG"
    jpg.write_bytes(_jpeg_bytes((8, 6), (10, 200, 10)))

    source = read_display_source(jpg)

    assert source is not None
    assert not source.is_raw_preview
    assert source.data == jpg.read_bytes()
    assert source.orientation is None


def test_missing_file_has_no_source(tmp_path: Path):
    assert read_display_source(tmp_path / "gone.jpg") is None


@pytest.mark.parametrize(
    "size, max_edge, expected",
    [
        ((6000, 4000), 3840, (3840, 2560)),
        ((4000, 6000), 3840, (2560, 3840)),
        ((1616, 1080), 3840, (1616, 1080)),  # never upscale
        ((6000, 4000), None, (6000, 4000)),  # 1:1 zoom decodes full size
    ],
)
def test_fit_size(size, max_edge, expected):
    assert fit_size(*size, max_edge) == expected


@needs_gexiv2  # proxy for real PyGObject typelibs (stub mode lacks GExiv2)
def test_raw_preview_decodes_upright_and_downscaled(tmp_path: Path):
    """End of the display path: preview bytes → GdkPixbuf rotated by the RAW orientation."""
    from photo_selector_linux.ui.photo_loader import decode_pixbuf

    raw = tmp_path / "DSC0004.ARW"
    _write_synthetic_raw(raw, orientation=6)  # 40x30 landscape preview, camera held portrait

    upright = decode_pixbuf(read_display_source(raw))
    small = decode_pixbuf(read_display_source(raw), max_edge=20)

    assert (upright.get_width(), upright.get_height()) == (30, 40)
    assert (small.get_width(), small.get_height()) == (15, 20)
