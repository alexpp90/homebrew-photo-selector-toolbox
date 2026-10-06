## 2026-10-06 - Verify single-pass Counter aggregation
**Learning:** Single-pass Counter accumulation during data extraction in `_extract_metadata_single_pass` eliminates intermediate list creations and double passes.
**Action:** Verify existing data flow before refactoring list comprehensions to avoid redundant or unnecessary changes.
