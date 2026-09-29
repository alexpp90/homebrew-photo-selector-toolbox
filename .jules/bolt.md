## 2026-09-29 - Recursive Directory Traversal
**Learning:** Replacing `os.walk` with a custom recursive `os.scandir` function significantly reduces overhead during directory traversal by avoiding intermediate list creations and tuple unpacking.
**Action:** Use a nested `_scan` function with `os.scandir` instead of `os.walk` for performance-critical file discovery, ensuring correct mocking of `os.scandir` as a context manager in tests.
