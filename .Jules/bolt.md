## 2024-05-24 - Coroutine Parallelization for I/O bounds

**Learning:** When dealing with multiple independent file operations (such as copying/moving files), executing them sequentially inside a standard `for` loop leaves significant performance on the table.
**Action:** Always prefer launching a `kotlinx.coroutines.async` block per item mapped to a list, followed by an `.awaitAll()` when performing a batch of independent file I/O or network requests.
