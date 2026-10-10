"""test_requirements_traceability.py — Unit & Regression Tests for Requirements Traceability Engine.

Verifies:
1. Strict requirement ID validation against format specification (STRICT_REQ_ID).
2. Markdown specification parsing, duplicate detection, and anchor isolation.
3. Test suite scanners for Python (AST), Kotlin (multiline annotations), and Swift (display names).
4. Comment scoping strictly to enclosing function bodies rather than following functions.
5. Detection of invalid ID formats in specifications and test suites.
6. Strict cross-product boundary enforcement and orphan detection.
7. Clean execution of verify_requirements_traceability.py --check --strict on HEAD.
"""

import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

# Resolve repository root
REPO_ROOT = Path(__file__).resolve().parents[4]
if str(REPO_ROOT) not in sys.path:
    sys.path.insert(0, str(REPO_ROOT))

from scripts.verify_requirements_traceability import (
    STRICT_REQ_ID,
    KotlinTestScanner,
    MarkdownRequirementsParser,
    PythonTestScanner,
    SwiftTestScanner,
    TestMapping,
    TraceabilityEngine,
)


class TestRequirementsTraceability(unittest.TestCase):
    """Regression test suite for verify_requirements_traceability.py."""

    def test_strict_req_id_regex(self):
        """Validate that STRICT_REQ_ID accurately distinguishes valid vs malformed IDs."""
        valid_ids = [
            "REQ-DESK-EXIF.01",
            "REQ-DESK-CULL.10",
            "REQ-AND-SAF.02",
            "REQ-TOK-GESTURE.03",
            "REQ-MAC-CULL.01",
            "REQ-SHARED-PLATFORM.01",
            "REQ-LINUX-META.01",
        ]
        for vid in valid_ids:
            self.assertTrue(bool(STRICT_REQ_ID.match(vid)), f"Expected {vid} to be valid")

        invalid_ids = [
            "REQ-INVALID-1.0",            # Period in section, single-digit index
            "REQ-DESK-99",                # Missing section name
            "REQ-DESK-EXIF",              # Missing index suffix
            "REQ-DESK-EXIF.1",            # Single-digit index instead of 2 digits
            "REQ-desk-exif.01",           # Lowercase letters
            "REQ-TOOLONGPRODUCT-EXIF.01", # Product code too long (>8 letters)
            "REQ-DE-EXIF.01",             # Product code too short (<3 letters)
            "R-LINUX-META-01",            # Non-standard prefix
        ]
        for iid in invalid_ids:
            self.assertFalse(bool(STRICT_REQ_ID.match(iid)), f"Expected {iid} to be invalid")

    def test_spec_parser_valid_and_anchor_isolation(self):
        """Test that Markdown parser extracts valid anchors and ignores inline prose references."""
        content = """# Spec Document
* **[REQ-DESK-EXIF.01] Optical Extraction:** Standardize on Aperture and Shutter Speed.
* **[REQ-DESK-EXIF.02] Cross-Reference:** See REQ-DESK-EXIF.01 for optical details.
1. **[REQ-DESK-CULL.01] Sharpness:** Noise-corrected focus scoring.
- **[REQ-SHARED-PLATFORM.01] Parity:** Core photographic concepts shared across products.
"""
        with tempfile.TemporaryDirectory() as tmpdir:
            spec_file = Path(tmpdir) / "REQUIREMENTS.md"
            spec_file.write_text(content, encoding="utf-8")

            reqs, invalids = MarkdownRequirementsParser.parse_file(spec_file, expected_code="DESK")
            self.assertEqual(len(invalids), 0, f"Expected 0 invalid IDs, got: {invalids}")
            self.assertEqual(len(reqs), 4)

            req_ids = [r.id for r in reqs]
            self.assertEqual(
                req_ids,
                [
                    "REQ-DESK-EXIF.01",
                    "REQ-DESK-EXIF.02",
                    "REQ-DESK-CULL.01",
                    "REQ-SHARED-PLATFORM.01",
                ],
            )

    def test_spec_parser_detects_invalid_syntax(self):
        """Test that Markdown parser captures malformed requirement IDs in specifications."""
        content = """# Malformed Specs
* **[REQ-INVALID-1.0] Bad Version:** Description.
* **[REQ-DESK-99] Missing Section:** Description.
* **[REQ-DESK-VALID.01] Good Spec:** Description.
"""
        with tempfile.TemporaryDirectory() as tmpdir:
            spec_file = Path(tmpdir) / "REQUIREMENTS.md"
            spec_file.write_text(content, encoding="utf-8")

            reqs, invalids = MarkdownRequirementsParser.parse_file(spec_file, expected_code="DESK")
            self.assertEqual(len(reqs), 1)
            self.assertEqual(reqs[0].id, "REQ-DESK-VALID.01")

            self.assertEqual(len(invalids), 2)
            invalid_raw_ids = [inv.raw_id for inv in invalids]
            self.assertIn("REQ-INVALID-1.0", invalid_raw_ids)
            self.assertIn("REQ-DESK-99", invalid_raw_ids)

    def test_spec_parser_detects_product_mismatch(self):
        """Test that Markdown parser detects when a spec contains requirement IDs for another product."""
        content = """# Desktop Specs with Foreign Product ID
* **[REQ-MAC-CULL.01] macOS Leak:** Should not be declared in Desktop specs.
* **[REQ-DESK-CULL.01] Native Desktop:** Valid declaration.
"""
        with tempfile.TemporaryDirectory() as tmpdir:
            spec_file = Path(tmpdir) / "REQUIREMENTS.md"
            spec_file.write_text(content, encoding="utf-8")

            reqs, invalids = MarkdownRequirementsParser.parse_file(spec_file, expected_code="DESK")
            self.assertEqual(len(reqs), 1)
            self.assertEqual(reqs[0].id, "REQ-DESK-CULL.01")

            self.assertEqual(len(invalids), 1)
            self.assertEqual(invalids[0].raw_id, "REQ-MAC-CULL.01")
            self.assertIn("Product code mismatch", invalids[0].reason)

    def test_spec_parser_duplicate_detection(self):
        """Test that TraceabilityEngine captures duplicate requirement declarations."""
        content = """# Duplicate Specs
* **[REQ-DESK-DUP.01] First Occurrence:** Line 2.
* **[REQ-DESK-DUP.01] Duplicate Occurrence:** Line 3.
"""
        with tempfile.TemporaryDirectory() as tmpdir:
            spec_file = Path(tmpdir) / "REQUIREMENTS.md"
            spec_file.write_text(content, encoding="utf-8")

            engine = TraceabilityEngine(target_product="desktop")
            reqs, invalids = MarkdownRequirementsParser.parse_file(spec_file, expected_code="DESK")
            for r in reqs:
                if r.id in engine.requirements:
                    existing = engine.requirements[r.id]
                    engine.duplicate_spec_ids.append((r, existing))
                else:
                    engine.requirements[r.id] = r

            self.assertEqual(len(engine.duplicate_spec_ids), 1)
            dup, orig = engine.duplicate_spec_ids[0]
            self.assertEqual(dup.id, "REQ-DESK-DUP.01")
            self.assertEqual(dup.line_number, 3)
            self.assertEqual(orig.line_number, 2)

    def test_python_scanner_multiline_and_invalid_ids(self):
        """Test Python scanner AST extraction on single/multiline decorators and invalid IDs."""
        code = '''
import pytest

@pytest.mark.requirement("REQ-DESK-EXIF.01")
def test_single_line(): pass

@pytest.mark.requirement(
    "REQ-DESK-EXIF.02",
    "REQ-DESK-EXIF.03"
)
def test_multi_line(): pass

@pytest.mark.requirement("REQ-DESK-99")
def test_invalid_syntax(): pass
'''
        with tempfile.TemporaryDirectory() as tmpdir:
            py_file = Path(tmpdir) / "test_sample.py"
            py_file.write_text(code, encoding="utf-8")

            mappings, invalids = PythonTestScanner.scan_file(py_file, product="desktop")
            self.assertEqual(len(mappings), 3)
            mapped_ids = [m.req_id for m in mappings]
            self.assertIn("REQ-DESK-EXIF.01", mapped_ids)
            self.assertIn("REQ-DESK-EXIF.02", mapped_ids)
            self.assertIn("REQ-DESK-EXIF.03", mapped_ids)

            self.assertEqual(len(invalids), 1)
            self.assertEqual(invalids[0].raw_id, "REQ-DESK-99")

    def test_kotlin_scanner_multiline_and_invalid_ids(self):
        """Test Kotlin scanner on single/multiline @Requirement annotations and invalid IDs."""
        code = '''
package com.photoselector.test

import org.junit.Test
import com.photoselector.core.Requirement

class SampleKotlinTest {
    @Test
    @Requirement("REQ-AND-EXIF.01")
    fun testSingle() {}

    @Test
    @Requirement(
        "REQ-AND-EXIF.02",
        "REQ-AND-EXIF.03"
    )
    fun `test multiline annotation`() {}

    @Requirement("REQ-AND-99")
    @Test
    fun testInvalid() {}
}
'''
        with tempfile.TemporaryDirectory() as tmpdir:
            kt_file = Path(tmpdir) / "SampleKotlinTest.kt"
            kt_file.write_text(code, encoding="utf-8")

            mappings, invalids = KotlinTestScanner.scan_file(kt_file, product="android-desktop")
            self.assertEqual(len(mappings), 3)
            mapped_ids = [m.req_id for m in mappings]
            self.assertIn("REQ-AND-EXIF.01", mapped_ids)
            self.assertIn("REQ-AND-EXIF.02", mapped_ids)
            self.assertIn("REQ-AND-EXIF.03", mapped_ids)

            self.assertEqual(len(invalids), 1)
            self.assertEqual(invalids[0].raw_id, "REQ-AND-99")

    def test_swift_scanner_multiline_and_invalid_ids(self):
        """Test Swift scanner on single/multiline @Test declarations and invalid IDs."""
        code = '''
import Testing
@testable import PhotoSelectorKit

struct SampleSwiftTests {
    @Test("REQ-MAC-CULL.01: Single line test")
    func testSingle() {}

    @Test(
        "REQ-MAC-CULL.02: Multiline display name",
        arguments: [1, 2]
    )
    func testMulti(val: Int) {}

    @Test("REQ-MAC-99: Bad ID")
    func testBad() {}
}
'''
        with tempfile.TemporaryDirectory() as tmpdir:
            sw_file = Path(tmpdir) / "SampleSwiftTests.swift"
            sw_file.write_text(code, encoding="utf-8")

            mappings, invalids = SwiftTestScanner.scan_file(sw_file, product="macos-desktop")
            self.assertEqual(len(mappings), 2)
            mapped_ids = [m.req_id for m in mappings]
            self.assertIn("REQ-MAC-CULL.01", mapped_ids)
            self.assertIn("REQ-MAC-CULL.02", mapped_ids)

            self.assertEqual(len(invalids), 1)
            self.assertEqual(invalids[0].raw_id, "REQ-MAC-99")

    def test_comment_scoping_inside_function_body(self):
        """Test that comments inside function bodies are attributed to enclosing func, not following func."""
        kt_code = '''
package com.photoselector.test

class ScopingTest {
    fun testFirst() {
        val x = 1
        // REQ-AND-EXIF.01
        assert(x == 1)
    }

    fun testSecond() {
        val y = 2
    }
}
'''
        with tempfile.TemporaryDirectory() as tmpdir:
            kt_file = Path(tmpdir) / "ScopingTest.kt"
            kt_file.write_text(kt_code, encoding="utf-8")

            mappings, _ = KotlinTestScanner.scan_file(kt_file, product="android-desktop")
            self.assertEqual(len(mappings), 1)
            self.assertEqual(mappings[0].req_id, "REQ-AND-EXIF.01")
            self.assertEqual(mappings[0].test_name, "testFirst")

        swift_code = '''
import Testing

struct ScopingSwiftTest {
    func testFirst() {
        // REQ-MAC-CULL.01
        let a = 1
    }

    func testSecond() {
        let b = 2
    }
}
'''
        with tempfile.TemporaryDirectory() as tmpdir:
            sw_file = Path(tmpdir) / "ScopingSwiftTest.swift"
            sw_file.write_text(swift_code, encoding="utf-8")

            mappings, _ = SwiftTestScanner.scan_file(sw_file, product="macos-desktop")
            self.assertEqual(len(mappings), 1)
            self.assertEqual(mappings[0].req_id, "REQ-MAC-CULL.01")
            self.assertEqual(mappings[0].test_name, "testFirst")

    def test_cross_product_rejection_and_strict_evaluation(self):
        """Test that cross-product references are flagged and fail under strict evaluation."""
        engine = TraceabilityEngine(target_product="desktop")
        from scripts.verify_requirements_traceability import Requirement
        engine.requirements["REQ-DESK-EXIF.01"] = Requirement(
            id="REQ-DESK-EXIF.01",
            product="desktop",
            title="EXIF",
            source_file="docs/products/desktop/REQUIREMENTS.md",
            line_number=1,
        )
        engine.requirements["REQ-MAC-CULL.01"] = Requirement(
            id="REQ-MAC-CULL.01",
            product="macos-desktop",
            title="Cull",
            source_file="docs/products/macos-desktop/REQUIREMENTS.md",
            line_number=1,
        )

        # Valid Desktop mapping
        engine.test_mappings.append(
            TestMapping(
                req_id="REQ-DESK-EXIF.01",
                product="desktop",
                test_file="products/desktop/tests/unit/test_exif.py",
                line_number=10,
                test_name="test_exif",
                mechanism="decorator",
            )
        )
        # Invalid cross-product mapping (Desktop test referencing macOS requirement)
        engine.test_mappings.append(
            TestMapping(
                req_id="REQ-MAC-CULL.01",
                product="desktop",
                test_file="products/desktop/tests/unit/test_cross.py",
                line_number=20,
                test_name="test_cross",
                mechanism="decorator",
            )
        )

        result = engine.evaluate(strict=True)
        self.assertEqual(len(result.cross_product_violations), 1)
        self.assertEqual(result.cross_product_violations[0].req_id, "REQ-MAC-CULL.01")

    def test_orphan_detection(self):
        """Test that tests referencing non-existent requirements are caught as orphans."""
        engine = TraceabilityEngine(target_product="desktop")
        from scripts.verify_requirements_traceability import Requirement
        engine.requirements["REQ-DESK-EXIF.01"] = Requirement(
            id="REQ-DESK-EXIF.01",
            product="desktop",
            title="EXIF",
            source_file="docs/products/desktop/REQUIREMENTS.md",
            line_number=1,
        )

        engine.test_mappings.append(
            TestMapping(
                req_id="REQ-DESK-NONEXISTENT.01",
                product="desktop",
                test_file="products/desktop/tests/unit/test_foo.py",
                line_number=10,
                test_name="test_foo",
                mechanism="decorator",
            )
        )

        result = engine.evaluate(strict=True)
        self.assertEqual(len(result.orphans), 1)
        self.assertEqual(result.orphans[0].req_id, "REQ-DESK-NONEXISTENT.01")

    def test_live_repository_passes_strict_cleanly(self):
        """Verify that current repository passes verify_requirements_traceability.py --check --strict."""
        proc = subprocess.run(
            [sys.executable, "scripts/verify_requirements_traceability.py", "--check", "--strict"],
            capture_output=True,
            text=True,
            cwd=str(REPO_ROOT),
        )
        msg = (
            f"Expected verify_requirements_traceability.py --check --strict to pass on clean HEAD.\n"
            f"Stdout:\n{proc.stdout}\nStderr:\n{proc.stderr}"
        )
        self.assertEqual(proc.returncode, 0, msg)


if __name__ == "__main__":
    unittest.main()
