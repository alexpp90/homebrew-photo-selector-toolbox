import socket

for h in ["localhost", "127.0.0.1", "::1", "google.com", "not-exist-domain-12345.com", "http://google.com"]:
    try:
        res = socket.getaddrinfo(h, None)
        print(f"{h}: {res[0][4][0]}")
    except socket.gaierror as e:
        print(f"{h}: gaierror {e}")
