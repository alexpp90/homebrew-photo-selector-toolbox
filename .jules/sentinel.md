## 2025-10-24 - Fix PATH hijacking in zenity execution
**Vulnerability:** Untrusted Search Path when calling 'zenity' in subprocess.
**Learning:** Relying on the bare binary name allows execution of malicious binaries placed earlier in the system PATH.
**Prevention:** Always resolve the absolute path using shutil.which() before execution.
