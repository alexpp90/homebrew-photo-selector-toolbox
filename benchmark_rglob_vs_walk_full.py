import os
import time
from pathlib import Path
from src.photo_selector_toolbox.utils import is_excluded_subfolder

# Create a larger fake directory structure
import shutil
if os.path.exists("test_dir_large"):
    shutil.rmtree("test_dir_large")

# 100 directories, 100 files each = 10000 files
for i in range(100):
    dir_name = f"test_dir_large/normal/sub{i}"
    os.makedirs(dir_name, exist_ok=True)
    for j in range(10):
        with open(f"{dir_name}/file_{j}.jpg", "w") as f: f.write("test")
        with open(f"{dir_name}/file_{j}.txt", "w") as f: f.write("test")
        with open(f"{dir_name}/file_{j}.arw", "w") as f: f.write("test")

for i in range(10):
    dir_name = f"test_dir_large/Selection/sub{i}"
    os.makedirs(dir_name, exist_ok=True)
    for j in range(50):
        with open(f"{dir_name}/file_{j}.jpg", "w") as f: f.write("test")

def discover_rglob(p, extensions):
    return [
        f for f in p.rglob("*")
        if f.suffix.lower() in extensions and not is_excluded_subfolder(f, p) and not f.name.startswith("._")
    ]

def discover_walk(p, extensions):
    files = []
    p_str = str(p)
    from src.photo_selector_toolbox.utils import get_excluded_folder_names
    excluded_names = get_excluded_folder_names()
    for root, dirs, filenames in os.walk(p_str):
        # Modify dirs in-place to skip excluded directories using set lookup
        dirs[:] = [d for d in dirs if d.lower() not in excluded_names]

        for name in filenames:
            if not name.startswith("._"):
                suffix = os.path.splitext(name)[1].lower()
                if suffix in extensions:
                    files.append(Path(root) / name)
    return files

p = Path("test_dir_large")
exts = {".jpg", ".jpeg", ".arw"}

t0 = time.time()
for _ in range(10): discover_rglob(p, exts)
print("rglob 10 runs:", time.time() - t0)

t0 = time.time()
for _ in range(10): discover_walk(p, exts)
print("walk 10 runs:", time.time() - t0)

r1 = discover_rglob(p, exts)
r2 = discover_walk(p, exts)
print("results equal:", set(r1) == set(r2))
print(f"found {len(r1)} files")
