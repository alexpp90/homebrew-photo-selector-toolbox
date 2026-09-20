## 2025-05-10 - Unblocking UI Progress with concurrent.futures.as_completed
**Learning:** Using `executor.map` in GUI progress loops blocks iteration until tasks complete in their strict submission order. If early tasks (such as processing large RAW images) take longer, finished results behind them are blocked from updating the progress bar.
**Action:** Replace `executor.map` with `concurrent.futures.as_completed(futures)` when collecting multi-threaded results that drive UI progress updates.
