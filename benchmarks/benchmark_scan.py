import time
from photo_selector_toolbox.controllers import ScanController
from benchmarks.utils import setup_test_images

def dummy_progress(res, i, total):
    pass

def dummy_finished():
    pass

def log(msg):
    pass


def main():
    files = setup_test_images()

    controller = ScanController()
    tools = {"sharpness": True, "noise": True}

    start = time.time()
    controller._scan_worker(
        files=files,
        grid_size=1,
        tools=tools,
        progress_callback=dummy_progress,
        finished_callback=dummy_finished,
        log_callback=log
    )
    end = time.time()
    print(f"Sequential Scan Time: {end - start:.2f} seconds")

if __name__ == "__main__":
    main()
