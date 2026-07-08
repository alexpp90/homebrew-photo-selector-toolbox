## 2024-05-24 - Prevent SSRF Redirect Bypasses in urllib

**Vulnerability:** Application validated initial URL IPs but `urllib.request.urlopen` followed redirects by default, allowing attackers to bypass initial IP validation by supplying an external URL that redirects to a restricted internal metadata endpoint (e.g., 169.254.169.254).
**Learning:** Checking the initial hostname/IP is insufficient if the underlying HTTP client automatically follows 3xx redirects to unchecked destinations.
**Prevention:** When using `urllib.request` alongside IP-based SSRF protections, always explicitly block redirects by applying a custom `HTTPRedirectHandler` that raises an exception, and pass it to `urllib.request.build_opener()`.
