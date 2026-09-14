## 2024-05-13 - Path.glob Overhead
**Learning:** `Path.glob` overhead is significant in directory scans (O(N) system calls under the hood, especially when called repeatedly per file). It iterates the file tree to find matches each time.
**Action:** When finding related files, avoid using `Path.glob` repeatedly. Use a single pass of `os.listdir()` and cache the results if doing this in a loop, or use basic string checks which are much faster.
