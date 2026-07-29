import urllib.request
import urllib.parse
from photo_selector_toolbox.utils import NoRedirectHandler
import ipaddress
import socket

def test_request():
    ollama_url = "http://[::1]:11434"
    hostname = urllib.parse.urlparse(ollama_url).hostname
    clean_hostname = hostname.strip("[]")

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

    if is_forbidden_ip(clean_hostname):
        print("Forbidden host string")
        return

    try:
        addr_info = socket.getaddrinfo(clean_hostname, None)
        for res in addr_info:
            ip_str = res[4][0]
            if is_forbidden_ip(ip_str):
                print("Forbidden resolved ip:", ip_str)
                return
    except socket.gaierror as e:
        print("gaierror", e)
        # BUG: if gaierror is thrown (e.g. invalid hostname or resolution failure),
        # the code just passes, allowing urllib to make the request later.
        # But wait, what if an attacker sets the url to "http://0xa9fea9fe/latest/meta-data/"
        # `getaddrinfo` might fail on some platforms if it can't resolve hex IP,
        # but urllib.request might be able to process it or pass it to lower level socket that handles hex IP?
        # Actually in python, socket.getaddrinfo handles 0xa9fea9fe on Linux but maybe not on all platforms?
        pass

    print("Allowed")
test_request()
