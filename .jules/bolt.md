## 2024-05-18 - ThreadPoolExecutor Overhead with Many Small Files
**Learning:** When calculating file hashes for duplicate detection, `ThreadPoolExecutor` has significant overhead that dominates the actual hashing time for small files (<5MB). A large library of thousands of small JPEGs/PNGs will be slower with a thread pool than processing them sequentially.
**Action:** Use a hybrid approach for file processing tasks: process small files sequentially and only dispatch large files (e.g., >= 5MB) to the `ThreadPoolExecutor`.
