## 2024-05-25 - Fix SSRF bypass for 0.0.0.0
**Vulnerability:** The application was vulnerable to an SSRF bypass when a user inputs `::ffff:0.0.0.0` (IPv4-mapped IPv6 for the unspecified address). The previous validation logic correctly blocked `0.0.0.0` natively, but failed to block it when represented in IPv6 mapped format.
**Learning:** `ipaddress.ip_address('::ffff:0.0.0.0').is_unspecified` evaluates to `False`. The mapped address must be extracted (`ipv4_mapped`) and its `is_unspecified` property checked separately to prevent bypasses.
**Prevention:** Always check both the primary IP object and its `ipv4_mapped` equivalent for restricted conditions (like `is_link_local` and `is_unspecified`).
