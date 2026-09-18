## 2024-09-18 - Untrusted Search Path Vulnerability in subprocess

**Vulnerability:** Found `subprocess.run` executing a system binary (`zenity`) using its bare name (`"zenity"`).
**Learning:** This exposes the application to PATH hijacking, where a malicious executable named `zenity` placed earlier in the user's `PATH` could be executed instead of the intended system binary.
**Prevention:** Always resolve the absolute path of system binaries using `shutil.which` before passing them to `subprocess` functions.
