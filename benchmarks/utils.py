from pathlib import Path

def setup_test_images(num_images=20, test_dir_path="benchmarks/test_images"):
    import cv2
    import numpy as np

    test_dir = Path(test_dir_path)
    test_dir.mkdir(parents=True, exist_ok=True)

    files = []
    for i in range(num_images):
        filepath = test_dir / f"test_{i}.jpg"
        if not filepath.exists():
            img = np.random.randint(0, 256, (1000, 1000, 3), dtype=np.uint8)
            cv2.imwrite(str(filepath), img)
        files.append(filepath)
    return files
