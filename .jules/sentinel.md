## 2025-05-24 - Prevent Path Hijacking in Subprocess Executions
**Vulnerability:** The application executed external binaries (like `zenity` and `exiftool`) using just their bare names instead of absolute paths.
**Learning:** Using bare names for executables combined with `subprocess` calls allows attackers to place malicious binaries of the same name in the current working directory or higher in the system `PATH`, resulting in unauthorized code execution (Untrusted Search Path vulnerability).
**Prevention:** Always use `shutil.which("binary_name")` to resolve the absolute path before passing it to `subprocess.run`, `subprocess.Popen`, or returning it for external execution.
