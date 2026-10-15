"""Format conformance: the legacy Desktop against the shared format contract.

The contract and its ground truth live in ``testdata/formats/manifest.json`` at the
repository root (human documentation: ``docs/shared/SUPPORTED_FORMATS.md``). Three
layers, all through the product's real code paths (no Tk):

* Contract (always runs): every extension of every format is scanned,
  case-insensitively, and every ``kind: raw`` format is classified as RAW.
* Committed bitmaps (always run): discovery, display decode, EXIF and scoring.
* Real-camera RAW corpus (runs when the corpus is present, see
  ``scripts/fetch_format_corpus.py``; ``PST_REQUIRE_FORMAT_CORPUS=1`` turns a
  missing corpus into a failure, which is how CI runs it).

EXIF is checked on two reader paths, because which one a user gets depends on
whether the ExifTool executable is installed — and the CI runners do not install
it: ``no-exiftool`` (ExifTool disabled; exifread, then Pillow — the CI path) and
``exiftool`` (the ExifTool strategy itself; skipped when the executable is
missing).

Gaps are listed in ``KNOWN_GAPS`` and enforced strictly: a gap that starts
passing fails the test until its entry is removed.
"""

from __future__ import annotations

import json
import os
import shutil
from pathlib import Path

import pytest

from photo_selector_toolbox.core.models import ExifData
from photo_selector_toolbox.core.sharpness import calculate_all_scores, find_related_files
from photo_selector_toolbox.core.utils import (
    REVIEW_PREVIEW_SIZE,
    get_exiftool_path,
    load_image_preview,
    scan_image_files,
)
from photo_selector_toolbox.exif.reader import (
    JPEG_EXTENSIONS,
    RAW_EXTENSIONS,
    SUPPORTED_EXTENSIONS,
    get_exif_data,
    is_raw_name,
    is_supported_image_name,
)
from photo_selector_toolbox.exif.readers.exiftool import ExifToolReader


def _find_formats_dir() -> Path:
    for parent in Path(__file__).resolve().parents:
        if (parent / "testdata" / "formats" / "manifest.json").is_file():
            return parent / "testdata" / "formats"
    raise RuntimeError("testdata/formats/manifest.json not found above " + __file__)


FORMATS_DIR = _find_formats_dir()
MANIFEST = json.loads((FORMATS_DIR / "manifest.json").read_text(encoding="utf-8"))
FORMATS = MANIFEST["formats"]
FORMAT_BY_ID = {f["id"]: f for f in FORMATS}
SAMPLES = MANIFEST["samples"]
REPO_SAMPLES = [s for s in SAMPLES if s["source"] == "repo"]
CORPUS_SAMPLES = [s for s in SAMPLES if s["source"] == "corpus"]
MIN_LONG_EDGE = MANIFEST["display"]["min_long_edge_px"]
REL_TOL = MANIFEST["exif_tolerance"]["relative"]

_CORPUS = MANIFEST["corpus"]
CORPUS_DIR = Path(
    os.environ.get(_CORPUS["cache_env"]) or os.path.expanduser(_CORPUS["default_cache_dir"])
)
CORPUS_REQUIRED = os.environ.get(_CORPUS["require_env"]) == "1"

# (capability, sample id) -> reason. Strict: see `_check`.
_NO_EXIFTOOL_CR3_RAF_RW2 = (
    "without the ExifTool executable only exifread and Pillow remain; exifread "
    "cannot parse this container ('File format not recognized') and Pillow cannot "
    "open it. The release builds bundle ExifTool; source installs without it hit this."
)
_NO_HEIF_DECODER = (
    "Pillow ships no HEIF decoder and pillow-heif is not a dependency of the "
    "archived legacy Desktop, so HEIC files are scanned and their EXIF read but "
    "they cannot be displayed or scored."
)
KNOWN_GAPS: dict[tuple[str, str], str] = {
    ("exif/no-exiftool", "cr3-eosm6ii-craw"): _NO_EXIFTOOL_CR3_RAF_RW2,
    ("exif/no-exiftool", "raf-xt30"): _NO_EXIFTOOL_CR3_RAF_RW2,
    ("exif/no-exiftool", "rw2-g100"): _NO_EXIFTOOL_CR3_RAF_RW2,
    ("display", "heif-sample"): _NO_HEIF_DECODER,
    ("score", "heif-sample"): _NO_HEIF_DECODER,
}

# Expect fields `ExifData` does not model: the legacy Desktop never extracts the
# camera make/model (its statistics are about exposure, focal length and lens).
# Strict as well — see test_unmodelled_exif_fields_are_really_unmodelled.
UNMODELLED_EXIF_FIELDS = {"make", "model"}

EXIF_PATHS = ["no-exiftool", "exiftool"]


def _check(capability: str, sample_id: str, check) -> None:
    """Runs `check`; a listed gap must fail (xfail), an unlisted one must pass."""
    reason = KNOWN_GAPS.get((capability, sample_id))
    if reason is None:
        check()
        return
    try:
        check()
    except AssertionError:
        pytest.xfail(f"known gap ({capability}, {sample_id}): {reason}")
    pytest.fail(
        f"({capability}, {sample_id}) is listed in KNOWN_GAPS but now passes; "
        "remove the entry."
    )


def _corpus_missing() -> list[str]:
    return [s["file"] for s in CORPUS_SAMPLES if not (CORPUS_DIR / s["file"]).is_file()]


@pytest.fixture(scope="module")
def corpus_dir() -> Path:
    missing = _corpus_missing()
    if missing:
        msg = (
            f"format corpus incomplete in {CORPUS_DIR} (missing {len(missing)}: "
            f"{', '.join(missing[:3])}...); run `python3 scripts/fetch_format_corpus.py` "
            f"or point {_CORPUS['cache_env']} at it"
        )
        if CORPUS_REQUIRED:
            pytest.fail(msg + f" ({_CORPUS['require_env']}=1)")
        pytest.skip(msg)
    return CORPUS_DIR


def _place(src: Path, dest: Path) -> Path:
    """Symlinks `src` to `dest` (copies where symlinks are not permitted)."""
    dest.parent.mkdir(parents=True, exist_ok=True)
    try:
        os.symlink(src, dest)
    except (OSError, NotImplementedError):
        shutil.copyfile(src, dest)
    return dest


def _swap_case(ext: str) -> str:
    return ext.upper() if ext == ext.lower() else ext.lower()


# ── Contract ──────────────────────────────────────────────────────────


def test_extension_sets_equal_the_contract():
    contract_all = {"." + e for f in FORMATS for e in f["extensions"]}
    contract_raw = {"." + e for f in FORMATS if f["kind"] == "raw" for e in f["extensions"]}
    assert set(RAW_EXTENSIONS) == contract_raw
    assert set(SUPPORTED_EXTENSIONS) == contract_all
    assert JPEG_EXTENSIONS <= SUPPORTED_EXTENSIONS - RAW_EXTENSIONS


@pytest.mark.parametrize("fmt", FORMATS, ids=lambda f: f["id"])
def test_every_extension_is_scanned_case_insensitively(fmt):
    for ext in fmt["extensions"]:
        mixed = ext[0].upper() + ext[1:].lower()
        for name in (f"IMG_0001.{ext.lower()}", f"IMG_0001.{ext.upper()}", f"IMG_0001.{mixed}"):
            assert is_supported_image_name(name), name
            assert is_raw_name(name) == (fmt["kind"] == "raw"), name
        # macOS AppleDouble companions carry the extension but no image.
        assert not is_supported_image_name(f"._IMG_0001.{ext}")


@pytest.mark.parametrize(
    "fmt", [f for f in FORMATS if f["kind"] == "raw"], ids=lambda f: f["id"]
)
def test_every_raw_extension_pairs_with_its_jpeg(tmp_path, fmt):
    """Companion grouping is name-based, so placeholder files exercise it for every RAW."""
    for ext in fmt["extensions"]:
        folder = tmp_path / ext
        folder.mkdir()
        raw = folder / f"DSC0001.{ext.upper()}"
        jpeg = folder / "DSC0001.JPG"
        raw.write_bytes(b"\0")
        jpeg.write_bytes(b"\0")
        (folder / "DSC0002.JPG").write_bytes(b"\0")
        assert set(find_related_files(raw)) == {raw, jpeg}
        assert set(find_related_files(jpeg)) == {raw, jpeg}
        assert sorted(scan_image_files(folder, frozenset())) == sorted(
            [raw, jpeg, folder / "DSC0002.JPG"]
        )


def test_scanner_prunes_excluded_folders_and_appledouble(tmp_path):
    (tmp_path / "Selection").mkdir()
    (tmp_path / "Selection" / "a.jpg").write_bytes(b"\0")
    (tmp_path / "._b.jpg").write_bytes(b"\0")
    (tmp_path / "notes.txt").write_bytes(b"\0")
    (tmp_path / "c.Nrw").write_bytes(b"\0")
    assert scan_image_files(tmp_path, frozenset({"selection"})) == [tmp_path / "c.Nrw"]


def test_unmodelled_exif_fields_are_really_unmodelled():
    fields = set(ExifData.__dataclass_fields__)
    now_modelled = UNMODELLED_EXIF_FIELDS & fields
    assert not now_modelled, (
        f"ExifData now models {now_modelled}; compare them in _exif_mismatches and "
        "remove them from UNMODELLED_EXIF_FIELDS"
    )
    expected = {k for s in SAMPLES for k in s["expect"]} - {"long_edge_px"}
    assert expected - UNMODELLED_EXIF_FIELDS == {
        "iso", "exposure_time_s", "f_number", "focal_length_mm", "lens",
    }


# ── Shared per-sample checks ──────────────────────────────────────────


def _exif_mismatches(data: ExifData | None, expect: dict) -> list[str]:
    if data is None:
        return ["no EXIF read"]
    problems = []
    if data.iso is None or float(data.iso) != float(expect["iso"]):
        problems.append(f"iso {data.iso!r} != {expect['iso']}")
    for attr, key in (
        ("shutter_speed", "exposure_time_s"),
        ("aperture", "f_number"),
        ("focal_length", "focal_length_mm"),
    ):
        got = getattr(data, attr)
        want = expect[key]
        if got is None or abs(got - want) > REL_TOL * want:
            problems.append(f"{attr} {got!r} != {want}")
    if "lens" in expect and (data.lens or "").strip().lower() != expect["lens"].strip().lower():
        problems.append(f"lens {data.lens!r} != {expect['lens']!r}")
    return problems


def _read_exif(path: Path, reader_path: str, monkeypatch) -> ExifData | None:
    if reader_path == "exiftool":
        if get_exiftool_path() is None:
            pytest.skip("ExifTool executable not installed (the CI runners exercise the no-exiftool path)")
        reader = ExifToolReader()
        # Formats ExifTool is not routed to (JPEG, TIFF) go through the chain.
        return reader.read(path) if reader.can_handle(path) else get_exif_data(path)
    monkeypatch.setattr(ExifToolReader, "can_handle", lambda self, p: False)
    return get_exif_data(path)


def _check_exif(sample: dict, path: Path, reader_path: str, monkeypatch) -> None:
    data = _read_exif(path, reader_path, monkeypatch)

    def check():
        problems = _exif_mismatches(data, sample["expect"])
        assert not problems, f"{sample['id']} via {reader_path}: {problems}"

    _check(f"exif/{reader_path}", sample["id"], check)


def _check_display(sample: dict, path: Path) -> None:
    target = min(MIN_LONG_EDGE, sample["expect"]["long_edge_px"])

    def check():
        img = load_image_preview(path, max_size=REVIEW_PREVIEW_SIZE)
        assert img is not None, f"{sample['id']}: no image decoded"
        try:
            assert img.mode == "RGB"
            assert max(img.size) >= target, f"{sample['id']}: {img.size} < {target}"
        finally:
            img.close()

    _check("display", sample["id"], check)


def _check_score(sample: dict, path: Path) -> None:
    def check():
        scores = calculate_all_scores(path, tools={"sharpness": True, "noise": True})
        # A failed decode yields 0.0, which is no measurement at all.
        assert isinstance(scores.get("sharpness"), float) and scores["sharpness"] > 0, scores
        assert isinstance(scores.get("noise"), float) and scores["noise"] > 0, scores

    _check("score", sample["id"], check)


def _check_discovery(sample: dict, path: Path, tmp_path: Path) -> None:
    stem, ext = os.path.splitext(path.name)
    as_is = _place(path, tmp_path / "as-is" / path.name)
    # A separate folder: case-insensitive file systems would merge the two names.
    swapped = _place(path, tmp_path / "swapped" / (stem + _swap_case(ext)))
    found = set(scan_image_files(tmp_path, frozenset()))
    assert found == {as_is, swapped}


# ── Committed bitmaps ─────────────────────────────────────────────────


def _repo_path(sample: dict) -> Path:
    return FORMATS_DIR / sample["file"]


@pytest.mark.parametrize("sample", REPO_SAMPLES, ids=lambda s: s["id"])
def test_bitmap_discovered(sample, tmp_path):
    _check_discovery(sample, _repo_path(sample), tmp_path)


@pytest.mark.parametrize("sample", REPO_SAMPLES, ids=lambda s: s["id"])
def test_bitmap_display(sample):
    _check_display(sample, _repo_path(sample))


@pytest.mark.parametrize("reader_path", EXIF_PATHS)
@pytest.mark.parametrize("sample", REPO_SAMPLES, ids=lambda s: s["id"])
def test_bitmap_exif(sample, reader_path, monkeypatch):
    _check_exif(sample, _repo_path(sample), reader_path, monkeypatch)


@pytest.mark.parametrize("sample", REPO_SAMPLES, ids=lambda s: s["id"])
def test_bitmap_score(sample):
    _check_score(sample, _repo_path(sample))


# ── Real-camera RAW corpus ────────────────────────────────────────────


@pytest.mark.parametrize("sample", CORPUS_SAMPLES, ids=lambda s: s["id"])
def test_raw_discovered(sample, corpus_dir, tmp_path):
    _check_discovery(sample, corpus_dir / sample["file"], tmp_path)


@pytest.mark.parametrize("sample", CORPUS_SAMPLES, ids=lambda s: s["id"])
def test_raw_pairs_with_jpeg(sample, corpus_dir, tmp_path):
    src = corpus_dir / sample["file"]
    stem = os.path.splitext(src.name)[0]
    raw = _place(src, tmp_path / src.name)
    jpeg = _place(FORMATS_DIR / "bitmaps" / "sample.jpg", tmp_path / f"{stem}.JPG")
    assert set(find_related_files(raw)) == {raw, jpeg}
    assert set(find_related_files(jpeg)) == {raw, jpeg}
    # The RAW is the primary: it is what goes to the Selection's RAW/ folder.
    assert is_raw_name(raw.name) and not is_raw_name(jpeg.name)
    assert sorted(scan_image_files(tmp_path, frozenset())) == sorted([raw, jpeg])


@pytest.mark.parametrize("sample", CORPUS_SAMPLES, ids=lambda s: s["id"])
def test_raw_display(sample, corpus_dir):
    _check_display(sample, corpus_dir / sample["file"])


@pytest.mark.parametrize("reader_path", EXIF_PATHS)
@pytest.mark.parametrize("sample", CORPUS_SAMPLES, ids=lambda s: s["id"])
def test_raw_exif(sample, reader_path, corpus_dir, monkeypatch):
    _check_exif(sample, corpus_dir / sample["file"], reader_path, monkeypatch)


@pytest.mark.parametrize("sample", CORPUS_SAMPLES, ids=lambda s: s["id"])
def test_raw_score(sample, corpus_dir):
    _check_score(sample, corpus_dir / sample["file"])


def test_known_gaps_name_real_samples():
    ids = {s["id"] for s in SAMPLES}
    for capability, sample_id in KNOWN_GAPS:
        assert sample_id in ids, sample_id
        assert capability in {"display", "score", "exif/no-exiftool", "exif/exiftool"}, capability
