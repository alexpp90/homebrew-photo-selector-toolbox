## 2025-02-18 - String Slicing and OS Calls vs Pathlib Overheads
**Learning:** In loops over files (e.g. grouping UI lists), instantiating `pathlib.Path` objects just to call `.stem` or `p.stat().st_mtime` adds massive cumulative performance overhead compared to basic string manipulation (`name.rsplit(".", 1)[0]`) and `os.stat`. Additionally, wrapping a function in `@functools.lru_cache` provides zero benefit if the input parameter is always unique across the list iteration (e.g., unique full file names passed to `get_name_prefix`), merely adding cache management overhead.
**Action:** When working with thousands of files inside loops, bypass `pathlib` for lightweight extraction and stick to `os` and `str` methods. Always ensure that functions marked with `lru_cache` actually receive overlapping input values before adding the decorator.
## 2025-02-18 - Single-Pass Metadata Extraction in Analyzer
**Learning:** The previous implementation of `analyze_data` and `analyze_data_json` performed multiple redundant iterations over the entire dataset using list comprehensions (`get_values` with `getattr`), resulting in an O(M*N) complexity overhead.
**Action:** Replace multiple sequential loops over the same array with a single-pass extraction helper. Utilizing direct attribute access instead of `getattr` inside a single loop provides significant performance boosts when extracting attributes from hundreds of thousands of objects.
## 2025-02-18 - Batching Cache Writes during Background Analysis
**Learning:** During background UI tasks (e.g. `_preload_all_metadata_and_dhashes` or `run_calc`), executing single `cache.set_scores()` updates sequentially inside a loop over files creates an N+1 write bottleneck on the SQLite database, drastically increasing latency.
**Action:** When updating cache during iterations, use a dictionary to accumulate updates and flush them using `cache.set_multiple_scores(updates)` after the loop. Ensure that the accumulated dictionary is flushed even when breaking out of loops early (e.g. `if self.stop_event.is_set():`) to prevent silent data loss.
## 2025-02-18 - Atomic JSON Merging in SQLite Cache
**Learning:** The previous implementation of `set_scores` and `set_multiple_scores` in `ScoreCache` suffered from N+1 query patterns because it fetched existing JSON records into Python, merged them with `dict.update()`, and then wrote them back. This caused significant database I/O latency, especially for batched cache updates.
**Action:** Always use SQLite's native `json_patch()` function directly inside the `INSERT ... ON CONFLICT DO UPDATE SET` clause. This allows for atomic merging entirely within the database engine and eliminates the need for any preliminary `SELECT` statements or client-side parsing/serialization, resulting in a ~2-3x speedup on cache writes.

## 2023-10-27 - Parallelize IO/CPU loops
**Learning:** Sequential loops containing blocking I/O (like reading EXIF via `get_exif_data`) and CPU operations (like dHash) bottleneck UI and background thread performance severely.
**Action:** Use `concurrent.futures.ThreadPoolExecutor` to parallelize the iteration. Define a pure worker function that returns extracted data, and use `as_completed` in the main loop to safely apply the results to the shared model, preserving GIL/thread safety.

## 2024-05-18 - Optimized list containment checks within a loop
**Learning:** Doing an O(N) list containment check (using `in`) inside an O(N) loop results in an O(N^2) operation, causing major performance bottlenecks when handling large item sets (like files in a directory).
**Action:** Always pre-convert lists to sets before using them for repeated containment checks inside loops to reduce the inner operation to O(1) and the overall complexity to O(N).

## 2025-02-18 - Avoid Path.resolve() overhead in loops
**Learning:** `Path.resolve()` interacts with the OS file system at each segment to check for symlinks, which introduces significant latency in bulk/iterative operations (like resolving cache keys for a large batch of images).
**Action:** Use `os.path.abspath()` instead of `Path.resolve()` when generating absolute string paths in performance-critical code where strict symlink resolution is not strictly necessary.
