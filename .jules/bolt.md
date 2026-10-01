## 2024-05-24 - Avoid O(N) pre-caching in sequential comparisons
**Learning:** Pre-caching metadata (like file mtimes) into dictionaries using comprehensions for a list of items causes memory and O(N) lookup overhead. In sequential iteration (comparing adjacent items), tracking state iteratively is significantly faster.
**Action:** Use O(1) iterative state tracking by carrying over the previous item's metadata to the next iteration.
