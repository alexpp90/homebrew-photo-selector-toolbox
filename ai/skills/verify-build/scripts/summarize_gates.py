#!/usr/bin/env python3
"""Summarise a run_tests.sh gate table into the sentence the retrospective requires.

The retrospective demands that every gate reported `⊘ SKIPPED` be named in the task summary
as an accepted risk. Agents forget. This turns the requirement into a command.

Usage:
    ./scripts/run_tests.sh 2>&1 | python3 ai/skills/verify-build/scripts/summarize_gates.py
    python3 ai/skills/verify-build/scripts/summarize_gates.py < build.log

Exit codes:
    0  no failures (skips and advisory warnings may exist and are reported)
    1  at least one gate failed
    2  no gate lines recognised in the input
"""

from __future__ import annotations

import re
import sys

ANSI = re.compile(r"\x1b\[[0-9;]*m")

# Classification is driven by run_tests.sh's leading status glyph, never by free text.
# Matching on words instead would misread the advisory line
# "! gradlew lintDebug (advisory — does not fail CI)" as a failure, and would count the
# closing "FAILED — one or more gates failed" banner as a gate of its own.
MARKERS = {"✔": "pass", "✓": "pass", "✘": "fail", "✗": "fail", "⊘": "skip", "!": "warn"}
MARKER_LINE = re.compile(r"^\s*([✔✓✘✗⊘!])\s+(?P<body>.+?)\s*$")
DASH_SPLIT = re.compile(r"\s+[—–]\s+")
# The summary table pads gate names to a fixed width, so a name longer than the padding
# runs straight into its CI reference with a single space. Recognise the reference shape
# directly, or the same gate is counted twice — once from the live line, once from the table.
CI_REF = re.compile(r"\s+((?:\S+\.ya?ml:\S+)|(?:ai/skills/\S+)|(?:—\s*$))\s*$")


def split_body(body: str) -> tuple[str, str]:
    """Split a gate line body into (name, trailing detail).

    Three shapes occur: `<name> — <reason>` for skips, `<name><padding><ci-ref>` in the
    summary table, and `<name> <ci-ref>` when the name overflows the padding.
    """
    dashed = DASH_SPLIT.split(body, maxsplit=1)
    if len(dashed) == 2:
        return dashed[0].strip(), dashed[1].strip()
    padded = re.split(r"\s{2,}", body, maxsplit=1)
    if len(padded) == 2:
        return padded[0].strip(), padded[1].strip()
    m = CI_REF.search(body)
    if m:
        return body[: m.start()].strip(), m.group(1).strip()
    return body.strip(), ""


def main() -> int:
    passed: list[str] = []
    skipped: list[tuple[str, str]] = []
    warned: list[str] = []
    failed: list[str] = []
    seen: set[tuple[str, str]] = set()

    for raw in sys.stdin:
        line = ANSI.sub("", raw.rstrip("\n"))
        m = MARKER_LINE.match(line)
        if not m:
            continue
        status = MARKERS[m.group(1)]
        name, detail = split_body(m.group("body"))
        if not name:
            continue
        key = (status, name)
        if key in seen:  # the table repeats each gate; count it once
            continue
        seen.add(key)

        if status == "pass":
            passed.append(name)
        elif status == "fail":
            failed.append(name)
        elif status == "warn":
            warned.append(name)
        else:
            skipped.append((name, detail or "no reason given"))

    if not (passed or skipped or failed or warned):
        print("No gate lines recognised. Paste the run_tests.sh summary table.", file=sys.stderr)
        return 2

    print(
        f"Gates: {len(passed)} passed, {len(skipped)} skipped, "
        f"{len(warned)} advisory, {len(failed)} failed\n"
    )

    if failed:
        print("FAILED — the task is not done. Do not push and let CI adjudicate:")
        for name in failed:
            print(f"  - {name}")
        print()

    print("Paste into the task summary:")
    print("-" * 60)
    if failed:
        print(f"Verification INCOMPLETE: {', '.join(failed)} failed locally.")
    elif skipped:
        names = ", ".join(n for n, _ in skipped)
        print(f"./scripts/run_tests.sh passed. Gates not run locally, accepted as risk: {names}.")
        for name, reason in skipped:
            print(f"  - {name}: {reason}")
        print("See docs/build/CI_PARITY.md for which of these are legitimately un-runnable.")
    else:
        print("./scripts/run_tests.sh passed with no skipped gates.")
    print("-" * 60)

    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
