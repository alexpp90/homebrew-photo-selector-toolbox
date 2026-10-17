"""Unit tests for scripts/check_native_alignment.py."""

from __future__ import annotations

import contextlib
import io
from pathlib import Path
import struct
import sys
import tempfile
import unittest
import zipfile

SCRIPTS_DIR = Path(__file__).resolve().parents[1]
if str(SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPTS_DIR))

import check_native_alignment as cna  # noqa: E402


def _make_elf64_le(load_alignments: list[int], non_load_count: int = 1) -> bytes:
    """Synthesize a minimal valid ELF64 little-endian binary with PT_LOAD segments."""
    phdrs = bytearray()
    for _ in range(non_load_count):
        # PT_NOTE = 4, p_align = 4
        phdrs.extend(struct.pack("<IIQQQQQQ", 4, 4, 0, 0, 0, 0, 0, 4))
    for align in load_alignments:
        # PT_LOAD = 1
        phdrs.extend(struct.pack("<IIQQQQQQ", 1, 5, 0, 0, 0, 0x1000, 0x1000, align))

    e_phnum = non_load_count + len(load_alignments)
    e_phentsize = 56
    e_phoff = 64

    ident = bytearray(16)
    ident[0:4] = b"\x7fELF"
    ident[4] = 2  # ELFCLASS64
    ident[5] = 1  # ELFDATA2LSB
    ident[6] = 1  # EV_CURRENT

    # e_type=3 (ET_DYN), e_machine=183 (EM_AARCH64), e_version=1,
    # e_entry=0, e_phoff=64, e_shoff=0, e_flags=0, e_ehsize=64,
    # e_phentsize=56, e_phnum, e_shentsize=0, e_shnum=0, e_shstrndx=0
    header_rest = struct.pack(
        "<HHIQQQIHHHHHH",
        3,
        183,
        1,
        0,
        e_phoff,
        0,
        0,
        64,
        e_phentsize,
        e_phnum,
        0,
        0,
        0,
    )
    return bytes(ident) + header_rest + bytes(phdrs)


class CheckNativeAlignmentTests(unittest.TestCase):
    def test_elf64_with_16kb_load_segments_passes(self) -> None:
        elf = _make_elf64_le([16384, 65536])
        report = cna.inspect_elf_bytes("sample.apk", "arm64-v8a", "arm64-v8a/libgood.so", elf)
        self.assertTrue(report.passes())
        self.assertEqual(report.min_alignment, 16384)

    def test_elf64_with_4kb_load_segment_fails(self) -> None:
        elf = _make_elf64_le([16384, 4096])
        report = cna.inspect_elf_bytes("sample.apk", "arm64-v8a", "arm64-v8a/libbad.so", elf)
        self.assertFalse(report.passes())
        self.assertEqual(report.min_alignment, 4096)

    def test_invalid_elf_header_reports_error_and_fails(self) -> None:
        report = cna.inspect_elf_bytes("sample.apk", "x86_64", "x86_64/libcorrupt.so", b"not-an-elf")
        self.assertFalse(report.passes())
        self.assertIsNotNone(report.error)

    def test_apk_and_aab_archives_inspect_only_64bit_abis(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            apk_path = tmp_path / "app-release.apk"
            with zipfile.ZipFile(apk_path, "w") as zf:
                zf.writestr("lib/arm64-v8a/libopencv_java5.so", _make_elf64_le([16384, 16384]))
                zf.writestr("lib/x86_64/libopencv_java5.so", _make_elf64_le([16384]))
                # 32-bit armeabi-v7a has 4 KB pages and must be ignored by the 64-bit gate
                zf.writestr("lib/armeabi-v7a/libopencv_java5.so", _make_elf64_le([4096]))

            ok, reports = cna.check_targets([apk_path])
            self.assertTrue(ok)
            self.assertEqual(len(reports), 2)
            self.assertEqual([r.abi for r in reports], ["arm64-v8a", "x86_64"])

            aab_path = tmp_path / "app-release.aab"
            with zipfile.ZipFile(aab_path, "w") as zf:
                zf.writestr("base/lib/arm64-v8a/libtflite_jni.so", _make_elf64_le([4096]))
                zf.writestr("base/lib/x86_64/libopencv_java5.so", _make_elf64_le([16384]))

            ok_aab, reports_aab = cna.check_targets([aab_path])
            self.assertFalse(ok_aab)
            self.assertEqual(len(reports_aab), 2)
            # main() prints ✘/✔ lines; capture them so a green suite prints no ✘.
            out = io.StringIO()
            with contextlib.redirect_stderr(out), contextlib.redirect_stdout(out):
                self.assertEqual(cna.main([str(aab_path)]), 1)
                self.assertEqual(cna.main([str(apk_path)]), 0)
            self.assertIn("libtflite_jni.so", out.getvalue())

    def test_directory_target_inspects_merged_native_libs(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            arm64_dir = root / "out" / "lib" / "arm64-v8a"
            x86_dir = root / "out" / "lib" / "armeabi-v7a"
            arm64_dir.mkdir(parents=True)
            x86_dir.mkdir(parents=True)
            (arm64_dir / "libc++_shared.so").write_bytes(_make_elf64_le([16384]))
            (x86_dir / "libc++_shared.so").write_bytes(_make_elf64_le([4096]))

            with contextlib.redirect_stderr(io.StringIO()), contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(cna.main([str(root)]), 0)


if __name__ == "__main__":
    unittest.main()
