## 2026-10-06 - Lazy Subfolder Resolution in MoveToSelectionUseCase
**Learning:** Deferring DocumentFile.findFile and createDirectory calls via lazy delegation avoids IPC overhead for unused subfolders during batch file moves.
**Action:** Use lazy initialization for directory queries in DocumentFile operations when subfolders are conditionally accessed.
