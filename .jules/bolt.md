## 2026-09-24 - Optimize Directory Traversal
**Learning:** `os.listdir` combined with fast string operations (like `startswith`) is significantly faster than `Path.glob` or `os.scandir` for finding related files in directories with thousands of files. `Path.glob` incurs significant overhead due to redundant traversals and object instantiations.
**Action:** Prefer a single `os.listdir` pass with fast string checking when filtering a large number of files by filename prefixes.
