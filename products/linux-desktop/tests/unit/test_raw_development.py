"""Unit tests for the display-size rule and the LibRaw development fallback of ``image_source``.

Fakes stand in for GExiv2's preview extraction and for ``dcraw_emu``, so these run everywhere;
test_format_conformance.py proves the same path against real camera files.

Adheres to:
- R-LINUX-META-01: Supported File Formats
"""

from __future__ import annotations

import io
import subprocess
from pathlib import Path

import pytest
from PIL import Image

from photo_selector_linux.core import image_source
from photo_selector_linux.core.image_source import (
    DisplaySource,
    decode_source,
    develop_raw,
    read_display_source,
)
from photo_selector_linux.ui import photo_loader


def _jpeg(size) -> bytes:
    buf = io.BytesIO()
    Image.new("RGB", size, (200, 80, 40)).save(buf, "JPEG")
    return buf.getvalue()


def _ppm(size) -> bytes:
    buf = io.BytesIO()
    Image.new("RGB", size, (10, 20, 30)).save(buf, "PPM")
    return buf.getvalue()


def _developed(size) -> DisplaySource:
    return DisplaySource(b"\x00" * (size[0] * size[1] * 3), None, False, rgb_size=size)


@pytest.fixture
def raw_with_preview(monkeypatch, tmp_path: Path):
    """A RAW whose embedded JPEG preview has the given long edge, native size as given."""
    def make(preview_edge, native_edge):
        preview = None
        if preview_edge:
            preview = DisplaySource(_jpeg((preview_edge, preview_edge * 2 // 3)), 1, True)
        monkeypatch.setattr(
            image_source, "_raw_preview_and_native_edge",
            lambda path, data: (preview, preview_edge, native_edge),
        )
        path = tmp_path / "PXL_0001.dng"
        path.write_bytes(b"raw")
        return path, preview
    return make


def test_large_preview_is_shown_without_developing(raw_with_preview):
    path, preview = raw_with_preview(1616, 6048)
    calls = []
    assert read_display_source(path, developer=lambda *a: calls.append(a)) is preview
    assert calls == []


def test_small_preview_falls_back_to_libraw(raw_with_preview):
    """Phone DNGs embed ~672 px previews; the display needs >= 1024 px."""
    path, _ = raw_with_preview(672, 4032)
    source = read_display_source(path, developer=lambda p, d, n: _developed((2016, 1512)))
    assert source.rgb_size == (2016, 1512) and not source.is_raw_preview


def test_raw_without_jpeg_preview_is_developed(raw_with_preview):
    path, _ = raw_with_preview(0, 4032)
    source = read_display_source(path, developer=lambda p, d, n: _developed((2016, 1512)))
    assert source.rgb_size == (2016, 1512)


def test_preview_showing_every_native_pixel_is_enough(raw_with_preview):
    path, preview = raw_with_preview(900, 900)
    assert read_display_source(path, developer=lambda *a: pytest.fail("developed")) is preview


def test_small_preview_is_best_effort_without_libraw(raw_with_preview):
    path, preview = raw_with_preview(672, 4032)
    assert read_display_source(path, developer=lambda *a: None) is preview


def test_developer_receives_display_name_and_buffer(raw_with_preview):
    """GVFS: the on-disk name is an opaque ID; the downloaded bytes are reused."""
    path, _ = raw_with_preview(0, 4032)
    seen = []
    read_display_source(path, "DSC0001.ARW", b"bytes", developer=lambda p, d, n: seen.append((d, n)))
    assert seen == [(b"bytes", "DSC0001.ARW")]


class _FakeRun:
    def __init__(self, stdout=b"", returncode=0, raises=None):
        self.stdout, self.returncode, self.raises = stdout, returncode, raises
        self.argv = None
        self.input_bytes = None

    def __call__(self, argv, **kwargs):
        self.argv = argv
        self.input_bytes = Path(argv[-1]).read_bytes() if Path(argv[-1]).exists() else None
        if self.raises:
            raise self.raises
        return subprocess.CompletedProcess(argv, self.returncode, stdout=self.stdout)


def test_develop_raw_reads_dcraw_emu_ppm_from_stdout(tmp_path: Path):
    run = _FakeRun(stdout=_ppm((2016, 1512)))
    source = develop_raw(tmp_path / "PXL.dng", runner=run, decoder="/usr/bin/dcraw_emu")
    assert source.rgb_size == (2016, 1512)
    assert len(source.data) == 2016 * 1512 * 3
    assert run.argv == ["/usr/bin/dcraw_emu", "-h", "-w", "-Z", "-", str(tmp_path / "PXL.dng")]


def test_develop_raw_uses_a_temporary_copy_of_in_memory_data(tmp_path: Path):
    run = _FakeRun(stdout=_ppm((8, 6)))
    develop_raw(tmp_path / "1aW85w", data=b"raw-bytes", name="DSC1.ARW", runner=run, decoder="d")
    assert run.input_bytes == b"raw-bytes"
    assert run.argv[-1].endswith(".arw")
    assert not Path(run.argv[-1]).exists()  # cleaned up


def test_develop_raw_failures_return_none(tmp_path: Path, monkeypatch):
    raw = tmp_path / "x.dng"
    assert develop_raw(raw, runner=_FakeRun(returncode=1, stdout=b"x"), decoder="d") is None
    assert develop_raw(raw, runner=_FakeRun(stdout=b""), decoder="d") is None
    assert develop_raw(raw, runner=_FakeRun(stdout=b"garbage"), decoder="d") is None
    assert develop_raw(raw, runner=_FakeRun(raises=subprocess.TimeoutExpired("d", 1)), decoder="d") is None
    assert develop_raw(raw, runner=_FakeRun(raises=OSError("gone")), decoder="d") is None
    monkeypatch.setattr(image_source.shutil, "which", lambda name: None)
    assert develop_raw(raw, runner=_FakeRun(stdout=_ppm((4, 4)))) is None


def test_decode_source_handles_developed_pixels_and_orientation():
    assert decode_source(_developed((30, 20))).size == (30, 20)
    rotated = decode_source(DisplaySource(_jpeg((40, 30)), 6, True))
    assert rotated.size == (30, 40)
    assert decode_source(DisplaySource(b"not an image", None, False)) is None


@pytest.mark.skipif(
    not (image_source.HAS_GEXIV2 and photo_loader.HAS_GI), reason="needs real GdkPixbuf and GTK 4 typelibs"
)
def test_developed_pixels_decode_to_a_downscaled_pixbuf():
    upright = photo_loader.decode_pixbuf(_developed((40, 30)))
    small = photo_loader.decode_pixbuf(_developed((40, 30)), max_edge=20)
    assert (upright.get_width(), upright.get_height()) == (40, 30)
    assert (small.get_width(), small.get_height()) == (20, 15)
