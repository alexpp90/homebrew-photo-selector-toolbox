1. **Optimize O(N) array search in `_update_scan_state`**
   - The method `_update_scan_state` in `src/photo_selector_toolbox/sharpness_gui.py` currently iterates over the entire `self.scan_results` list (O(N)) for every new `ScanResult` to check if a result for that path already exists. This can be significantly slow for large directories (e.g., thousands of photos).
   - I will introduce a dictionary `self._scan_results_idx_map` that maps `Path` to the list index in `self.scan_results` for O(1) lookups.
   - I will update `_load_folder_contents`, `clear_scores_in_memory` and `execute_delete` to initialize, clear and maintain the map properly.
   - I will update `_update_scan_state` to use `self._scan_results_idx_map` to find if the element exists instead of an O(N) loop.
2. **Verify Changes**
   - Use `read_file` to verify the changes were written correctly to `src/photo_selector_toolbox/sharpness_gui.py`.
3. **Run Tests**
   - Run the relevant test suite (using `xvfb-run pytest`) to ensure the changes are correct and haven't introduced regressions.
4. **Complete pre-commit steps**
   - Complete pre-commit steps to ensure proper testing, verification, review, and reflection are done.
5. **Submit**
   - Submit the PR with the performance improvements.
