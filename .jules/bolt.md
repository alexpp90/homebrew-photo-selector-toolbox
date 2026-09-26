## 2024-05-24 - os.scandir vs Path.glob
**Learning:** `Path.glob` is significantly slower than `os.scandir` combined with fast string operations like `startswith` when scanning directories with many files. `Path.glob` incurs significant overhead due to redundant directory traversals and system calls.
**Action:** When finding files by stem or patterns, replace `Path.glob` with a single `os.scandir` or `os.listdir` pass. Use `name.startswith(stem + ".") and name.rfind('.') == len(stem)` for exact stem matching.
