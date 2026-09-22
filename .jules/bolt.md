## 2024-05-18 - Faster single-pass directory traversal
**Learning:** Using `Path.glob` with complex or multiple patterns results in significant overhead due to redundant directory traversals and system calls, especially in large directories.
**Action:** Prefer `os.scandir` combined with fast string matching (`name.startswith(stem + ".") and name.rfind('.') == len(stem)`) for a much faster single-pass discovery in performance-critical file searches.
