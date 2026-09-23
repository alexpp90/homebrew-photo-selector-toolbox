## 2024-05-23 - Prevent Untrusted Search Path (PATH hijacking)
**Vulnerability:** Untrusted Search Path (PATH hijacking) in `subprocess.run` when executing `zenity` with a bare binary name.
**Learning:** Relying on the system `PATH` variable to locate a binary during execution can allow attackers to place a malicious executable in a directory earlier in the `PATH`, executing arbitrary code with the user's privileges.
**Prevention:** Always resolve and use the absolute path of the binary (e.g., using `shutil.which`) before passing it to `subprocess.run`.
