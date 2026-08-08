## 2025-02-12 - [Proper SSRF Protection for IPv4-mapped IPv6 literals]
**Vulnerability:** The application was vulnerable to SSRF bypass using IPv4-mapped IPv6 literals (e.g., `::ffff:169.254.169.254`). While it checked `is_link_local` on the mapped IP, it failed to verify `is_unspecified` on it.
**Learning:** Python's `ipaddress` module requires explicitly running all the same checks on the `ipv4_mapped` property as on the primary IP object, because `ipaddress` doesn't automatically proxy all properties.
**Prevention:** Whenever checking an IP object for properties like `is_unspecified`, `is_link_local`, or `is_loopback` (if blocked), ensure the identical checks are run on the `ipv4_mapped` object if it exists.
