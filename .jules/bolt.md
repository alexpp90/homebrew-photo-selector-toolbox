## 2026-06-21 - Optimize `get_multiple_scores` last_used update
**Learning:** `executemany` might not always yield drastic latency improvements compared to chunked `IN` queries (e.g., when doing individual scalar row updates on SQLite). The DB engine overhead for `executemany` parameter mapping and cursor loops can sometimes match manual `IN` chunks.
**Action:** Always measure before claiming a speedup. Refactoring to `executemany` removes explicit chunk loops, simplifies the Python code significantly, and prevents string building overhead, but does not always reduce execution time.
