## 2025-02-28 - Flag Injection in Subprocess Run
**Vulnerability:** Constructing command arguments by concatenating flags and user inputs (e.g., `f"--title={title}"`) within `subprocess.run` argument lists.
**Learning:** Even with `shell=False`, complex CLI tools like `zenity` might parse embedded flags or unexpected characters in concatenated strings maliciously if not strictly separated. The list items act as boundaries preventing parameter bleed.
**Prevention:** Always separate flags and user-controlled values into distinct list items (e.g., `["--title", title]`) when using `subprocess` calls.
