## 2024-05-24 - Optimization: O(1) iterative state tracking vs O(N) dict comprehension
**Learning:** Pre-caching file metadata (like mtime and prefix) using dictionary comprehensions over the entire list of files introduces significant overhead. Because the `group_files_by_similarity` function traverses files sequentially, iterating and tracking state avoids O(N) dictionary lookup times and memory overhead.
**Action:** Replace `mtimes` and `prefixes` dict comprehension logic with state tracking iteratively.
