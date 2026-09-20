## 2025-05-18 - Removing dead code functions
**Learning:** Functions used only in tests but nowhere in production code should be removed along with their corresponding unit tests to reduce maintenance burden and improve code health.
**Action:** Always verify with `grep` across the codebase to ensure a function is truly dead before removing it and its unit tests.
