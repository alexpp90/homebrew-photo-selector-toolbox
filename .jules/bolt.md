## 2024-05-24 - File Discovery Overhead
**Learning:** In Tkinter/Python desktop apps processing thousands of local files, `pathlib.Path.glob` is a significant performance bottleneck due to redundant directory traversals and regex parsing overhead.
**Action:** Always prefer a single `os.scandir` or `os.listdir` pass combined with manual string matching (`startswith`, `endswith`) for bulk local file discovery in performance-critical paths.
