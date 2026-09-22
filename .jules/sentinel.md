## 2025-02-14 - Prevent Command Injection in subprocess calls
**Vulnerability:** Untrusted executables could be executed via subprocess calls if they exist in the current working directory, as paths were not fully resolved before being passed to `subprocess.run()`.
**Learning:** Python's `subprocess.run` without absolute paths is susceptible to untrusted search path vulnerabilities (CWE-426), particularly when `shell=True` is used or when relying on PATH. Even when `shell=False`, executing commands like `"zenity"` directly instead of resolving them fully can be problematic.
**Prevention:** Always use `shutil.which()` to get the absolute path to an executable before passing it to `subprocess.run()`, and pass arguments as a list of strings.
