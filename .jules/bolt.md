## 2026-10-10 - Refactored directory scanning with os.scandir
**Learning:** In Python, replacing os.walk with a custom recursive os.scandir implementation significantly improves directory traversal performance because os.scandir caches DirEntry attributes and avoids intermediate list allocations for directories and files.
**Action:** Always prefer os.scandir with careful exception handling for performance-critical directory walks.
