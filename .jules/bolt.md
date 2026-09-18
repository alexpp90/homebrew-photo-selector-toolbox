## 2024-10-24 - Fast file discovery using os.scandir
**Learning:** Using `Path.glob` for file discovery in directories with thousands of files incurs significant overhead due to redundant directory traversals.
**Action:** Prefer `os.scandir` combined with fast string operations over `Path.glob` for single-pass directory traversal. Use `name.startswith(stem + ".") and name.rfind('.') == len(stem)` for exact stem matching.
