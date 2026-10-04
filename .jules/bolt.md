## 2025-05-18 - Single-Pass Counter Aggregation
**Learning:** Accumulating Counter instances during initial EXIF list traversal eliminates redundant list allocations and subsequent Counter construction passes.
**Action:** Aggregate frequency counters directly in single-pass data extractors when frequency distributions are required.
