## 2025-02-23 - Fix N+1 Query in dHash Preloading
**Learning:** Calling SQLite caching queries individually inside a preloading loop causes an N+1 query performance issue and introduces unnecessary overhead per file.
**Action:** Use batch processing functions like `get_multiple_scores` before entering loops to load data into memory once, retrieving items directly from the dictionary, changing lookup from O(N*DB_latency) to O(1) dictionary lookups.
