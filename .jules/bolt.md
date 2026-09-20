## 2026-09-20 - Out-of-Order Parallel Execution with concurrent.futures.as_completed

**Learning:** `concurrent.futures.ThreadPoolExecutor.map` forces results to be returned in sequential order, blocking UI progress updates if early tasks are slow or delayed. Replacing it with `concurrent.futures.as_completed` yields finished tasks immediately regardless of input ordering, drastically reducing latency for progress updates and improving UI responsiveness.
**Action:** When collecting parallel task results in GUI or CLI applications that display real-time progress, prefer `as_completed` over `executor.map` unless strict item order preservation is required.
