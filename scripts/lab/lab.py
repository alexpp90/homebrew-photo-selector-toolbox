#!/usr/bin/env python3
"""Entry point for the Photo Selector device lab CLI."""

from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from lab.cli import main  # noqa: E402


if __name__ == "__main__":
    raise SystemExit(main())
