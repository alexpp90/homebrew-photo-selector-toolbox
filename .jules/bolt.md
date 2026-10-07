## 2023-10-07 - Replace os.walk with os.scandir
**Learning:** In Python, replacing `os.walk` with a custom recursive `os.scandir` implementation significantly improves directory traversal performance because `os.scandir` caches `DirEntry` attributes and avoids intermediate list allocations for directories and files.
**Action:** Use `os.scandir` directly for faster, recursive directory traversal instead of `os.walk`.
