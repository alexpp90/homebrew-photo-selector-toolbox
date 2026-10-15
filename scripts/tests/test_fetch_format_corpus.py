"""Tests for scripts/fetch_format_corpus.py — run with `python3 -m unittest discover scripts/tests`."""

from __future__ import annotations

import copy
import hashlib
import io
import os
import sys
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import fetch_format_corpus as fc  # noqa: E402


class ManifestValidationTest(unittest.TestCase):
    def setUp(self) -> None:
        self.manifest = fc.load_manifest()

    def test_committed_manifest_is_valid(self) -> None:
        self.assertEqual(fc.validate(self.manifest), [])

    def test_every_required_format_has_a_sample(self) -> None:
        broken = copy.deepcopy(self.manifest)
        broken["samples"] = [s for s in broken["samples"] if s["format"] != "cr3"]
        self.assertIn("required format cr3 has no sample", " ".join(fc.validate(broken)))

    def test_extension_claimed_twice_is_rejected(self) -> None:
        broken = copy.deepcopy(self.manifest)
        broken["formats"][1]["extensions"].append("arw")
        self.assertTrue(any("claimed by both" in e for e in fc.validate(broken)))

    def test_uppercase_or_dotted_extension_is_rejected(self) -> None:
        broken = copy.deepcopy(self.manifest)
        broken["formats"][0]["extensions"] = [".ARW"]
        self.assertTrue(any("lowercase without a dot" in e for e in fc.validate(broken)))

    def test_sample_extension_must_belong_to_its_format(self) -> None:
        broken = copy.deepcopy(self.manifest)
        sample = next(s for s in broken["samples"] if s["format"] == "nef")
        sample["format"] = "arw"
        self.assertTrue(any("is not an extension of format arw" in e for e in fc.validate(broken)))

    def test_committed_fixture_hash_is_checked(self) -> None:
        broken = copy.deepcopy(self.manifest)
        sample = next(s for s in broken["samples"] if s["source"] == "repo")
        sample["sha256"] = "0" * 64
        self.assertTrue(any("does not match its sha256" in e for e in fc.validate(broken)))

    def test_missing_ground_truth_is_rejected(self) -> None:
        broken = copy.deepcopy(self.manifest)
        del broken["samples"][0]["expect"]["iso"]
        self.assertTrue(any("expect is missing ['iso']" in e for e in fc.validate(broken)))


class CorpusFetchTest(unittest.TestCase):
    PAYLOAD = b"not really a raw file"

    def _manifest(self) -> dict:
        return {
            "corpus": {"mirrors": ["https://mirror.invalid/{file}"]},
            "samples": [{
                "id": "x", "format": "arw", "file": "A B.ARW", "source": "corpus",
                "url": "https://origin.invalid/A%20B.ARW",
                "sha256": hashlib.sha256(self.PAYLOAD).hexdigest(), "bytes": len(self.PAYLOAD),
            }],
        }

    def test_mirrors_come_first_and_file_names_are_quoted(self) -> None:
        manifest = self._manifest()
        self.assertEqual(
            fc.candidate_urls(manifest, manifest["samples"][0]),
            ["https://mirror.invalid/A%20B.ARW", "https://origin.invalid/A%20B.ARW"],
        )

    def test_falls_back_to_origin_when_mirror_fails(self) -> None:
        manifest = self._manifest()
        calls: list[str] = []

        def fake_urlopen(request, timeout):  # noqa: ANN001 — urllib signature
            calls.append(request.full_url)
            if "mirror" in request.full_url:
                raise OSError("404")
            return io.BytesIO(self.PAYLOAD)

        with tempfile.TemporaryDirectory() as tmp, mock.patch.object(fc.urllib.request, "urlopen", fake_urlopen), \
                redirect_stderr(io.StringIO()):
            self.assertTrue(fc.fetch(manifest, manifest["samples"][0], Path(tmp)))
            self.assertEqual((Path(tmp) / "A B.ARW").read_bytes(), self.PAYLOAD)
            self.assertEqual([p.name for p in Path(tmp).iterdir()], ["A B.ARW"])
        self.assertEqual(len(calls), 2)

    def test_hash_mismatch_never_lands_in_the_cache(self) -> None:
        manifest = self._manifest()
        with tempfile.TemporaryDirectory() as tmp, \
                mock.patch.object(fc.urllib.request, "urlopen", lambda r, timeout: io.BytesIO(b"tampered")), \
                redirect_stderr(io.StringIO()):
            self.assertFalse(fc.fetch(manifest, manifest["samples"][0], Path(tmp)))
            self.assertEqual(list(Path(tmp).iterdir()), [])

    def test_verify_reports_missing_and_corrupt_files(self) -> None:
        manifest = self._manifest()
        with tempfile.TemporaryDirectory() as tmp:
            missing, bad = fc.verify(manifest, Path(tmp))
            self.assertEqual([s["file"] for s in missing], ["A B.ARW"])
            (Path(tmp) / "A B.ARW").write_bytes(b"x" * len(self.PAYLOAD))
            missing, bad = fc.verify(manifest, Path(tmp))
            self.assertEqual(missing, [])
            self.assertEqual(len(bad), 1)

    def test_check_mode_never_downloads(self) -> None:
        with tempfile.TemporaryDirectory() as tmp, \
                mock.patch.dict(os.environ, {"PST_FORMAT_CORPUS": tmp}), \
                mock.patch.object(fc, "download", side_effect=AssertionError("network used")), \
                redirect_stderr(io.StringIO()), redirect_stdout(io.StringIO()):
            self.assertEqual(fc.main(["--check"]), 1)


if __name__ == "__main__":
    unittest.main()
