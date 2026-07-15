## 2024-05-18 - Batching File I/O and DB updates across worker processes
**Learning:** Returning modified dictionaries across `ProcessPoolExecutor` futures is far more performant than making N+1 synchronous database writes in parallel workers.
**Action:** When delegating scanning/processing in python across worker processes, pre-fetch state using batch DB reads before starting the workers, pass the needed data inside the dictionary, return the new computed values, and perform batch updates on the main thread via accumulating dictionary entries.
