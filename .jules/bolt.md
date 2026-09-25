## 2026-09-25 - Optimize File Discovery in find_related_files
**Learning:** In directories with thousands of files, multiple passes using `Path.glob` for specific prefixes is significantly slower than a single `os.scandir` pass combined with efficient string operations.
**Action:** Prefer `os.scandir` with single-pass string matching (e.g. `startswith`) over repetitive `Path.glob` calls for performance-critical file discovery in large directories.
