## 2024-10-12 - Refactored directory traversal to use os.scandir
**Learning:** Using os.scandir directly instead of os.walk is vastly more performant because it avoids unnecessary intermediate allocations for all directories and files and provides properties without secondary stat operations.
**Action:** Use recursive os.scandir wrappers where directory traversal is needed.
