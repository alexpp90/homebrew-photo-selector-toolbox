## 2024-08-01 - ActivityNotFoundException Crash

**Vulnerability:** Unsafe intent launch via `startActivity` can crash the app if no activity handles the intent.
**Learning:** Always handle the potential for `ActivityNotFoundException` when using implicit intents, especially for opening URLs, as users might not have a web browser or matching app installed. Wrap `startActivity` in a `try-catch` block for a robust fallback.
**Prevention:** Always test intent resolution and ensure `try-catch` blocks are in place for `startActivity` calls with external URLs.
