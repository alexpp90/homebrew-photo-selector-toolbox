## $(date +%Y-%m-%d) - Batched Cache Writes in Loop
**Learning:** Performing database or disk cache writes inside a tight loop creates severe N+1 bottlenecks.
**Action:** Always accumulate the results into a data structure (e.g., a dictionary or list) during the loop, and write them in a single batched transaction (like `set_multiple_scores`) outside the loop.
