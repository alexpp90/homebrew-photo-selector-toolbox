## 2025-02-28 - Secure Archive Extraction (Zip/Tar Slip Prevention)
**Vulnerability:** The build script used `zipfile.ZipFile.extractall` and `tarfile.extractall` to extract downloaded archives without verifying if the extraction paths stay within the intended destination directory.
**Learning:** `extractall` blindly trusts paths within archives. If an archive contains paths with `../` (e.g., `../../etc/passwd`), it can overwrite arbitrary files on the system, leading to code execution or data corruption.
**Prevention:** For zip files, iterate through `infolist()` and validate that `os.path.commonpath([dest.resolve(), target.resolve()]) == str(dest.resolve())` before extracting. For tar files, use `filter='data'` if available (Python 3.12+), otherwise fallback to the same `commonpath` manual validation loop via `getmembers()`.
## 2025-02-27 - Prevent Path Traversal in SMB URL resolution
**Vulnerability:** The `resolve_path` function was susceptible to Path Traversal when parsing and resolving `smb://` URLs. An attacker could embed `../` commands within the path component, allowing them to traverse outside of the intended directory context when the split path segments were concatenated with the local mount point.
**Learning:** Resolving and splitting path components before normalizing them preserves traversal commands (like `..`) which can be exploited during later concatenation. Simply filtering out `..` strings is often incomplete or incorrect, as it breaks the semantic meaning of paths.
**Prevention:** Use `os.path.normpath()` (e.g., `os.path.normpath(unquoted_path)`) to logically resolve and collapse path components *before* performing splits or concatenation, ensuring traversal commands are resolved properly before being used as local filesystem paths.
## 2025-02-27 - Cross-Platform Path Traversal Resolution
**Vulnerability:** Fixing Path Traversal by resolving path segments must account for cross-platform differences. Using `os.path.normpath` on URLs (which are always `/` delimited) while running on Windows translates forward slashes into backslashes (`\`). This can break subsequent path parsing logic that splits by `/`.
**Learning:** For resolving parsed network URLs or URIs specifically, standard `os.path.normpath` should be avoided if platform-agnostic output is needed.
**Prevention:** Use `posixpath.normpath()` directly when working with URLs or paths that must explicitly preserve the POSIX standard forward slash representation across all operating systems.

## 2025-02-28 - SSRF Prevention with Cloud Metadata API Protection
**Vulnerability:** User-configurable API URLs can be exploited to request internal or cloud metadata URLs, potentially exposing sensitive IAM roles or tokens via Server-Side Request Forgery (SSRF).
**Learning:** Simple URL scheme checks (e.g., enforcing HTTP/HTTPS) are not enough to prevent SSRF against cloud provider metadata APIs located at link-local IP addresses (e.g., `169.254.169.254`).
**Prevention:** Always validate that the parsed hostname does not resolve to or match restricted subnets (`169.254.*`) or sensitive internal IPs, unless explicitly intended for local services.
