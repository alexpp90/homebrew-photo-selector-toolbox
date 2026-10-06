## 2026-07-27 - Hoist Invariant Checks Out of Loop
**Learning:** Evaluating volatile or synchronized property checks like `aestheticAnalyzer.isAvailable()` inside a loop over cached items introduces unnecessary method call overhead for every image item.
**Action:** Hoist loop-invariant conditions out of cache evaluation loops into local variables before iteration.
