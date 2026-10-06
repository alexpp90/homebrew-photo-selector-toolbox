## 2025-03-09 - Ensure Cancellation Responsiveness in I/O Loops
**Learning:** In Kotlin coroutines doing streaming I/O or loop operations inside try/catch blocks, calling ensureActive() inside the loop prevents coroutine cancellation stalling, and catching CancellationException explicitly before general Exception ensures cancellation exceptions are not swallowed.
**Action:** Always place ensureActive() inside tight loops and catch CancellationException before generic Exception in suspend functions.
