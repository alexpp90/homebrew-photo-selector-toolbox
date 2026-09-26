## 2026-09-20 - Suboptimal Directory Traversal in Sharpness Tool
**Learning:** `os.walk` generates full file lists and directory lists as tuples on every step, whereas `os.scandir` yields `DirEntry` objects that cache OS metadata (such as file/dir status). Using recursive `os.scandir` yields a ~1.25x-1.3x speedup during image folder scans.
**Action:** Replace `os.walk` with recursive `os.scandir` in folder indexing routines to reduce overhead and file system stat calls.
