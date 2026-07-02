import pytest
from unittest.mock import patch, MagicMock
from pathlib import Path

from photo_selector_toolbox.image_panels import ImagePanelsMixin
from photo_selector_toolbox.models import ScanResult

@pytest.fixture(autouse=True)
def mock_gui_deps():
    with (
        patch("photo_selector_toolbox.image_panels.ttk.LabelFrame") as MockLabelFrame,
        patch("photo_selector_toolbox.image_panels.ttk.Frame") as MockFrame,
        patch("photo_selector_toolbox.image_panels.ttk.Label") as MockLabel,
        patch("photo_selector_toolbox.image_panels.ImageTk.PhotoImage") as MockPhotoImage,
        patch("photo_selector_toolbox.image_panels.create_placeholder_image") as MockCreatePlaceholder,
        patch("photo_selector_toolbox.image_panels.load_image_preview") as MockLoadImagePreview,
    ):
        yield {
            "LabelFrame": MockLabelFrame,
            "Frame": MockFrame,
            "Label": MockLabel,
            "PhotoImage": MockPhotoImage,
            "create_placeholder_image": MockCreatePlaceholder,
            "load_image_preview": MockLoadImagePreview,
        }

class DummyMixinHost(ImagePanelsMixin):
    def __init__(self):
        self.files_map = {}
        self.cache_manager = MagicMock()
        self.parent = MagicMock()
        self.focus_mode = False

        self.panel_prev = MagicMock()
        self.panel_curr = MagicMock()
        self.panel_next = MagicMock()
        self.focus_prev_lbl = MagicMock()
        self.focus_curr_lbl = MagicMock()
        self.focus_next_lbl = MagicMock()

        self.after_calls = []
        self.after_cancel_calls = []
        self.open_fullscreen_calls = []

    def after(self, delay, callback):
        self.after_calls.append((delay, callback))
        callback()
        return "timer_id_" + str(len(self.after_calls))

    def after_cancel(self, timer_id):
        self.after_cancel_calls.append(timer_id)

    def open_fullscreen(self, path, fit, coords=None):
        self.open_fullscreen_calls.append((path, fit, coords))


def test_create_image_panel():
    host = DummyMixinHost()
    parent_mock = MagicMock()

    panel = host.create_image_panel(parent_mock, "Test Panel")

    assert panel.img_container is not None
    assert panel.img_lbl is not None
    assert panel.details_lbl is not None
    assert panel.path is None
    assert panel.pil_image is None

    panel.img_container.bind.assert_called()
    panel.img_lbl.bind.assert_called()


def test_on_panel_resize():
    host = DummyMixinHost()
    panel_mock = MagicMock()
    event_mock = MagicMock()
    event_mock.width = 100
    event_mock.height = 100

    # We need to mock scale_image_to_panel so it doesn't try to access winfo_width of Mock
    host.scale_image_to_panel = MagicMock()

    # First resize
    host.on_panel_resize(event_mock, panel_mock)
    assert panel_mock._last_width == 100
    assert panel_mock._last_height == 100
    assert len(host.after_calls) == 1

    # Same resize, should return early
    host.on_panel_resize(event_mock, panel_mock)
    assert len(host.after_calls) == 1

    # Different resize, should cancel previous timer and set new one
    event_mock.width = 200
    host.on_panel_resize(event_mock, panel_mock)
    assert panel_mock._last_width == 200
    assert len(host.after_cancel_calls) == 1
    assert len(host.after_calls) == 2


def test_scale_image_to_panel():
    host = DummyMixinHost()
    panel_mock = MagicMock()

    # Case 1: no pil_image
    panel_mock.pil_image = None
    host.scale_image_to_panel(panel_mock)

    # Case 2: pil_image exists
    pil_image_mock = MagicMock()
    pil_image_mock.size = (100, 50)
    pil_image_mock.copy.return_value = pil_image_mock
    panel_mock.pil_image = pil_image_mock

    img_container_mock = MagicMock()
    img_container_mock.winfo_width.return_value = 200
    img_container_mock.winfo_height.return_value = 200
    panel_mock.img_container = img_container_mock

    lbl_mock = MagicMock()
    panel_mock.img_lbl = lbl_mock

    host.scale_image_to_panel(panel_mock)

    img_container_mock.update_idletasks.assert_called_once()
    pil_image_mock.copy.assert_called_once()
    pil_image_mock.thumbnail.assert_called_once()
    lbl_mock.config.assert_called_once()


def test_on_focus_label_resize():
    host = DummyMixinHost()
    lbl_mock = MagicMock()
    event_mock = MagicMock()
    event_mock.width = 100
    event_mock.height = 100

    # We need to mock scale_image_to_focus_label so it doesn't crash on winfo_width
    host.scale_image_to_focus_label = MagicMock()

    host.scale_image_to_focus_label = MagicMock()
    host.on_focus_label_resize(event_mock, lbl_mock)
    assert lbl_mock._last_width == 100
    assert len(host.after_calls) == 1


def test_scale_image_to_focus_label():
    host = DummyMixinHost()
    lbl_mock = MagicMock()
    lbl_mock.pil_image = None

    # Early return test
    host.scale_image_to_focus_label(lbl_mock)

    # Full execution test
    pil_image_mock = MagicMock()
    pil_image_mock.size = (100, 50)
    pil_image_mock.copy.return_value = pil_image_mock
    lbl_mock.pil_image = pil_image_mock

    container_mock = MagicMock()
    container_mock.winfo_width.return_value = 200
    container_mock.winfo_height.return_value = 200
    lbl_mock.container = container_mock

    host.scale_image_to_focus_label(lbl_mock)

    container_mock.update_idletasks.assert_called_once()
    pil_image_mock.copy.assert_called_once()
    pil_image_mock.thumbnail.assert_called_once()
    lbl_mock.config.assert_called_once()


def test_on_thumbnail_single_click():
    host = DummyMixinHost()
    frame_mock = MagicMock()
    frame_mock.path = Path("test.jpg")
    event_mock = MagicMock()

    host.on_thumbnail_single_click(event_mock, frame_mock)
    assert host._pending_click_path == frame_mock.path
    assert len(host.after_calls) == 1
    assert len(host.open_fullscreen_calls) == 1
    assert host.open_fullscreen_calls[0] == (frame_mock.path, "fit", None)


def test_on_thumbnail_double_click():
    host = DummyMixinHost()
    frame_mock = MagicMock()
    frame_mock.path = Path("test.jpg")
    event_mock = MagicMock()
    event_mock.widget = MagicMock()
    event_mock.x = 50
    event_mock.y = 50

    # Mock single click pending
    host._pending_click_id = "timer_1"

    # Mock image and widget dimensions
    img_mock = MagicMock()
    img_mock.width.return_value = 100
    img_mock.height.return_value = 100
    event_mock.widget.image = img_mock
    event_mock.widget.winfo_width.return_value = 100
    event_mock.widget.winfo_height.return_value = 100
    event_mock.widget.focus_set = MagicMock()

    host.on_thumbnail_double_click(event_mock, frame_mock)
    assert len(host.after_cancel_calls) == 1
    assert host.after_cancel_calls[0] == "timer_1"
    assert not hasattr(host, "_pending_click_id")
    assert len(host.open_fullscreen_calls) == 1

    # Verify open_fullscreen arguments (path, fit_mode, coords)
    assert host.open_fullscreen_calls[0][0] == frame_mock.path
    assert host.open_fullscreen_calls[0][1] == "100%"
    assert host.open_fullscreen_calls[0][2] == (0.5, 0.5)


def test_on_image_click():
    host = DummyMixinHost()
    path_mock = MagicMock()
    path_mock.exists.return_value = True

    host.on_image_click(path_mock)
    assert len(host.open_fullscreen_calls) == 1
    assert host.open_fullscreen_calls[0] == (path_mock, "fit", None)


def test_set_placeholder(mock_gui_deps):
    host = DummyMixinHost()
    panel_mock = MagicMock()
    path = Path("test.jpg")

    # Mock container dimensions
    container_mock = MagicMock()
    container_mock.winfo_width.return_value = 200
    container_mock.winfo_height.return_value = 200
    panel_mock.img_container = container_mock

    # No path
    host.set_placeholder(panel_mock, None)
    panel_mock.img_lbl.config.assert_called()
    assert panel_mock.path is None

    # With path, no result
    host.set_placeholder(panel_mock, path)
    panel_mock.details_lbl.config.assert_called_with(text=path.name)

    # With path and result
    res = ScanResult(path=path, score=0.85, noise_score=0.2)
    host.files_map[path] = res
    host.set_placeholder(panel_mock, path)

    details_call_args = panel_mock.details_lbl.config.call_args[1]
    assert path.name in details_call_args["text"]
    assert "Sharpness:" in details_call_args["text"]
    assert "Noise:" in details_call_args["text"]


def test_load_images_background(mock_gui_deps):
    host = DummyMixinHost()

    path1 = Path("1.jpg")
    path2 = Path("2.jpg")
    path3 = Path("3.jpg")

    # Mock cache hit for path1
    host.cache_manager.get_preview.side_effect = lambda p: "cached_img" if p == path1 else None

    # Mock load_image_preview for path2
    mock_gui_deps["load_image_preview"].side_effect = lambda p, max_size: "loaded_img" if p == path2 else None

    # Note: image loading code uses copy and thumbnail
    img_mock = MagicMock()
    img_mock.copy.return_value = img_mock
    host.cache_manager.get_preview.side_effect = lambda p: img_mock if p == path1 else None
    mock_gui_deps["load_image_preview"].side_effect = lambda p, max_size: img_mock if p == path2 else None

    host.panel_prev.path = path1
    host.panel_curr.path = path2
    host.panel_next.path = path3
    host.refresh_active_view = MagicMock()

    host.load_images_background(path1, path2, path3, (100, 100), (100, 100), (100, 100))

    # check that parent.after was called
    host.parent.after.assert_called_once()

    # Execute the callback
    callback = host.parent.after.call_args[0][1]
    callback()

    # Assertions
    assert hasattr(host, 'current_triplet_images')
    assert host.current_triplet_images == (img_mock, img_mock, None)
    host.refresh_active_view.assert_called_once()


def test_update_panels_final():
    host = DummyMixinHost()

    # Case 1: Stale load
    host.panel_prev.path = Path("old1.jpg")
    host.update_panels_final(None, None, None, Path("new1.jpg"), Path("new2.jpg"), Path("new3.jpg"))
    assert not hasattr(host, "current_triplet_images")

    # Case 2: Fresh load
    host.panel_prev.path = Path("1.jpg")
    host.panel_curr.path = Path("2.jpg")
    host.panel_next.path = Path("3.jpg")

    # Mock refresh_active_view
    host.refresh_active_view = MagicMock()

    host.update_panels_final("img1", "img2", "img3", Path("1.jpg"), Path("2.jpg"), Path("3.jpg"))
    assert host.current_triplet_images == ("img1", "img2", "img3")
    host.refresh_active_view.assert_called_once()


def test_refresh_active_view(mock_gui_deps):
    host = DummyMixinHost()
    host.current_triplet_images = ("img1", "img2", "img3")

    # Mock container dimensions
    for p in [host.panel_prev, host.panel_curr, host.panel_next]:
        p.img_container.winfo_width.return_value = 200
        p.img_container.winfo_height.return_value = 200

    for lbl in [host.focus_prev_lbl, host.focus_curr_lbl, host.focus_next_lbl]:
        lbl.container.winfo_width.return_value = 200
        lbl.container.winfo_height.return_value = 200

    # Test focus mode
    host.focus_mode = True
    host.scale_image_to_focus_label = MagicMock()
    host.refresh_active_view()

    assert host.focus_prev_lbl.pil_image == "img1"
    assert host.focus_curr_lbl.pil_image == "img2"
    assert host.focus_next_lbl.pil_image == "img3"
    assert host.scale_image_to_focus_label.call_count == 3

    # Test normal mode
    host.focus_mode = False
    host.scale_image_to_panel = MagicMock()
    host.refresh_active_view()

    assert host.panel_prev.pil_image == "img1"
    assert host.panel_curr.pil_image == "img2"
    assert host.panel_next.pil_image == "img3"
    assert host.scale_image_to_panel.call_count == 3


def test_scale_image_to_panel_edge_cases():
    host = DummyMixinHost()
    panel_mock = MagicMock()

    # Mock container
    img_container_mock = MagicMock()
    panel_mock.img_container = img_container_mock
    lbl_mock = MagicMock()
    panel_mock.img_lbl = lbl_mock

    # Case: container uninitialized (w < 10 or h < 10)
    img_container_mock.winfo_width.return_value = 5
    img_container_mock.winfo_height.return_value = 5

    pil_image_mock = MagicMock()
    pil_image_mock.size = (100, 100)
    panel_mock.pil_image = pil_image_mock

    host.scale_image_to_panel(panel_mock)
    pil_image_mock.copy.assert_called()

    # Case: avoid division by zero
    img_container_mock.winfo_width.return_value = 100
    img_container_mock.winfo_height.return_value = 100
    pil_image_mock.size = (0, 0)

    pil_image_mock.copy.reset_mock()
    host.scale_image_to_panel(panel_mock)
    pil_image_mock.copy.assert_not_called()

    # Case: Exception during scaling
    pil_image_mock.size = (100, 100)
    pil_image_mock.copy.side_effect = Exception("Test error")

    with patch("photo_selector_toolbox.image_panels.logger") as mock_logger:
        host.scale_image_to_panel(panel_mock)
        mock_logger.error.assert_called_with("Error scaling panel image: Test error")


def test_scale_image_to_focus_label_edge_cases():
    host = DummyMixinHost()
    lbl_mock = MagicMock()

    container_mock = MagicMock()
    lbl_mock.container = container_mock

    # Case: container uninitialized (w < 10 or h < 10)
    container_mock.winfo_width.return_value = 5
    container_mock.winfo_height.return_value = 5

    pil_image_mock = MagicMock()
    pil_image_mock.size = (100, 100)
    lbl_mock.pil_image = pil_image_mock

    host.scale_image_to_focus_label(lbl_mock)
    pil_image_mock.copy.assert_called()

    # Case: avoid division by zero
    container_mock.winfo_width.return_value = 100
    container_mock.winfo_height.return_value = 100
    pil_image_mock.size = (0, 0)

    pil_image_mock.copy.reset_mock()
    host.scale_image_to_focus_label(lbl_mock)
    pil_image_mock.copy.assert_not_called()

    # Case: Exception during scaling
    pil_image_mock.size = (100, 100)
    pil_image_mock.copy.side_effect = Exception("Test error")

    with patch("photo_selector_toolbox.image_panels.logger") as mock_logger:
        host.scale_image_to_focus_label(lbl_mock)
        mock_logger.error.assert_called_with("Error scaling focus label image: Test error")


def test_load_images_background_errors(mock_gui_deps):
    host = DummyMixinHost()

    path = Path("err.jpg")

    # Force get_preview to return None
    host.cache_manager.get_preview.return_value = None

    # Force load_image_preview to raise Exception
    mock_gui_deps["load_image_preview"].side_effect = Exception("Load error")

    with patch("photo_selector_toolbox.image_panels.logger") as mock_logger:
        host.load_images_background(path, None, None, (100, 100), (100, 100), (100, 100))
        mock_logger.error.assert_any_call("Error loading err.jpg: Load error")

    # Now make load_image_preview return a valid image, but copy() raise an exception
    img_mock = MagicMock()
    img_mock.copy.side_effect = Exception("Copy error")
    mock_gui_deps["load_image_preview"].side_effect = None
    mock_gui_deps["load_image_preview"].return_value = img_mock

    with patch("photo_selector_toolbox.image_panels.logger") as mock_logger:
        host.load_images_background(path, None, None, (100, 100), (100, 100), (100, 100))
        mock_logger.error.assert_any_call("Error preparing err.jpg: Copy error")



    # Now make copy() succeed, but thumbnail() raise an exception
    img_mock.copy.side_effect = None
    img_mock.copy.return_value.thumbnail.side_effect = Exception("Thumbnail error")
    mock_gui_deps["load_image_preview"].return_value = img_mock

    with patch("photo_selector_toolbox.image_panels.logger") as mock_logger:
        host.load_images_background(path, None, None, (100, 100), (100, 100), (100, 100))
        mock_logger.error.assert_any_call("Error preparing err.jpg: Thumbnail error")


def test_refresh_active_view_missing_images(mock_gui_deps):
    host = DummyMixinHost()
    host.current_triplet_images = (None, None, None)

    # Test focus mode
    host.focus_mode = True
    for lbl in [host.focus_prev_lbl, host.focus_curr_lbl, host.focus_next_lbl]:
        lbl.container.winfo_width.return_value = 5
        lbl.container.winfo_height.return_value = 5

    host.refresh_active_view()
    mock_gui_deps["create_placeholder_image"].assert_called()
    assert mock_gui_deps["create_placeholder_image"].call_args[0][0] == 400
    assert mock_gui_deps["create_placeholder_image"].call_args[0][1] == 300

    # Test normal mode
    host.focus_mode = False
    for p in [host.panel_prev, host.panel_curr, host.panel_next]:
        p.img_container.winfo_width.return_value = 5
        p.img_container.winfo_height.return_value = 5
        p.path = None

    mock_gui_deps["create_placeholder_image"].reset_mock()
    host.refresh_active_view()
    mock_gui_deps["create_placeholder_image"].assert_called()
    assert mock_gui_deps["create_placeholder_image"].call_args[0][0] == 400
    assert mock_gui_deps["create_placeholder_image"].call_args[0][1] == 300

def test_on_focus_label_resize_edge_cases():
    host = DummyMixinHost()
    lbl_mock = MagicMock()
    event_mock = MagicMock()
    event_mock.width = 100
    event_mock.height = 100

    # First resize
    host.scale_image_to_focus_label = MagicMock()
    host.on_focus_label_resize(event_mock, lbl_mock)
    assert lbl_mock._last_width == 100

    # Same resize (lines 115)
    host.scale_image_to_focus_label = MagicMock()
    host.on_focus_label_resize(event_mock, lbl_mock)

    # Different resize with existing timer (line 121)
    event_mock.width = 200
    setattr(host, "_resize_timer_f_" + str(id(lbl_mock)), "timer_existing")
    host.scale_image_to_focus_label = MagicMock()
    host.on_focus_label_resize(event_mock, lbl_mock)
    assert len(host.after_cancel_calls) > 0


def test_thumbnail_clicks_edge_cases():
    host = DummyMixinHost()
    frame_mock = MagicMock()
    event_mock = MagicMock()

    # No path (lines 163, 172)
    frame_mock.path = None
    host.on_thumbnail_single_click(event_mock, frame_mock)
    assert not hasattr(host, "_pending_click_path")

    host.on_thumbnail_double_click(event_mock, frame_mock)
    assert len(host.open_fullscreen_calls) == 0


def test_set_placeholder_edge_cases(mock_gui_deps):
    host = DummyMixinHost()
    panel_mock = MagicMock()

    container_mock = MagicMock()
    # Force w < 10 (line 216)
    container_mock.winfo_width.return_value = 5
    container_mock.winfo_height.return_value = 5
    panel_mock.img_container = container_mock

    host.set_placeholder(panel_mock, None)
    mock_gui_deps["create_placeholder_image"].assert_called()
    assert mock_gui_deps["create_placeholder_image"].call_args[0][0] == 400
    assert mock_gui_deps["create_placeholder_image"].call_args[0][1] == 300
