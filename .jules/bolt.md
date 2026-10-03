## 2026-07-27 - Recursive os.scandir Traversal for Directory Walking
**Learning:** os.walk incurs performance overhead compared to os.scandir when single-pass traversal is needed because os.scandir yields DirEntry objects with cached attributes (is_file, is_dir), avoiding extra stat calls and intermediate list allocations.
**Action:** Replaced os.walk with a recursive os.scandir function when populating folder contents in sharpness_tool.py for faster single-pass directory scanning.
