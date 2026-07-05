import re

file_path = "tests/test_ssrf_protection.py"
with open(file_path, "r") as f:
    content = f.read()

# Replace urlopen mock with OpenerDirector.open mock
content = content.replace('@patch("urllib.request.urlopen")', '@patch("urllib.request.OpenerDirector.open")')
content = content.replace('mock_urlopen', 'mock_open')

with open(file_path, "w") as f:
    f.write(content)
