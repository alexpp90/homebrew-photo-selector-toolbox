## 2025-02-28 - Secure Archive Extraction (Zip/Tar Slip Prevention)
**Vulnerability:** The build script used `zipfile.ZipFile.extractall` and `tarfile.extractall` to extract downloaded archives without verifying if the extraction paths stay within the intended destination directory.
**Learning:** `extractall` blindly trusts paths within archives. If an archive contains paths with `../` (e.g., `../../etc/passwd`), it can overwrite arbitrary files on the system, leading to code execution or data corruption.
**Prevention:** For zip files, iterate through `infolist()` and validate that `os.path.commonpath([dest.resolve(), target.resolve()]) == str(dest.resolve())` before extracting. For tar files, use `filter='data'` if available (Python 3.12+), otherwise fallback to the same `commonpath` manual validation loop via `getmembers()`.

## 2025-02-28 - Secure Path Resolution (Path Traversal Prevention)
**Vulnerability:** The `resolve_path` function manually constructed local paths for `smb://` URLs by directly splitting and appending the URI path to system mount locations (e.g. `/Volumes/` or GVFS paths), without sanitizing `../` segments.
**Learning:** Arbitrary user-provided URLs can contain `..` or empty segments, which when directly concatenated into file paths can escape intended base directories and cause Path Traversal (e.g. `smb://server/share/../../etc/passwd` becoming `/etc/passwd`).
**Prevention:** Always validate and sanitize user-provided file paths. When parsing URI strings, strip or reject traversal markers (`..`) and explicitly reconstruct the path using cleaned components, preventing any unescaped directory traversal attempt.
