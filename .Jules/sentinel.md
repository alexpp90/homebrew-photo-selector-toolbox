
## 2026-07-31 - Fix Broad Activity Export in AndroidManifest
**Vulnerability:** Android activity (MainActivity) was exported broadly, exposing standard intent filters (MAIN, LAUNCHER) and potential application surface.
**Learning:** Implementing `activity-alias` allows exposing just the launcher intent while keeping the actual activity unexported for defense-in-depth.
**Prevention:** Use an unexported activity along with an exported `activity-alias` for intent filters instead of directly exporting main application activities.
