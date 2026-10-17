"""Command-line interface dispatcher for scripts/lab/lab.py."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from . import _compat

_compat.install_fallbacks_if_needed()

import yaml  # noqa: E402


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="lab.py",
        description="Photo Selector Toolbox E2E Device Lab CLI",
    )
    sub = parser.add_subparsers(dest="subcommand")

    # preflight (C-00, C-32)
    p_pre = sub.add_parser("preflight", help="Check host and device toolchains for lab execution")
    p_pre.add_argument("--product", default=None, choices=["android-desktop", "phototok", "macos", "linux"])

    # fixtures (C-02)
    p_fix = sub.add_parser("fixtures", help="Generate deterministic synthetic fixture sets")
    p_fix.add_argument("--out", required=True, help="Target root directory (e.g. /tmp/pstlab)")
    p_fix.add_argument("--set", dest="only_set", default=None, help="Generate only the named fixture set")
    p_fix.add_argument("--seed", type=int, default=42, help="Deterministic RNG seed (default: 42)")

    # checkpoint (C-00, C-13)
    p_chk = sub.add_parser("checkpoint", help="Capture a live checkpoint from an Android device")
    p_chk.add_argument("--serial", required=True, help="ADB device serial")
    p_chk.add_argument("--name", required=True, help="Checkpoint name")
    p_chk.add_argument("--out", required=True, help="Output directory")

    # lint / layout-lint (C-03)
    for cmd_name in ("lint", "layout-lint"):
        p_lint = sub.add_parser(cmd_name, help="Run deterministic layout lint (R1-R8) on a tree.json")
        p_lint.add_argument("tree_json", help="Path to checkpoint .tree.json")
        p_lint.add_argument(
            "--product", default=None, help="Product name (e.g. android-desktop, phototok, macos, linux)"
        )
        p_lint.add_argument("--rules", default=None, help="Path to product rules YAML or product name")
        p_lint.add_argument("--checkpoint", default="checkpoint", help="Checkpoint name")
        p_lint.add_argument("--expect", default=None, help="Optional JSON string or YAML file with layout_expect")

    # oracle (C-04)
    p_ora = sub.add_parser("oracle", help="Run file-system oracle against a checkpoint fs.json")
    p_ora.add_argument("fs_json", help="Path to checkpoint .fs.json")
    p_ora.add_argument("--expect", required=True, help="JSON or YAML fs_expect specification")
    p_ora.add_argument("--initial", default=None, help="Optional path to 00_initial.fs.json")

    # report (C-06)
    p_rep = sub.add_parser("report", help="Render self-contained report.html for a run directory")
    p_rep.add_argument("run_dir", help="Path to lab run directory")

    # review-bundle (C-20)
    p_bun = sub.add_parser("review-bundle", help="Build AI review bundles from a lab run directory")
    p_bun.add_argument("run_dir", help="Path to lab run directory")
    p_bun.add_argument("--all", action="store_true", help="Include all checkpoints even if unchanged")
    p_bun.add_argument("--max-images", type=int, default=15, help="Max images per bundle (default: 15)")

    # review-ingest (C-21, C-22)
    p_ing = sub.add_parser("review-ingest", help="Extract or ingest AI review findings")
    p_ing.add_argument("run_dir", nargs="?", default=None, help="Path to lab run directory")
    p_ing.add_argument("--extract", default=None, help="Extract JSON array from raw LLM text file")
    p_ing.add_argument("--out", default=None, help="Output findings.json path when using --extract")

    # triage (C-22)
    p_tri = sub.add_parser("triage", help="Record an accepted finding ID in known_findings.yaml")
    p_tri.add_argument("run_dir", help="Path to lab run directory")
    p_tri.add_argument("finding_id", help="12-hex finding ID")
    p_tri.add_argument("--reason", required=True, help="Reason for accepting the finding")
    p_tri.add_argument("--until", default="2027-01-01", help="Expiry date YYYY-MM-DD")

    # run (C-60)
    p_run = sub.add_parser("run", help="Run E2E device lab journeys")
    p_run.add_argument("--all", action="store_true", help="Run all four products")
    p_run.add_argument("--product", default=None, choices=["android-desktop", "phototok", "macos", "linux"])
    p_run.add_argument("--journey", default=None, help="Filter by journey ID")
    p_run.add_argument("--config", default=None, help="Filter by config name")
    p_run.add_argument("--out", default=None, help="Explicit output run directory")
    p_run.add_argument("--installed", action="store_true", help="Use installed package binary (C-50)")

    return parser


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)

    if not args.subcommand:
        parser.print_help()
        return 0

    if args.subcommand == "preflight":
        import shutil

        tools = {
            "python": sys.version.split()[0],
            "adb": shutil.which("adb") or "missing",
            "maestro": shutil.which("maestro") or "missing",
            "swift": shutil.which("swift") or "missing",
        }
        print(json.dumps(tools, indent=2))
        return 0

    if args.subcommand == "fixtures":
        from .fixtures import CorpusMissingError, generate_fixtures, load_spec

        out_root = Path(args.out).expanduser().resolve()
        if args.only_set:
            generate_fixtures(args.only_set, out_root / args.only_set, seed=int(args.seed))
        else:
            spec = load_spec()
            for set_name in spec.get("sets", {}):
                try:
                    generate_fixtures(set_name, out_root / set_name, seed=int(args.seed))
                except CorpusMissingError:
                    continue
        return 0

    if args.subcommand in ("lint", "layout-lint"):
        from .layout_lint import lint, load_rules
        from .model import Tree

        tree_path = Path(args.tree_json)
        tree_obj = Tree.from_dict(json.loads(tree_path.read_text(encoding="utf-8")))
        rule_spec = args.rules or args.product
        if not rule_spec:
            print("error: specify --product or --rules", file=sys.stderr)
            return 2
        rules_p = Path(rule_spec)
        if rules_p.is_file():
            rules_cfg = yaml.safe_load(rules_p.read_text(encoding="utf-8")) or {}
        else:
            rules_cfg = load_rules(rule_spec)
        expect_cfg = None
        if args.expect:
            exp_p = Path(args.expect)
            if exp_p.is_file():
                expect_cfg = yaml.safe_load(exp_p.read_text(encoding="utf-8"))
            else:
                expect_cfg = json.loads(args.expect)
        findings = lint(
            tree_obj,
            rules_cfg,
            expect_cfg,
            product=args.product or str(rule_spec),
            checkpoint=args.checkpoint,
        )
        print(json.dumps([f.to_dict() for f in findings], indent=2))
        return 1 if findings else 0

    if args.subcommand == "oracle":
        from .fs_oracle import evaluate_fs_oracle

        fs_data = json.loads(Path(args.fs_json).read_text(encoding="utf-8"))
        exp_p = Path(args.expect)
        exp_data = yaml.safe_load(exp_p.read_text(encoding="utf-8")) if exp_p.is_file() else yaml.safe_load(args.expect)
        init_data = json.loads(Path(args.initial).read_text(encoding="utf-8")) if args.initial else None
        findings = evaluate_fs_oracle(fs_data, exp_data, initial_fs=init_data)
        print(json.dumps([f.to_dict() for f in findings], indent=2))
        return 1 if findings else 0

    if args.subcommand == "checkpoint":
        from .android.adb import AdbClient
        from .android.runner import capture_android_checkpoint

        adb = AdbClient(serial=args.serial)
        capture_android_checkpoint(
            adb=adb,
            idx=1,
            name=args.name,
            out_dir=Path(args.out),
            device_fixture_root="/sdcard/Pictures/PSTLab/smoke",
        )
        return 0

    if args.subcommand == "report":
        from .report import generate_report

        _, html_path = generate_report(Path(args.run_dir))
        print(str(html_path))
        return 0

    if args.subcommand == "review-bundle":
        from .review_bundle import build_review_bundles

        bundles = build_review_bundles(
            Path(args.run_dir),
            include_all=bool(args.all),
            max_images=int(args.max_images),
        )
        for b in bundles:
            print(str(b))
        return 0

    if args.subcommand == "review-ingest":
        from .ingest import extract_findings_from_raw, ingest_review_bundles

        if args.extract:
            if not args.out:
                print("error: --out is required with --extract", file=sys.stderr)
                return 2
            extract_findings_from_raw(Path(args.extract), Path(args.out))
            return 0
        if not args.run_dir:
            print("error: run_dir is required unless --extract is used", file=sys.stderr)
            return 2
        ingest_review_bundles(Path(args.run_dir))
        return 0

    if args.subcommand == "triage":
        from .ingest import triage_finding

        triage_finding(Path(args.run_dir), args.finding_id, reason=args.reason, until=args.until)
        return 0

    if args.subcommand == "run":
        from .orchestrate import ALL_PRODUCTS, run_lab

        if args.all:
            products = list(ALL_PRODUCTS)
        elif args.product:
            products = [args.product]
        else:
            print("error: specify --all or --product <name>", file=sys.stderr)
            return 2
        run_lab(
            products=products,
            journey_filter=args.journey,
            config_filter=args.config,
            out_dir=Path(args.out) if args.out else None,
            installed=bool(args.installed),
        )
        return 0

    return 2
