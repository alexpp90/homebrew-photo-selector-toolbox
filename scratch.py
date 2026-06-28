import socket

# Look at windows behavior for socket.gethostbyname
# Oh, on Windows `socket.gethostbyname("0xa9fea9fe")` raises socket.gaierror!
