import pytest
from unittest.mock import patch, MagicMock
from pathlib import Path
import time
from photo_selector_toolbox.controllers import ImageCacheManager
import photo_selector_toolbox.controllers as ctrl


@pytest.fixture
def mock_load_image():
    with patch.object(ctrl, "load_image_preview") as mock:
        mock.return_value = MagicMock()
        yield mock


def test_image_cache_manager_queues(mock_load_image):
    manager = ImageCacheManager(preview_cache_limit=2, full_res_cache_limit=2)
    p1 = Path("/tmp/1.jpg")
    p2 = Path("/tmp/2.jpg")

    manager.queue_preview(p1)
    manager.queue_full_res(p2)

    # give threads a moment to process
    for _ in range(100):
        if p1 in manager.preview_cache and p2 in manager.full_res_cache:
            break
        time.sleep(0.05)

    assert p1 in manager.preview_cache
    assert p2 in manager.full_res_cache
    assert manager.get_preview(p1) is not None
    assert manager.get_full_res(p2) is not None


def test_image_cache_manager_limits(mock_load_image):
    manager = ImageCacheManager(preview_cache_limit=2)
    paths = [Path(f"/tmp/{i}.jpg") for i in range(5)]

    for p in paths:
        manager.queue_preview(p)

    for _ in range(100):
        if not manager.preview_queue.empty():
            time.sleep(0.05)
        else:
            time.sleep(0.05) # one more for processing
            break

    # the cache limit is 2
    assert len(manager.preview_cache) <= 2


def test_image_cache_manager_clear(mock_load_image):
    manager = ImageCacheManager()
    p1 = Path("/tmp/1.jpg")
    manager.queue_preview(p1)
    for _ in range(100):
        if p1 in manager.preview_cache:
            break
        time.sleep(0.05)

    assert p1 in manager.preview_cache
    manager.clear()
    assert len(manager.preview_cache) == 0


def test_scan_controller_run(tmp_path):
    from photo_selector_toolbox.controllers import ScanController, ScanRequest
    from PIL import Image
    import numpy as np

    img_path = tmp_path / "test_img.jpg"
    data = np.random.randint(0, 255, (100, 100, 3), dtype=np.uint8)
    img = Image.fromarray(data)
    img.save(img_path)

    controller = ScanController()
    tools = {
        "sharpness": True,
        "noise": True,
        "highlight_clipping": True,
        "shadow_clipping": True
    }

    progress_results = []
    def progress_cb(result, current, total):
        progress_results.append(result)

    finished_flag = False
    def finished_cb():
        nonlocal finished_flag
        finished_flag = True

    request = ScanRequest(
        files=[img_path],
        grid_size=8,
        tools=tools,
        progress_callback=progress_cb,
        finished_callback=finished_cb
    )
    controller.run_scan(request)

    for _ in range(100):
        if finished_flag:
            break
        time.sleep(0.1)

    assert finished_flag
    assert len(progress_results) == 1
    assert progress_results[0].path == img_path
    assert "sharpness" in progress_results[0].scores
    assert "noise" in progress_results[0].scores

