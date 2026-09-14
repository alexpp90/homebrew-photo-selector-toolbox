## 2024-09-14 - Fix command hijacking in zenity execution
**Vulnerability:** Command injection / PATH hijacking via bare binary name in `subprocess.run(["zenity", ...])`.
**Learning:** Checking for availability with `shutil.which("binary")` does not protect against execution time manipulation if the raw string `"binary"` is passed to `subprocess.run` (which resolves it via `PATH` again).
**Prevention:** Always capture the absolute path returned by `shutil.which()` and pass that explicit absolute path to `subprocess.run`.
