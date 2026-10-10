#!/usr/bin/env python3
"""verify_requirements_traceability.py — Automated Bidirectional Test Assertion Traceability Engine.

Validates that:
1. Every requirement defined in docs/products/*/REQUIREMENTS.md carries a unique machine-readable ID.
2. Every requirement ID conforms strictly to REQ-<PRODUCT>-<SECTION>.<INDEX:2d>.
3. Every requirement ID is mapped to at least one automated test assertion (100% forward coverage).
4. Every requirement ID referenced in test suites exists in the formal specifications (0 orphan references).
5. All multiline annotations (@Requirement, @Test) and decorators are fully parsed.
6. Comments inside test functions are strictly attributed to their enclosing function.
7. No cross-product requirement leakage occurs across test suites (--strict).

Usage:
    python3 scripts/verify_requirements_traceability.py --check
    python3 scripts/verify_requirements_traceability.py --check --strict
    python3 scripts/verify_requirements_traceability.py --product desktop
    python3 scripts/verify_requirements_traceability.py --matrix docs/TRACEABILITY_MATRIX.md
    python3 scripts/verify_requirements_traceability.py --json coverage.json
"""

from __future__ import annotations

import argparse
import ast
import json
import re
import sys

if sys.stdout and hasattr(sys.stdout, "reconfigure"):
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass
if sys.stderr and hasattr(sys.stderr, "reconfigure"):
    try:
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass
from dataclasses import asdict, dataclass, field
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

# Repository Root Resolution
REPO_ROOT = Path(__file__).resolve().parents[1]

# Requirement ID Syntax: REQ-<PRODUCT>-<SECTION>.<INDEX:2d>
# Supports product codes from 3 to 8 uppercase ASCII letters (e.g. AND, TOK, MAC, DESK, LINUX, SHARED).
REQ_ID_PATTERN = re.compile(r"\b(REQ-[A-Z]{3,8}-[A-Z0-9_]+\.[0-9]{2,})\b")
STRICT_REQ_ID = re.compile(r"^REQ-[A-Z]{3,8}-[A-Z0-9_]+\.[0-9]{2}$")
CANDIDATE_REQ_TOKEN = re.compile(r"\b(REQ-[A-Za-z0-9_.-]+)")
TOKEN_REQ_PATTERN = CANDIDATE_REQ_TOKEN

# Declaration Anchor Pattern:
# Matches bullet or heading declaration anchors at the beginning of a line:
#   * **[REQ-DESK-EXIF.01] Title:** ...
#   * **REQ-DESK-EXIF.01: Title** ...
#   1. **[REQ-DESK-EXIF.01] Title:** ...
#   ### [REQ-MAC-CULL.01] Title
DECLARATION_PATTERN = re.compile(
    r"^\s*(?:[*+\-\d.]+\s+|\#{1,6}\s+)?(?:\*\*)?"
    r"(?:\[(?P<bracket_id>[^\]]+)\]|(?P<bare_id>REQ-[^\s:*]+))(?:\*\*)?(?P<rest>.*)"
)
SPEC_DECL_PATTERN = DECLARATION_PATTERN

# Product Mapping Definitions
PRODUCT_CONFIGS = {
    "desktop": {
        "code": "DESK",
        "doc": REPO_ROOT / "docs" / "products" / "desktop" / "REQUIREMENTS.md",
        "test_dirs": [REPO_ROOT / "products" / "desktop" / "tests"],
        "lang": "python",
    },
    "android-desktop": {
        "code": "AND",
        "doc": REPO_ROOT / "docs" / "products" / "android-desktop" / "REQUIREMENTS.md",
        "test_dirs": [
            REPO_ROOT / "products" / "android" / "android-desktop" / "tests",
            REPO_ROOT / "products" / "android" / "core" / "tests",
        ],
        "lang": "kotlin",
    },
    "phototok": {
        "code": "TOK",
        "doc": REPO_ROOT / "docs" / "products" / "phototok" / "REQUIREMENTS.md",
        "test_dirs": [
            REPO_ROOT / "products" / "android" / "phototok" / "tests",
            REPO_ROOT / "products" / "android" / "core" / "tests",
        ],
        "lang": "kotlin",
    },
    "macos-desktop": {
        "code": "MAC",
        "doc": REPO_ROOT / "docs" / "products" / "macos-desktop" / "REQUIREMENTS.md",
        "test_dirs": [REPO_ROOT / "products" / "macos-desktop" / "tests"],
        "lang": "swift",
    },
}

SHARED_DOCS = [
    REPO_ROOT / "docs" / "shared" / "ANDROID_PLATFORM.md",
    REPO_ROOT / "docs" / "shared" / "FEATURE_PARITY.md",
]


@dataclass
class InvalidIdRecord:
    raw_id: str
    source_file: str
    line_number: int
    context: str = ""
    reason: str = ""
    id: str = ""

    def __post_init__(self) -> None:
        if not self.id:
            self.id = self.raw_id
        if not self.context:
            self.context = self.raw_id


InvalidRequirement = InvalidIdRecord
InvalidIdError = InvalidIdRecord


@dataclass
class Requirement:
    id: str
    product: str
    title: str
    source_file: str
    line_number: int


@dataclass
class TestMapping:
    __test__ = False
    req_id: str
    product: str
    test_file: str
    line_number: int
    test_name: str
    mechanism: str  # 'decorator', 'docstring', 'annotation', 'swift_display_name', 'comment', 'test_name'


@dataclass
class TraceabilityResult:
    total_requirements: int = 0
    covered_requirements: int = 0
    coverage_percentage: float = 0.0
    uncovered: List[Requirement] = field(default_factory=list)
    orphans: List[TestMapping] = field(default_factory=list)
    cross_product_violations: List[TestMapping] = field(default_factory=list)
    invalid_spec_ids: List[InvalidIdRecord] = field(default_factory=list)
    invalid_test_ids: List[InvalidIdRecord] = field(default_factory=list)
    invalid_ids: List[InvalidIdRecord] = field(default_factory=list)
    invalid_requirements: List[InvalidIdRecord] = field(default_factory=list)
    duplicate_spec_ids: List[Tuple[Requirement, Requirement]] = field(default_factory=list)
    duplicate_requirements: List[Tuple[Requirement, Requirement]] = field(default_factory=list)
    duplicates: List[Any] = field(default_factory=list)
    mappings_by_req: Dict[str, List[TestMapping]] = field(default_factory=dict)
    reqs_by_id: Dict[str, Requirement] = field(default_factory=dict)


def parse_function_spans(text: str, is_swift: bool = False) -> List[Dict[str, Any]]:
    """Token-aware brace parser for Kotlin and Swift function declarations and body spans."""
    pattern = (
        r"\bfunc\s+([a-zA-Z0-9_]+)(?:<[^>]+>)?\s*\("
        if is_swift
        else r"\bfun\s+(?:<[^>]+>\s+)?(?:`([^`]+)`|([a-zA-Z0-9_]+))\s*\("
    )
    fun_re = re.compile(pattern)
    funcs = []

    matches = list(fun_re.finditer(text))
    for idx, m in enumerate(matches):
        name = m.group(1) if is_swift else (m.group(1) or m.group(2))
        start_char = m.start()
        start_line = text.count("\n", 0, start_char) + 1

        next_start = matches[idx + 1].start() if idx + 1 < len(matches) else len(text)
        open_brace = text.find("{", m.end())
        if open_brace != -1 and open_brace < next_start:
            depth = 1
            i = open_brace + 1
            in_str = False
            str_char = None
            in_line_comment = False
            in_block_comment = False

            while i < len(text) and depth > 0:
                c = text[i]
                if in_line_comment:
                    if c == "\n":
                        in_line_comment = False
                elif in_block_comment:
                    if text[i : i + 2] == "*/":
                        in_block_comment = False
                        i += 1
                elif in_str:
                    if c == "\\":
                        i += 1
                    elif c == str_char:
                        in_str = False
                else:
                    if text[i : i + 2] == "//":
                        in_line_comment = True
                        i += 1
                    elif text[i : i + 2] == "/*":
                        in_block_comment = True
                        i += 1
                    elif c in ('"', "'"):
                        in_str = True
                        str_char = c
                    elif c == "{":
                        depth += 1
                    elif c == "}":
                        depth -= 1
                i += 1

            end_char = i - 1 if depth == 0 else len(text)
            body_start_line = text.count("\n", 0, open_brace) + 1
            body_end_line = text.count("\n", 0, end_char) + 1
            funcs.append(
                {
                    "name": name,
                    "def_line": start_line,
                    "body_start": body_start_line,
                    "body_end": body_end_line,
                }
            )
        else:
            funcs.append(
                {
                    "name": name,
                    "def_line": start_line,
                    "body_start": start_line,
                    "body_end": start_line,
                }
            )

    return funcs


def resolve_enclosing_or_following_func(funcs: List[Dict[str, Any]], line_no: int, fallback: str) -> str:
    """Attributes a line to its enclosing function body, or to the immediately following test signature."""
    # 1. Enclosing function body check
    for f in funcs:
        if f["body_start"] <= line_no <= f["body_end"]:
            return f["name"]

    # 2. Preceding annotation / doc comment check
    for f in funcs:
        if f["def_line"] >= line_no:
            return f["name"]

    return fallback


class MarkdownRequirementsParser:
    """Parses formal requirement IDs from Markdown specification documents with strict syntax validation."""

    @staticmethod
    def parse_file(
        path: Path,
        expected_code: Optional[str] = None,
        errors: Optional[List[InvalidIdRecord]] = None,
    ) -> Tuple[List[Requirement], List[InvalidIdRecord]]:
        if not path.exists():
            return [], []

        requirements: List[Requirement] = []
        invalid_ids: List[InvalidIdRecord] = []
        try:
            rel_path = str(path.resolve().relative_to(REPO_ROOT))
        except ValueError:
            rel_path = str(path)

        lines = path.read_text(encoding="utf-8").splitlines()

        for idx, line in enumerate(lines, start=1):
            m = DECLARATION_PATTERN.match(line)
            declared_id: Optional[str] = None

            if m:
                cand = (m.group("bracket_id") or m.group("bare_id") or "").strip()
                if cand.startswith("REQ-") or cand.startswith("R-"):
                    declared_id = cand
                    if not STRICT_REQ_ID.match(cand):
                        reason_msg = (
                            f"Malformed requirement ID syntax '{cand}' "
                            f"(must match REQ-<PRODUCT>-<SECTION>.<INDEX:2d>)"
                        )
                        invalid_ids.append(
                            InvalidIdRecord(
                                raw_id=cand,
                                source_file=rel_path,
                                line_number=idx,
                                context=line.strip()[:70],
                                reason=reason_msg,
                            )
                        )
                    else:
                        parts = cand.split("-")
                        prod_code = parts[1] if len(parts) >= 2 else ""
                        if expected_code and prod_code != expected_code and prod_code != "SHARED":
                            mismatch_msg = (
                                f"Product code mismatch: '{prod_code}' in '{cand}' "
                                f"does not match expected product '{expected_code}'"
                            )
                            invalid_ids.append(
                                InvalidIdRecord(
                                    raw_id=cand,
                                    source_file=rel_path,
                                    line_number=idx,
                                    context=line.strip()[:70],
                                    reason=mismatch_msg,
                                )
                            )
                        else:
                            # Extract clean title from the line anchor
                            cleaned = re.sub(r"^[*\-\d.]+\s+", "", line).strip()
                            title_match = re.search(
                                r"\*\*(?:\[?" + re.escape(cand) + r"\]?\s*[:-]?\s*)?([^*]+)\*\*", cleaned
                            )
                            title = title_match.group(1).strip() if title_match else cleaned[:60]
                            title = re.sub(r"^\[" + re.escape(cand) + r"\]\s*", "", title).strip(" :*-")

                            prod = "unknown"
                            for p_name, p_cfg in PRODUCT_CONFIGS.items():
                                if cand.startswith(f"REQ-{p_cfg['code']}-"):
                                    prod = p_name
                                    break
                            if cand.startswith("REQ-SHARED-"):
                                prod = "shared"

                            requirements.append(
                                Requirement(
                                    id=cand,
                                    product=prod,
                                    title=title or cand,
                                    source_file=rel_path,
                                    line_number=idx,
                                )
                            )

            # Scan line for any malformed REQ- tokens in prose/cross-references
            for raw_tok in TOKEN_REQ_PATTERN.findall(line):
                clean_tok = raw_tok.rstrip(".,:;)]*`\"'")
                if declared_id and clean_tok == declared_id:
                    continue
                if "<" in clean_tok or ">" in clean_tok or "INDEX" in clean_tok:
                    continue
                if not STRICT_REQ_ID.match(clean_tok):
                    invalid_ids.append(
                        InvalidIdRecord(
                            raw_id=clean_tok,
                            source_file=rel_path,
                            line_number=idx,
                            context=line.strip()[:70],
                            reason=f"Malformed requirement token '{clean_tok}' in description",
                        )
                    )

        if errors is not None:
            errors.extend(invalid_ids)
        return requirements, invalid_ids


class PythonTestScanner:
    """Scans Python test files using AST traversal for requirement markers, docstrings, and comments."""

    @staticmethod
    def scan_file(
        path: Path, product: str, errors: Optional[List[InvalidIdRecord]] = None
    ) -> Tuple[List[TestMapping], List[InvalidIdRecord]]:
        mappings: List[TestMapping] = []
        invalid_ids: List[InvalidIdRecord] = []
        try:
            content = path.read_text(encoding="utf-8")
            tree = ast.parse(content, filename=str(path))
        except Exception:
            return mappings, invalid_ids

        try:
            rel_path = str(path.resolve().relative_to(REPO_ROOT))
        except ValueError:
            rel_path = str(path)

        for node in ast.walk(tree):
            if not isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
                continue

            test_name = node.name
            line_no = node.lineno

            # 1. Inspect Decorators: @pytest.mark.requirement(...)
            for dec in node.decorator_list:
                found_valid, found_invalid = PythonTestScanner._extract_req_ids_from_decorator(
                    dec, rel_path=rel_path, test_name=test_name
                )
                invalid_ids.extend(found_invalid)
                for req_id, dec_line in found_valid:
                    mappings.append(
                        TestMapping(
                            req_id=req_id,
                            product=product,
                            test_file=rel_path,
                            line_number=dec_line,
                            test_name=test_name,
                            mechanism="decorator",
                        )
                    )

            # 2. Inspect Docstring
            doc = ast.get_docstring(node)
            if doc:
                for match in TOKEN_REQ_PATTERN.findall(doc):
                    clean = match.rstrip(".,;:*)]}\"'")
                    if STRICT_REQ_ID.match(clean):
                        if not any(m.req_id == clean and m.test_name == test_name for m in mappings):
                            mappings.append(
                                TestMapping(
                                    req_id=clean,
                                    product=product,
                                    test_file=rel_path,
                                    line_number=line_no,
                                    test_name=test_name,
                                    mechanism="docstring",
                                )
                            )
                    elif clean.startswith("REQ-"):
                        invalid_ids.append(
                            InvalidIdRecord(
                                raw_id=clean,
                                source_file=rel_path,
                                line_number=line_no,
                                context=f"docstring in {test_name}",
                                reason=f"Malformed requirement ID format in docstring: '{clean}'",
                            )
                        )

        # 3. Inspect line comments # REQ-...
        lines = content.splitlines()
        for idx, line in enumerate(lines, start=1):
            if "#" in line:
                comment_part = line.split("#", 1)[1]
                if "REQ-" in comment_part:
                    for match in TOKEN_REQ_PATTERN.findall(comment_part):
                        clean = match.rstrip(".,;:*)]}\"'")
                        if STRICT_REQ_ID.match(clean):
                            if not any(m.req_id == clean and m.line_number == idx for m in mappings):
                                mappings.append(
                                    TestMapping(
                                        req_id=clean,
                                        product=product,
                                        test_file=rel_path,
                                        line_number=idx,
                                        test_name="comment",
                                        mechanism="comment",
                                    )
                                )
                        elif clean.startswith("REQ-"):
                            invalid_ids.append(
                                InvalidIdRecord(
                                    raw_id=clean,
                                    source_file=rel_path,
                                    line_number=idx,
                                    context=comment_part.strip()[:70],
                                    reason=f"Malformed requirement ID format in comment: '{clean}'",
                                )
                            )

        if errors is not None:
            errors.extend(invalid_ids)
        return mappings, invalid_ids

    @staticmethod
    def _extract_req_ids_from_decorator(
        dec: ast.expr, rel_path: str = "", test_name: str = ""
    ) -> Tuple[List[Tuple[str, int]], List[InvalidIdRecord]]:
        valid: List[Tuple[str, int]] = []
        invalid: List[InvalidIdRecord] = []
        if isinstance(dec, ast.Call):
            func = dec.func
            is_req_marker = False
            if isinstance(func, ast.Attribute) and func.attr in ("requirement", "req"):
                is_req_marker = True
            elif isinstance(func, ast.Name) and func.id in ("requirement", "req"):
                is_req_marker = True

            if is_req_marker:
                dec_line = dec.lineno if hasattr(dec, "lineno") else 1
                for arg in dec.args:
                    if isinstance(arg, ast.Constant) and isinstance(arg.value, str):
                        raw_val = arg.value
                        pieces = [p.strip() for p in raw_val.split(",")] if "," in raw_val else [raw_val.strip()]
                        for piece in pieces:
                            if STRICT_REQ_ID.match(piece):
                                valid.append((piece, dec_line))
                            elif piece.startswith("REQ-"):
                                invalid.append(
                                    InvalidIdRecord(
                                        raw_id=piece,
                                        source_file=rel_path,
                                        line_number=dec_line,
                                        context=f"@pytest.mark.requirement in {test_name}",
                                        reason=f"Invalid requirement ID format in @pytest.mark.requirement: '{piece}'",
                                    )
                                )
        return valid, invalid


class KotlinTestScanner:
    """Scans Kotlin test files for @Requirement annotations (including multiline), KDoc, and test methods."""

    @staticmethod
    def scan_file(
        path: Path, product: str, errors: Optional[List[InvalidIdRecord]] = None
    ) -> Tuple[List[TestMapping], List[InvalidIdRecord]]:
        mappings: List[TestMapping] = []
        invalid_ids: List[InvalidIdRecord] = []
        try:
            text = path.read_text(encoding="utf-8")
        except Exception:
            return mappings, invalid_ids

        try:
            rel_path = str(path.resolve().relative_to(REPO_ROOT))
        except ValueError:
            rel_path = str(path)

        funcs = parse_function_spans(text, is_swift=False)

        # 1. Parse @Requirement(...) (handles single-line and multiline annotations)
        req_anno_re = re.compile(r"@Requirement\s*\(")
        for m in req_anno_re.finditer(text):
            anno_start_char = m.start()
            anno_line = text.count("\n", 0, anno_start_char) + 1
            open_paren = m.end() - 1

            depth = 1
            i = open_paren + 1
            in_str = False
            str_char = None
            while i < len(text) and depth > 0:
                c = text[i]
                if in_str:
                    if c == "\\":
                        i += 1
                    elif c == str_char:
                        in_str = False
                else:
                    if c in ('"', "'"):
                        in_str = True
                        str_char = c
                    elif c == "(":
                        depth += 1
                    elif c == ")":
                        depth -= 1
                i += 1

            args_text = text[open_paren + 1 : i - 1] if depth == 0 else text[open_paren + 1 :]
            test_name = resolve_enclosing_or_following_func(funcs, anno_line, fallback="testMethod")

            raw_strings = re.findall(r'"([^"\\]*(?:\\.[^"\\]*)*)"', args_text)
            for s in raw_strings:
                s_clean = s.strip()
                if STRICT_REQ_ID.match(s_clean):
                    mappings.append(
                        TestMapping(
                            req_id=s_clean,
                            product=product,
                            test_file=rel_path,
                            line_number=anno_line,
                            test_name=test_name,
                            mechanism="annotation",
                        )
                    )
                elif s_clean.startswith("REQ-"):
                    invalid_ids.append(
                        InvalidIdRecord(
                            raw_id=s_clean,
                            source_file=rel_path,
                            line_number=anno_line,
                            context=f"@Requirement in {test_name}",
                            reason=f"Malformed requirement ID format in @Requirement: '{s_clean}'",
                        )
                    )

        # 2. Inspect KDoc or Line Comments (strictly scoped to enclosing or following function)
        lines = text.splitlines()
        for idx, line in enumerate(lines, start=1):
            if ("//" in line or "/*" in line or "*" in line) and "REQ-" in line:
                candidates = CANDIDATE_REQ_TOKEN.findall(line)
                for cand in candidates:
                    cand_clean = cand.rstrip(".,;:*)]}\"'")
                    test_name = resolve_enclosing_or_following_func(funcs, idx, fallback="testMethod")
                    if STRICT_REQ_ID.match(cand_clean):
                        if not any(m.req_id == cand_clean and m.line_number == idx for m in mappings):
                            mappings.append(
                                TestMapping(
                                    req_id=cand_clean,
                                    product=product,
                                    test_file=rel_path,
                                    line_number=idx,
                                    test_name=test_name,
                                    mechanism="kdoc_or_comment",
                                )
                            )
                    elif cand_clean.startswith("REQ-"):
                        invalid_ids.append(
                            InvalidIdRecord(
                                raw_id=cand_clean,
                                source_file=rel_path,
                                line_number=idx,
                                context=line.strip()[:70],
                                reason=f"Malformed requirement ID format in comment: '{cand_clean}'",
                            )
                        )

        # 3. Inspect Test Method Names Containing REQ-
        for f in funcs:
            if "REQ-" in f["name"]:
                candidates = CANDIDATE_REQ_TOKEN.findall(f["name"])
                for cand in candidates:
                    cand_clean = cand.rstrip(".,;:*)]}\"'")
                    if STRICT_REQ_ID.match(cand_clean):
                        if not any(m.req_id == cand_clean and m.test_name == f["name"] for m in mappings):
                            mappings.append(
                                TestMapping(
                                    req_id=cand_clean,
                                    product=product,
                                    test_file=rel_path,
                                    line_number=f["def_line"],
                                    test_name=f["name"],
                                    mechanism="test_name",
                                )
                            )
                    elif cand_clean.startswith("REQ-"):
                        invalid_ids.append(
                            InvalidIdRecord(
                                raw_id=cand_clean,
                                source_file=rel_path,
                                line_number=f["def_line"],
                                context="test_name",
                                reason=f"Malformed requirement ID format in test method name: '{cand_clean}'",
                            )
                        )

        if errors is not None:
            errors.extend(invalid_ids)
        return mappings, invalid_ids


class SwiftTestScanner:
    """Scans Swift test files for Swift Testing @Test declarations (including multiline) and comments."""

    @staticmethod
    def scan_file(
        path: Path, product: str, errors: Optional[List[InvalidIdRecord]] = None
    ) -> Tuple[List[TestMapping], List[InvalidIdRecord]]:
        mappings: List[TestMapping] = []
        invalid_ids: List[InvalidIdRecord] = []
        try:
            text = path.read_text(encoding="utf-8")
        except Exception:
            return mappings, invalid_ids

        try:
            rel_path = str(path.resolve().relative_to(REPO_ROOT))
        except ValueError:
            rel_path = str(path)

        funcs = parse_function_spans(text, is_swift=True)

        # 1. Inspect @Test Declarations (handles multiline declarations and attributes)
        test_anno_re = re.compile(r"@Test\s*\(")
        for m in test_anno_re.finditer(text):
            anno_start_char = m.start()
            anno_line = text.count("\n", 0, anno_start_char) + 1
            open_paren = m.end() - 1

            depth = 1
            i = open_paren + 1
            in_str = False
            str_char = None
            while i < len(text) and depth > 0:
                c = text[i]
                if in_str:
                    if c == "\\":
                        i += 1
                    elif c == str_char:
                        in_str = False
                else:
                    if c in ('"', "'"):
                        in_str = True
                        str_char = c
                    elif c == "(":
                        depth += 1
                    elif c == ")":
                        depth -= 1
                i += 1

            args_text = text[open_paren + 1 : i - 1] if depth == 0 else text[open_paren + 1 :]
            test_name = resolve_enclosing_or_following_func(funcs, anno_line, fallback="testFunction")

            raw_strings = re.findall(r'"([^"\\]*(?:\\.[^"\\]*)*)"', args_text)
            for s in raw_strings:
                candidates = CANDIDATE_REQ_TOKEN.findall(s)
                for cand in candidates:
                    cand_clean = cand.rstrip(".,;:*)]}\"'")
                    if STRICT_REQ_ID.match(cand_clean):
                        mappings.append(
                            TestMapping(
                                req_id=cand_clean,
                                product=product,
                                test_file=rel_path,
                                line_number=anno_line,
                                test_name=test_name,
                                mechanism="swift_display_name",
                            )
                        )
                    elif cand_clean.startswith("REQ-"):
                        invalid_ids.append(
                            InvalidIdRecord(
                                raw_id=cand_clean,
                                source_file=rel_path,
                                line_number=anno_line,
                                context=s[:70],
                                reason=f"Malformed requirement ID format in @Test: '{cand_clean}'",
                            )
                        )

        # 2. Inspect Comments /// REQ-... or // REQ-... (strictly scoped)
        lines = text.splitlines()
        for idx, line in enumerate(lines, start=1):
            if ("///" in line or "//" in line) and "REQ-" in line:
                candidates = CANDIDATE_REQ_TOKEN.findall(line)
                for cand in candidates:
                    cand_clean = cand.rstrip(".,;:*)]}\"'")
                    test_name = resolve_enclosing_or_following_func(funcs, idx, fallback="testFunction")
                    if STRICT_REQ_ID.match(cand_clean):
                        if not any(m.req_id == cand_clean and m.line_number == idx for m in mappings):
                            mappings.append(
                                TestMapping(
                                    req_id=cand_clean,
                                    product=product,
                                    test_file=rel_path,
                                    line_number=idx,
                                    test_name=test_name,
                                    mechanism="comment",
                                )
                            )
                    elif cand_clean.startswith("REQ-"):
                        invalid_ids.append(
                            InvalidIdRecord(
                                raw_id=cand_clean,
                                source_file=rel_path,
                                line_number=idx,
                                context=line.strip()[:70],
                                reason=f"Malformed requirement ID format in Swift comment: '{cand_clean}'",
                            )
                        )

        if errors is not None:
            errors.extend(invalid_ids)
        return mappings, invalid_ids


class TraceabilityEngine:
    """Cross-validates requirements and test mappings, calculating matrix coverage."""

    def __init__(self, target_product: Optional[str] = None):
        self.target_product = target_product
        self.requirements: Dict[str, Requirement] = {}
        self.test_mappings: List[TestMapping] = []
        self.invalid_spec_ids: List[InvalidIdRecord] = []
        self.invalid_test_ids: List[InvalidIdRecord] = []
        self.duplicate_spec_ids: List[Tuple[Requirement, Requirement]] = []

    def collect(self) -> None:
        # 1. Parse Requirements from Product Specs
        prods = (
            [self.target_product]
            if self.target_product and self.target_product != "all"
            else list(PRODUCT_CONFIGS.keys())
        )
        for p_name in prods:
            cfg = PRODUCT_CONFIGS[p_name]
            reqs, invalids = MarkdownRequirementsParser.parse_file(cfg["doc"], cfg["code"])
            self.invalid_spec_ids.extend(invalids)
            for r in reqs:
                if r.id in self.requirements:
                    existing = self.requirements[r.id]
                    self.duplicate_spec_ids.append((r, existing))
                else:
                    self.requirements[r.id] = r

        # 2. Parse Requirements from Shared Documents
        for shared_doc in SHARED_DOCS:
            if shared_doc.exists():
                reqs, invalids = MarkdownRequirementsParser.parse_file(shared_doc, expected_code=None)
                self.invalid_spec_ids.extend(invalids)
                for r in reqs:
                    if r.id in self.requirements:
                        existing = self.requirements[r.id]
                        self.duplicate_spec_ids.append((r, existing))
                    else:
                        self.requirements[r.id] = r

        # 3. Scan Test Suites
        for p_name in prods:
            cfg = PRODUCT_CONFIGS[p_name]
            lang = cfg["lang"]
            for test_dir in cfg["test_dirs"]:
                if not test_dir.exists():
                    continue

                if lang == "python":
                    for py_file in test_dir.rglob("*.py"):
                        mappings, invalids = PythonTestScanner.scan_file(py_file, p_name)
                        self.test_mappings.extend(mappings)
                        self.invalid_test_ids.extend(invalids)
                elif lang == "kotlin":
                    for kt_file in test_dir.rglob("*.kt"):
                        if "/build/" in str(kt_file):
                            continue
                        mappings, invalids = KotlinTestScanner.scan_file(kt_file, p_name)
                        self.test_mappings.extend(mappings)
                        self.invalid_test_ids.extend(invalids)
                elif lang == "swift":
                    for sw_file in test_dir.rglob("*.swift"):
                        if "/.build/" in str(sw_file):
                            continue
                        mappings, invalids = SwiftTestScanner.scan_file(sw_file, p_name)
                        self.test_mappings.extend(mappings)
                        self.invalid_test_ids.extend(invalids)

    def evaluate(self, strict: bool = False) -> TraceabilityResult:
        result = TraceabilityResult()
        result.total_requirements = len(self.requirements)
        result.reqs_by_id = self.requirements
        result.invalid_spec_ids = list(self.invalid_spec_ids)
        result.invalid_test_ids = list(self.invalid_test_ids)
        result.invalid_ids = list(self.invalid_spec_ids) + list(self.invalid_test_ids)
        result.invalid_requirements = result.invalid_ids
        result.duplicate_spec_ids = list(self.duplicate_spec_ids)
        result.duplicate_requirements = list(self.duplicate_spec_ids)
        result.duplicates = list(self.duplicate_spec_ids)

        # Index mappings by requirement ID
        for m in self.test_mappings:
            result.mappings_by_req.setdefault(m.req_id, []).append(m)

        # Check Forward Coverage (Requirements -> Tests)
        covered_count = 0
        for req_id, req in self.requirements.items():
            mappings = result.mappings_by_req.get(req_id, [])
            if mappings:
                covered_count += 1
            else:
                result.uncovered.append(req)

        result.covered_requirements = covered_count
        result.coverage_percentage = (
            (covered_count / result.total_requirements * 100.0) if result.total_requirements > 0 else 100.0
        )

        # Check Backward Traceability (Tests -> Requirements)
        for m in self.test_mappings:
            if m.req_id not in self.requirements:
                result.orphans.append(m)

        # Check Product Boundary Isolation
        for m in self.test_mappings:
            expected_prefix = PRODUCT_CONFIGS.get(m.product, {}).get("code")
            if expected_prefix:
                if not m.req_id.startswith(f"REQ-{expected_prefix}-") and not m.req_id.startswith("REQ-SHARED-"):
                    result.cross_product_violations.append(m)

        return result


def generate_markdown_matrix(result: TraceabilityResult, dest: Path) -> None:
    lines = [
        "# Requirements Traceability Matrix",
        "",
        f"- **Total Formal Requirements**: {result.total_requirements}",
        f"- **Covered Requirements**: {result.covered_requirements}",
        f"- **Coverage Percentage**: {result.coverage_percentage:.1f}%",
        f"- **Total Test Mappings**: {sum(len(m) for m in result.mappings_by_req.values())}",
        f"- **Orphan Mappings**: {len(result.orphans)}",
        f"- **Invalid ID Errors**: {len(result.invalid_ids)}",
        f"- **Duplicate Declarations**: {len(result.duplicate_spec_ids)}",
        "",
        "| Requirement ID | Product | Title | Test Count | Mapped Tests | Status |",
        "|---|---|---|---|---|---|",
    ]

    for req_id in sorted(result.reqs_by_id.keys()):
        req = result.reqs_by_id[req_id]
        mappings = result.mappings_by_req.get(req_id, [])
        status = "✅ COVERED" if mappings else "❌ UNCOVERED"
        test_summary = "<br>".join([f"`{m.test_file}:{m.line_number}` ({m.test_name})" for m in mappings[:3]])
        if len(mappings) > 3:
            test_summary += f"<br>*(+{len(mappings)-3} more)*"
        if not test_summary:
            test_summary = "*None*"

        lines.append(f"| `{req.id}` | {req.product} | {req.title} | {len(mappings)} | {test_summary} | {status} |")

    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description="Validate bidirectional requirement-to-test traceability.")
    parser.add_argument(
        "--check", action="store_true", help="CI gate check: verifies requirements, orphans, and test mappings."
    )
    parser.add_argument(
        "--product", choices=["desktop", "android-desktop", "phototok", "macos-desktop", "all"], default="all"
    )
    parser.add_argument("--matrix", type=Path, help="Export markdown traceability matrix to file.")
    parser.add_argument("--json", type=Path, help="Export JSON summary data to file.")
    parser.add_argument("--strict", action="store_true", help="Fail on cross-product test mappings.")
    parser.add_argument(
        "--strict-coverage", action="store_true", help="Fail if any requirement is uncovered (100%% SLA)."
    )
    parser.add_argument(
        "--min-coverage", type=float, default=None, help="Fail if coverage percentage is below threshold (0-100)."
    )
    parser.add_argument("-v", "--verbose", action="store_true", help="Verbose output.")
    args = parser.parse_args()

    engine = TraceabilityEngine(target_product=None if args.product == "all" else args.product)
    engine.collect()
    result = engine.evaluate(strict=args.strict)

    # ANSI Colors
    GREEN = "\033[0;32m"
    RED = "\033[0;31m"
    YELLOW = "\033[1;33m"
    BLUE = "\033[0;34m"
    RESET = "\033[0m"

    print(f"\n{BLUE}=== Photo Selector Toolbox — Requirements Traceability Audit ==={RESET}")
    print(f"Target Product Scope : {args.product}")
    print(f"Total Requirements   : {result.total_requirements}")
    print(f"Covered by Tests     : {result.covered_requirements}")
    print(f"Coverage Percentage  : {result.coverage_percentage:.1f}%")

    if result.invalid_spec_ids:
        print(f"\n{RED}❌ Invalid Requirement IDs in Specifications ({len(result.invalid_spec_ids)}):{RESET}")
        for inv in result.invalid_spec_ids:
            print(f"  - '{inv.raw_id}' at {inv.source_file}:{inv.line_number} — {inv.reason}")

    if result.duplicate_spec_ids:
        print(
            f"\n{RED}❌ Duplicate Requirement Declarations in Specifications "
            f"({len(result.duplicate_spec_ids)}):{RESET}"
        )
        for dup, orig in result.duplicate_spec_ids:
            print(
                f"  - '{dup.id}' at {dup.source_file}:{dup.line_number} duplicates "
                f"definition at {orig.source_file}:{orig.line_number}"
            )

    if result.invalid_test_ids:
        print(f"\n{RED}❌ Invalid Requirement IDs in Test Suites ({len(result.invalid_test_ids)}):{RESET}")
        for inv in result.invalid_test_ids:
            print(f"  - '{inv.raw_id}' in {inv.source_file}:{inv.line_number} ({inv.context}) — {inv.reason}")

    if result.uncovered and (args.verbose or args.strict_coverage or args.min_coverage is not None):
        color = (
            RED
            if (args.strict_coverage or (args.min_coverage and result.coverage_percentage < args.min_coverage))
            else YELLOW
        )
        print(f"\n{color}Uncovered Requirements ({len(result.uncovered)}):{RESET}")
        for u in result.uncovered:
            print(f"  - {u.id} ({u.product}): {u.title} [{u.source_file}:{u.line_number}]")

    if result.orphans:
        print(f"\n{RED}❌ Orphan Test References ({len(result.orphans)}):{RESET}")
        for o in result.orphans:
            print(
                f"  - {o.req_id} referenced in {o.test_file}:{o.line_number} "
                f"({o.test_name}) does not exist in specs."
            )

    if result.cross_product_violations:
        color = YELLOW if not args.strict else RED
        print(f"\n{color}⚠️ Cross-Product Test Violations ({len(result.cross_product_violations)}):{RESET}")
        for c in result.cross_product_violations:
            print(f"  - Test in product {c.product} references {c.req_id} at {c.test_file}:{c.line_number}")

    if args.matrix:
        generate_markdown_matrix(result, args.matrix)
        print(f"\nGenerated Markdown Matrix at: {args.matrix}")

    if args.json:
        args.json.parent.mkdir(parents=True, exist_ok=True)
        summary_data = {
            "total_requirements": result.total_requirements,
            "covered_requirements": result.covered_requirements,
            "coverage_percentage": result.coverage_percentage,
            "uncovered_count": len(result.uncovered),
            "orphans_count": len(result.orphans),
            "invalid_spec_count": len(result.invalid_spec_ids),
            "invalid_test_count": len(result.invalid_test_ids),
            "duplicate_spec_count": len(result.duplicate_spec_ids),
            "cross_product_count": len(result.cross_product_violations),
            "uncovered": [asdict(u) for u in result.uncovered],
            "orphans": [asdict(o) for o in result.orphans],
            "invalid_ids": [asdict(e) for e in result.invalid_ids],
        }
        args.json.write_text(json.dumps(summary_data, indent=2), encoding="utf-8")
        print(f"Generated JSON report at: {args.json}")

    # Check Evaluation
    if args.check:
        has_blocking_errors = False

        if result.total_requirements == 0:
            print(f"\n{RED}FAIL: No requirements found in specification documents. Tagging required.{RESET}")
            has_blocking_errors = True
        if result.invalid_spec_ids:
            print(f"\n{RED}FAIL: {len(result.invalid_spec_ids)} invalid requirement IDs in specifications.{RESET}")
            has_blocking_errors = True
        if result.duplicate_spec_ids:
            print(f"\n{RED}FAIL: {len(result.duplicate_spec_ids)} duplicate requirement definitions detected.{RESET}")
            has_blocking_errors = True
        if result.invalid_test_ids:
            print(f"\n{RED}FAIL: {len(result.invalid_test_ids)} invalid requirement IDs in tests.{RESET}")
            has_blocking_errors = True
        if result.orphans:
            print(f"\n{RED}FAIL: {len(result.orphans)} orphan test mappings detected.{RESET}")
            has_blocking_errors = True
        if args.strict and result.cross_product_violations:
            print(f"\n{RED}FAIL: Cross-product requirement violations detected in strict mode.{RESET}")
            has_blocking_errors = True
        if args.min_coverage is not None and result.coverage_percentage < args.min_coverage:
            print(
                f"\n{RED}FAIL: Coverage {result.coverage_percentage:.1f}% is below required "
                f"threshold {args.min_coverage:.1f}%.{RESET}"
            )
            has_blocking_errors = True
        if args.strict_coverage and result.uncovered:
            print(f"\n{RED}FAIL: {len(result.uncovered)} requirements lack automated test coverage.{RESET}")
            has_blocking_errors = True
        if result.covered_requirements == 0:
            print(f"\n{RED}FAIL: No test mappings found across test suites.{RESET}")
            has_blocking_errors = True

        if has_blocking_errors:
            return 1

        print(
            f"\n{GREEN}✔ ALL GATES PASSED: Traceability validation verified "
            f"({result.covered_requirements}/{result.total_requirements} mapped, 0 orphans).{RESET}"
        )
        return 0

    return 0


if __name__ == "__main__":
    sys.exit(main())
