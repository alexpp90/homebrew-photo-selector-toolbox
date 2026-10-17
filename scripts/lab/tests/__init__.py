"""Unit test package for scripts/lab — ensures scripts/lab is on sys.path."""

from __future__ import annotations

import sys
from pathlib import Path

_LAB_ROOT = str(Path(__file__).resolve().parent.parent)
if _LAB_ROOT not in sys.path:
    sys.path.insert(0, _LAB_ROOT)

import lab  # noqa: E402,F401
