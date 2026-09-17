## 2024-05-18 - Replacing Path.glob with os.scandir
**Learning:** `Path.glob` is very slow when called in a loop (e.g. for multiple files in the same directory), as it repeatedly iterates over the directory.
**Action:** Use `os.scandir` or `os.listdir` for faster directory scanning when you need to match files, especially if checking multiple patterns or files in the same directory.
## 2024-05-18 - Safe string matching for Path.stem
**Learning:** Refactoring `pathlib.Path.stem == stem` to use string operations for performance can introduce false positives for files with multiple extensions (e.g., `name.startswith(stem + ".")` incorrectly matches `photo.backup.jpg` to stem `photo`).
**Action:** Use `name.startswith(stem + ".") and name.rfind('.') == len(stem)` as a safe, highly efficient string-matching alternative to `.stem`.
