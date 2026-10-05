## 2026-10-03 - Caching Directory Listings for File Discovery
**Learning:** Repeatedly calling directory scanning functions like `os.scandir` in loops for multiple files in the same directory incurs unnecessary disk I/O. Caching directory filenames using `@lru_cache` keyed on path and `st_mtime_ns` avoids redundant filesystem traversals.
**Action:** Always wrap directory scanning helpers in LRU caches with directory modification time keys when operating on multiple files in common parent directories.
