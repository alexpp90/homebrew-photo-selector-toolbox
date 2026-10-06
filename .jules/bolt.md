## 2026-10-06 - Generator Expression in Counter Instantiation
**Learning:** Passing generator expressions directly into `Counter()` eliminates temporary list allocations in memory.
**Action:** Prefer generator expressions over list comprehensions when feeding single-pass aggregators like Counter.
