## 2025-05-18 - Single Pass CSV Row Generation

**Learning:** When generating CSV output from collections with optional or dynamically key-based schemas (e.g. `r.scores.keys()`), caching row elements in a single pass trades list iteration overhead for memory allocations.
**Action:** Measure micro-benchmarks carefully when considering single-pass vs. two-pass collection traversals; two-pass streaming can often be more memory-efficient and CPU-fast for large in-memory dataset iterations.
