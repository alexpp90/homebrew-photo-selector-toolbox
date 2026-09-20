## 2024-05-24 - Prevent PATH Hijacking with Absolute Paths
**Vulnerability:** Calling system commands via `subprocess.run` using bare executable names (e.g., `["zenity", ...]`).
**Learning:** This exposes the application to Untrusted Search Path vulnerabilities, where an attacker could place a malicious executable named `zenity` earlier in the system PATH.
**Prevention:** Always use `shutil.which` to resolve the absolute path to the system binary before passing it to `subprocess` functions.
