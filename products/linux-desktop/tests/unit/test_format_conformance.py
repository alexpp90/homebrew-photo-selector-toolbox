"""Format conformance: the Linux Desktop against the shared format contract.

Reads ``testdata/formats/manifest.json`` (docs/shared/SUPPORTED_FORMATS.md). The contract part —
extensions, RAW classification, discovery and companion pairing — and the committed bitmaps always
run. The RAW corpus part runs when the corpus is downloaded
(``python3 scripts/fetch_format_corpus.py``) and skips otherwise. With
``PST_REQUIRE_FORMAT_CORPUS=1`` (CI, and ``run_tests.sh`` once the corpus is downloaded) a missing
corpus fails instead of skipping; on Linux — the target platform and CI — so does a missing Debian
decoding backend (GExiv2, LibRaw ``dcraw_emu``, a HEIF decoder). Elsewhere (a macOS Poetry venv)
those backend-dependent checks skip with the package to install.

Adheres to:
- R-LINUX-META-01: Supported File Formats
- R-LINUX-META-02: Standardized ExposureMetadata Data Model
- R-LINUX-META-04: Companion RAW+JPEG, Sidecar & Edit Binding
"""

from __future__ import annotations

import json
import math
import os
import sys
from pathlib import Path
from typing import Callable, Dict, Tuple

import pytest

from photo_selector_linux.core import metadata as metadata_mod
from photo_selector_linux.core.formats import (
    BITMAP_EXTENSIONS,
    RAW_EXTENSIONS,
    RECOGNIZED_RAW_EXTENSIONS,
    REQUIRED_RAW_EXTENSIONS,
    is_raw,
)
from photo_selector_linux.core.image_source import (
    decode_source,
    heif_decoder_available,
    raw_developer_path,
    read_display_source,
)
from photo_selector_linux.ui import photo_loader
from photo_selector_linux.core.metadata import read_metadata
from photo_selector_linux.core.scanner import DirectoryScanner
from photo_selector_linux.core.scoring import compute_quality_scores

REPO_ROOT = Path(__file__).resolve().parents[4]
FORMATS_DIR = REPO_ROOT / "testdata" / "formats"
MANIFEST = json.loads((FORMATS_DIR / "manifest.json").read_text(encoding="utf-8"))
FORMATS = {f["id"]: f for f in MANIFEST["formats"]}
SAMPLES = MANIFEST["samples"]
MIN_LONG_EDGE = MANIFEST["display"]["min_long_edge_px"]
REL_TOL = MANIFEST["exif_tolerance"]["relative"]

REQUIRE = os.environ.get(MANIFEST["corpus"]["require_env"], "").strip() == "1"
REQUIRE_BACKENDS = REQUIRE and sys.platform.startswith("linux")
_cache_env = os.environ.get(MANIFEST["corpus"]["cache_env"], "").strip()
CORPUS_DIR = Path(_cache_env or MANIFEST["corpus"]["default_cache_dir"]).expanduser()

# Capabilities this product genuinely cannot provide, keyed by (sample id, capability) with the
# reason. Strict: an entry that starts passing fails the test until it is removed.
KNOWN_GAPS: Dict[Tuple[str, str], str] = {}


def _missing(reason: str, required: bool) -> None:
    if required:
        pytest.fail(f"{reason} (PST_REQUIRE_FORMAT_CORPUS=1)")
    pytest.skip(reason)


def _sample_path(sample: dict) -> Path:
    if sample["source"] == "repo":
        return FORMATS_DIR / sample["file"]
    path = CORPUS_DIR / sample["file"]
    if not path.is_file():
        _missing(f"format corpus not downloaded ({path}); run: python3 scripts/fetch_format_corpus.py", REQUIRE)
    return path


def _check(sample_id: str, capability: str, check: Callable[[], None]) -> None:
    gap = KNOWN_GAPS.get((sample_id, capability))
    if gap is None:
        check()
        return
    try:
        check()
    except AssertionError:
        pytest.xfail(f"known gap: {gap}")
    pytest.fail(f"known gap ({sample_id}, {capability}) now passes — remove it from KNOWN_GAPS")


def _require_backends(sample: dict, capability: str) -> None:
    fmt = FORMATS[sample["format"]]
    if capability == "exif":
        # Pillow reads EXIF of JPEG/PNG/TIFF/WebP; RAW and HEIF need exiv2.
        if (fmt["kind"] == "raw" or fmt["id"] == "heif") and not metadata_mod._HAVE_GEXIV2:
            _missing("GExiv2 not available (Debian: gir1.2-gexiv2-0.10)", REQUIRE_BACKENDS)
    if capability in ("display", "score"):
        if fmt["kind"] == "raw" and not metadata_mod._HAVE_GEXIV2:
            _missing("GExiv2 not available for RAW previews (Debian: gir1.2-gexiv2-0.10)", REQUIRE_BACKENDS)
        if fmt["id"] == "heif" and not heif_decoder_available():
            _missing("no HEIF decoder (Debian: heif-gdk-pixbuf; pip: pillow-heif)", REQUIRE_BACKENDS)
        if fmt["kind"] == "raw" and raw_developer_path() is None:
            _missing("LibRaw dcraw_emu not installed (Debian: libraw-bin)", REQUIRE_BACKENDS)


def image_source_has_typelibs() -> bool:
    """Real PyGObject (not the conftest stubs): GExiv2 loaded for real."""
    return metadata_mod._HAVE_GEXIV2


def _close(actual, expected) -> bool:
    return actual is not None and math.isclose(actual, expected, rel_tol=REL_TOL)


def _norm(text):
    return (text or "").strip().casefold()


# ── Contract: extensions and classification ──────────────────────────────────────────────


def _extensions(kind: str, tiers=("required", "recognized")) -> set:
    return {
        ext
        for f in MANIFEST["formats"]
        if f["kind"] == kind and f["tier"] in tiers
        for ext in f["extensions"]
    }


def test_raw_extensions_match_the_contract():
    assert RAW_EXTENSIONS == _extensions("raw")
    assert REQUIRED_RAW_EXTENSIONS == _extensions("raw", tiers=("required",))
    assert RECOGNIZED_RAW_EXTENSIONS == _extensions("raw", tiers=("recognized",))


def test_bitmap_extensions_match_the_contract():
    assert BITMAP_EXTENSIONS == _extensions("bitmap")


@pytest.mark.parametrize("ext", sorted(_extensions("raw") | _extensions("bitmap")))
def test_classification_is_case_insensitive(ext):
    expected = ext in _extensions("raw")
    assert is_raw(f"DSC00001.{ext}") is expected
    assert is_raw(f"DSC00001.{ext.upper()}") is expected


@pytest.mark.parametrize("fmt_id", sorted(f["id"] for f in MANIFEST["formats"] if f["kind"] == "raw"))
@pytest.mark.parametrize("companion", ["JPG", "jpeg", "HEIC", "heif"])
def test_raw_is_discovered_and_paired_with_its_companion(tmp_path: Path, fmt_id: str, companion: str):
    for ext in FORMATS[fmt_id]["extensions"]:
        folder = tmp_path / ext
        folder.mkdir()
        raw = folder / f"DSC00001.{ext.upper()}"
        raw.write_bytes(b"raw")
        (folder / f"DSC00001.{companion}").write_bytes(b"companion")
        (folder / "DSC00002.jpg").write_bytes(b"lone jpeg")

        candidates = DirectoryScanner().scan_directory(folder)

        assert [c.primary_path.name for c in candidates] == [raw.name, "DSC00002.jpg"]
        assert [p.name for p in candidates[0].companion_paths] == [f"DSC00001.{companion}"]


@pytest.mark.parametrize("ext", sorted(_extensions("bitmap")))
def test_bitmap_is_discovered_in_either_case(tmp_path: Path, ext: str):
    (tmp_path / f"a.{ext}").write_bytes(b"x")
    (tmp_path / f"b.{ext.upper()}").write_bytes(b"x")
    names = [c.primary_path.name for c in DirectoryScanner().scan_directory(tmp_path)]
    assert names == [f"a.{ext}", f"b.{ext.upper()}"]


# ── Corpus: every sample is displayed, its EXIF read and it is scored ───────────────────


@pytest.mark.parametrize("sample", SAMPLES, ids=[s["id"] for s in SAMPLES])
def test_sample_is_displayed_at_full_display_size(sample: dict):
    path = _sample_path(sample)
    _require_backends(sample, "display")
    native = sample["expect"]["long_edge_px"]
    required = min(MIN_LONG_EDGE, native)

    def check():
        # The canvas path: image_source picks the bytes, photo_loader decodes them (real GTK in
        # CI); decode_source decodes the same bytes with Pillow/GdkPixbuf elsewhere.
        source = read_display_source(path)
        assert source is not None, f"{path.name}: no display source"
        if photo_loader.HAS_GI and image_source_has_typelibs():
            pixbuf = photo_loader.decode_pixbuf(source)
            assert pixbuf is not None, f"{path.name}: GdkPixbuf could not decode the source"
            size = (pixbuf.get_width(), pixbuf.get_height())
        else:
            image = decode_source(source)
            assert image is not None, f"{path.name}: the display source does not decode"
            size = image.size
        long_edge = max(size)
        kind = "LibRaw development" if source.rgb_size else "preview" if source.is_raw_preview else "file"
        assert long_edge >= required, f"{path.name}: {kind} long edge {long_edge} < {required}"
        assert long_edge <= native + 1, f"{path.name}: larger than the native image"

    _check(sample["id"], "display", check)


@pytest.mark.parametrize("sample", SAMPLES, ids=[s["id"] for s in SAMPLES])
def test_sample_exif_matches_the_camera(sample: dict):
    path = _sample_path(sample)
    _require_backends(sample, "exif")
    expect = sample["expect"]

    def check():
        meta = read_metadata(path)
        assert _norm(meta.camera_make) == _norm(expect["make"])
        assert _norm(meta.camera_model) == _norm(expect["model"])
        assert meta.iso == expect["iso"]
        assert _close(meta.shutter_speed, expect["exposure_time_s"]), meta.shutter_speed
        assert _close(meta.aperture, expect["f_number"]), meta.aperture
        assert _close(meta.focal_length, expect["focal_length_mm"]), meta.focal_length
        if "lens" in expect:
            assert _norm(meta.lens) == _norm(expect["lens"])
        # Padding must never reach the screen (Pentax, Olympus).
        for text in (meta.camera_make, meta.camera_model, meta.lens):
            assert text is None or text == text.strip()

    _check(sample["id"], "exif", check)


@pytest.mark.parametrize("sample", SAMPLES, ids=[s["id"] for s in SAMPLES])
def test_sample_is_scored(sample: dict):
    path = _sample_path(sample)
    _require_backends(sample, "score")

    def check():
        scores = compute_quality_scores(path)
        assert scores is not None, f"{path.name}: not scored"
        assert 0.0 <= scores.sharpness <= 100.0
        assert 0.0 <= scores.highlight_clipping <= 100.0

    _check(sample["id"], "score", check)


def test_linux_has_every_decoding_backend():
    """On Linux CI the Debian runtime backends must all be present, or the corpus tests prove nothing."""
    if not REQUIRE_BACKENDS:
        pytest.skip("only enforced on Linux with PST_REQUIRE_FORMAT_CORPUS=1")
    assert metadata_mod._HAVE_GEXIV2, "GExiv2 missing (gir1.2-gexiv2-0.10)"
    assert raw_developer_path(), "dcraw_emu missing (libraw-bin)"
    assert heif_decoder_available(), "HEIF decoder missing (heif-gdk-pixbuf)"
