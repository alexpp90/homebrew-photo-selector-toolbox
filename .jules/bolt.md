## 2025-05-18 - Replacing os.walk with os.scandir for directory scanning
**Learning:** `os.scandir` yields `DirEntry` objects that cache file attributes and entry types, avoiding redundant `stat()` calls and intermediate memory allocations compared to `os.walk`.
**Action:** When performing recursive directory searches for target file extensions in performance-sensitive code, use `os.scandir` in a recursive helper function instead of `os.walk`.
