## 2023-10-26 - ThreadPoolExecutor Overhead
**Learning:** Using ThreadPoolExecutor for thousands of fast CPU/IO operations (like hashing small files) incurs significant overhead that dwarfs the execution time.
**Action:** Use a hybrid approach: process small items sequentially and only dispatch large items to the ThreadPoolExecutor.
