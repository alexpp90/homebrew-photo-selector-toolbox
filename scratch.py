from pathlib import Path
expected_linux = Path("/run/user/1000/gvfs/smb-share:server=myserver,share=myshare/path/to/image.jpg")
expected_str = str(expected_linux)
print(expected_str)
