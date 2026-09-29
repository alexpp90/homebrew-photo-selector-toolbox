## 2026-09-29 - Recursive Directory Traversal
**Learning:** Replacing `os.walk` with a custom recursive `os.scandir` function significantly reduces overhead during directory traversal by avoiding intermediate list creations and tuple unpacking.
**Action:** Use a nested `_scan` function with `os.scandir` instead of `os.walk` for performance-critical file discovery, ensuring correct mocking of `os.scandir` as a context manager in tests.
## 2026-09-29 - Recursive Directory Traversal Flake8 Errors
**Learning:** When adding optimization comments and breaking long lines in nested blocks, ensure the line length does not exceed Flake8 limits (120 chars) and that no trailing whitespaces are left on blank lines (W293).
**Action:** Use multi-line parentheses for long `if` conditions in deeply nested logic and carefully trim all trailing spaces in search/replace blocks.
