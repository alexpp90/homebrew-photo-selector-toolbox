import ipaddress
import socket

try:
    ip = ipaddress.ip_address("169.254.169.254")
    print(ip, type(ip))
except Exception as e:
    print(f"Exception: {e}")

def is_forbidden_ip(ip_str):
    try:
        ip_obj = ipaddress.ip_address(ip_str)
        if ip_obj.is_link_local:
            return True
        if getattr(ip_obj, "ipv4_mapped", None) and ip_obj.ipv4_mapped.is_link_local:
            return True
        return False
    except ValueError:
        return False

print("127.0.0.1 is_forbidden_ip:", is_forbidden_ip("127.0.0.1"))
print("::1 is_forbidden_ip:", is_forbidden_ip("::1"))
print("0.0.0.0 is_forbidden_ip:", is_forbidden_ip("0.0.0.0"))
