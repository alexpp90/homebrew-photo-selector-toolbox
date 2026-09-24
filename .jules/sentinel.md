## 2024-05-24 - Untrusted Search Path in Subprocess Commands
**Vulnerability:** The application was passing bare executable names (like 'zenity' or 'exiftool') to `subprocess.run()`.
**Learning:** This is vulnerable to PATH hijacking (Untrusted Search Path vulnerability) if an attacker can place a malicious executable with the same name earlier in the system PATH.
**Prevention:** Always use `shutil.which()` to resolve the absolute path of system binaries before passing them to subprocess execution functions.
