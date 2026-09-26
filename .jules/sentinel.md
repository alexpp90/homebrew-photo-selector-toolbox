## 2026-03-30 - Prevent PATH Hijacking in Subprocess Binaries
**Learning:** Calling bare binary names (e.g. `zenity`) in `subprocess.run` relies on system PATH resolution, which can allow an attacker who manipulates the process PATH environment variable to execute untrusted executables.
**Action:** Always resolve system binaries using `shutil.which` and pass the resolved absolute binary path to `subprocess.run` (e.g. `cmd = [zenity_path, ...]`).
