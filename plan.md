1. *Modify imports in `src/photo_selector_toolbox/controllers.py`*
   - Add `Tuple` and `Union` to `typing` imports to correctly type hint the return of `_process_single_file`.
2. *Refactor `_process_single_file` in `src/photo_selector_toolbox/controllers.py`*
   - Add an optional `cached_scores` parameter. If provided, skip the per-file SQLite read.
   - Remove the `cache.set_scores(f, new_calculations)` write inside the function.
   - Return a `Tuple[ScanResult, Dict[str, Union[float, str]]]` containing the `ScanResult` and any `new_calculations`.
3. *Optimize `_scan_worker` in `src/photo_selector_toolbox/controllers.py`*
   - Before starting workers, batch-read all cached scores using `ScoreCache().get_multiple_scores(files)`.
   - Pass the prefetched scores to each `_process_single_file` call.
   - Accumulate `new_calculations` from the `future.result()` in the main thread.
   - Batch write accumulated scores to `ScoreCache().set_multiple_scores()` periodically (e.g., every 50) and before any early exit/cancellation to prevent silent data loss.
4. *Optimize `_background_update_worker` in `src/photo_selector_toolbox/sharpness_gui.py`*
   - Pre-fetch scores using `get_multiple_scores(files)`.
   - Update the executor to pass the cached scores and unpack the new tuple return type.
   - Accumulate and batch write new calculations similarly to `_scan_worker`, ensuring flushes on `bg_stop_event.is_set()`.
5. *Run tests and linting*
   - Run `poetry run pytest` to ensure `ScanResult` and scan processes are unaffected.
   - Run `poetry run flake8 src/ tests/` to ensure no unused imports or syntax issues.
6. *Complete pre-commit steps to ensure proper testing, verification, review, and reflection are done.*
7. *Submit the Pull Request*
   - Create a PR with the title '⚡ Bolt: Optimize caching I/O in worker processes' and the required Bolt markdown structure.
