import time
from photo_selector_toolbox.models import ScanResult
from concurrent.futures import ProcessPoolExecutor
from benchmarks.utils import setup_test_images

def dummy_progress(res, i, total):
    pass

def dummy_finished():
    pass

def log(msg):
    pass


def process_file(f, grid_size, tools):
    from photo_selector_toolbox.sharpness import calculate_sharpness, calculate_noise
    from photo_selector_toolbox.reader import get_exif_data

    score = "N/A"
    if tools.get("sharpness", False):
        score = calculate_sharpness(f, grid_size=grid_size)
    noise_score = "N/A"
    if tools.get("noise", False):
        noise_score = calculate_noise(f)
    exif = get_exif_data(f) or {}
    return ScanResult(path=f, score=score, noise_score=noise_score, exif=exif)


def main():
    files = setup_test_images()

    tools = {"sharpness": True, "noise": True}

    start = time.time()

    with ProcessPoolExecutor() as executor:
        futures = []
        for f in files:
            futures.append(executor.submit(process_file, f, 1, tools))

        for i, future in enumerate(futures):
            res = future.result()
            dummy_progress(res, i+1, len(files))

    end = time.time()
    print(f"Process Scan Time: {end - start:.2f} seconds")

if __name__ == "__main__":
    main()
