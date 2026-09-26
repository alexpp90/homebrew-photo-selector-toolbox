## 2024-05-18 - Prevent Untrusted Search Path Vulnerabilities
**Vulnerability:** Calling external commands (like `zenity` or `exiftool`) via `subprocess.run` using just their basename allows an attacker to hijack execution if they can alter the PATH environment variable or place a malicious executable earlier in the PATH.
**Learning:** Explicitly resolving the absolute path of system binaries using `shutil.which` before execution ensures we are invoking the expected system utility, rather than relying on a potentially untrusted runtime environment.
**Prevention:** Always use `shutil.which()` to retrieve the absolute path before passing external command names to `subprocess.Popen` or `subprocess.run`.
