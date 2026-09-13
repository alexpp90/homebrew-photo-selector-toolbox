## 2024-09-13 - Path.glob Performance Bottleneck
**Learning:** Using `Path.glob` multiple times per file inside a directory iteration is significantly slower than a single `os.listdir()` pass combined with string prefix matching (`startswith`), especially for directories with thousands of files. `Path.glob` incurs high overhead due to redundant directory scans and system calls.
**Action:** Prefer `os.listdir()` with string operations (like `startswith`) over `Path.glob` when querying specific file variants in a tight loop or when bulk processing large directories.
