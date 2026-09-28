## 2026-09-28 - Faster directory traversal
**Learning:** In Python, `os.scandir` is significantly faster than `os.walk` or `Path.glob` for scanning directories when we need to filter files by type and avoid redundant `stat()` calls, as `DirEntry` caches file attributes.
**Action:** Prefer `os.scandir` with custom recursion for performance-critical file discovery loops.
