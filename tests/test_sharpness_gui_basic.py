import pytest
from unittest.mock import patch, MagicMock
import sys

# Save original modules to prevent test pollution
original_modules = {}

@pytest.fixture(scope="module", autouse=True)
def mock_sys_modules():
    existing_modules = set(sys.modules.keys())
    import importlib
    modules_to_mock = [
        "rawpy",
        "photo_selector_toolbox.controllers",
        "photo_selector_toolbox.models",
        "photo_selector_toolbox.sharpness",
        "photo_selector_toolbox.reader",
        "photo_selector_toolbox.utils",
        "photo_selector_toolbox.formatting",
        "send2trash",
    ]
    for name in modules_to_mock:
        original_modules[name] = sys.modules.get(name)
        mock_mod = MagicMock()
        if name == "photo_selector_toolbox.models":
            orig_mod = importlib.import_module("photo_selector_toolbox.models")
            mock_mod.ScanResult.side_effect = orig_mod.ScanResult
            mock_mod.ExifData.side_effect = orig_mod.ExifData
        elif name == "photo_selector_toolbox.utils":
            orig_mod = importlib.import_module("photo_selector_toolbox.utils")
            mock_mod.select_representative.side_effect = orig_mod.select_representative
            mock_mod.is_excluded_subfolder.return_value = False
        sys.modules[name] = mock_mod

    yield

    for name in modules_to_mock:
        orig = original_modules[name]
        if orig is None:
            sys.modules.pop(name, None)
        else:
            sys.modules[name] = orig

    # Pop any newly imported photo_selector_toolbox modules imported during mock session to prevent pollution
    to_pop = [
        m for m in list(sys.modules.keys())
        if m.startswith("photo_selector_toolbox") and m not in existing_modules
    ]
    for m in to_pop:
        sys.modules.pop(m, None)




@pytest.fixture(autouse=True)
def mock_sharpness_gui_deps():
    class DummyGet:
        def __init__(self, var):
            self.var = var
            self.return_value = None
        def __call__(self):
            if self.return_value is not None:
                return self.return_value
            return self.var._val

    class DummyVar:
        def __init__(self, value=None):
            self._val = value
            self.get = DummyGet(self)
        def set(self, val):
            self._val = val

    with (
        patch("photo_selector_toolbox.sharpness_gui.tk.Tk"),
        patch(
            "photo_selector_toolbox.sharpness_gui.tk.StringVar",
            side_effect=lambda value=None: DummyVar(value),
        ),
        patch(
            "photo_selector_toolbox.sharpness_gui.tk.IntVar",
            side_effect=lambda value=None: DummyVar(value),
        ),
        patch(
            "photo_selector_toolbox.sharpness_gui.tk.DoubleVar",
            side_effect=lambda value=None: DummyVar(value),
        ),
        patch(
            "photo_selector_toolbox.sharpness_gui.tk.BooleanVar",
            side_effect=lambda value=None: DummyVar(value),
        ),
        patch("photo_selector_toolbox.sharpness_gui.ttk.Frame"),
        patch("photo_selector_toolbox.sharpness_gui.ttk.LabelFrame"),
        patch("photo_selector_toolbox.sharpness_gui.ttk.Label"),
        patch("photo_selector_toolbox.sharpness_gui.ttk.Button"),
        patch("photo_selector_toolbox.sharpness_gui.ttk.Notebook"),
        patch("photo_selector_toolbox.sharpness_gui.ttk.Treeview"),
        patch("photo_selector_toolbox.sharpness_gui.ttk.Scrollbar"),
        patch("photo_selector_toolbox.sharpness_gui.ImageTk.PhotoImage"),
        patch("photo_selector_toolbox.controllers.ImageCacheManager"),
    ):
        yield


def test_sharpness_tool_init():
    from photo_selector_toolbox.sharpness_gui import SharpnessTool

    parent = MagicMock()

    # Needs to mock out parent methods used in __init__
    parent.register = MagicMock()

    with (
        patch("photo_selector_toolbox.sharpness_gui.tk.Toplevel"),
        patch("photo_selector_toolbox.sharpness_gui.SharpnessTool.setup_ui"),
        patch("photo_selector_toolbox.sharpness_gui.SharpnessTool.setup_focus_ui"),
        patch("photo_selector_toolbox.sharpness_gui.SharpnessTool.bind_all"),
    ):
        tool = SharpnessTool(parent)
        assert not tool.is_scanning
        assert tool.candidates == []
        assert tool.sorted_files == []


def test_sharpness_tool_filtering():
    from photo_selector_toolbox.sharpness_gui import SharpnessTool
    from pathlib import Path

    parent = MagicMock()
    parent.register = MagicMock()

    with (
        patch("photo_selector_toolbox.sharpness_gui.tk.Toplevel"),
        patch("photo_selector_toolbox.sharpness_gui.SharpnessTool.bind_all"),
    ):
        tool = SharpnessTool(parent)
        tool.config = MagicMock()
        tool.update = MagicMock()
        tool.folder_var.get.return_value = "/mock/folder"

        test_files = [
            Path("/mock/folder/img1.jpg"),
            Path("/mock/folder/img2.arw"),
            Path("/mock/folder/img3.jpg"),
            Path("/mock/folder/img4.png"),
        ]
        walk_return = [("/mock/folder", [], ["img1.jpg", "img2.arw", "img3.jpg", "img4.png"])]
        with patch("os.walk", return_value=walk_return):
            with patch("photo_selector_toolbox.reader.SUPPORTED_EXTENSIONS", {".jpg", ".arw", ".png"}):
                tool._load_folder_contents("/mock/folder")

                # Check sorted_files and candidates initially contain everything
                assert len(tool.sorted_files) == 4
                assert len(tool.candidates) == 4

                # Mock file_type_var.get to return ".JPG"
                tool.file_type_var.get.return_value = ".JPG"
                tool.on_file_type_change()

                assert len(tool.candidates) == 2
                assert all(f.suffix.upper() == ".JPG" for f in tool.candidates)

                # Test resetting to All
                tool.file_type_var.get.return_value = "All Supported"
                tool.on_file_type_change()

                assert len(tool.candidates) == 4


def test_update_scan_button_state_no_recreation():
    from photo_selector_toolbox.sharpness_gui import SharpnessTool

    parent = MagicMock()
    parent.register = MagicMock()

    with (
        patch("photo_selector_toolbox.sharpness_gui.tk.Toplevel"),
        patch("photo_selector_toolbox.sharpness_gui.SharpnessTool.bind_all"),
    ):
        tool = SharpnessTool(parent)
        # Verify that setup_review_ui created preview_area
        assert hasattr(tool, "preview_area")

        # Save reference of preview_area
        initial_preview_area = tool.preview_area

        # Call update_scan_button_state multiple times
        tool.update_scan_button_state()
        tool.is_scanning = True
        tool.update_scan_button_state()
        tool.is_scanning = False
        tool.update_scan_button_state()

        # Verify preview_area is still the same object and not recreated
        assert tool.preview_area is initial_preview_area


def test_get_candidate_listbox_text():
    import sys
    from unittest.mock import patch, MagicMock
    from pathlib import Path

    # Temporarily restore real models module to construct ScanResult properly
    orig_models = original_modules.get("photo_selector_toolbox.models")
    mock_models = sys.modules.get("photo_selector_toolbox.models")

    if orig_models:
        sys.modules["photo_selector_toolbox.models"] = orig_models
    else:
        sys.modules.pop("photo_selector_toolbox.models", None)

    try:
        import photo_selector_toolbox.sharpness_gui as sg
        from photo_selector_toolbox.sharpness_gui import SharpnessTool
        from photo_selector_toolbox.models import ScanResult

        # Override format_score in the sharpness_gui module namespace
        original_format_score = sg.format_score
        sg.format_score = lambda val, **kwargs: (
            "N/A" if val is None or val == "N/A" else (f"{val:.1f}" if isinstance(val, float) else str(val))
        )

        parent = MagicMock()
        parent.register = MagicMock()

        try:
            with (
                patch("photo_selector_toolbox.sharpness_gui.tk.Toplevel"),
                patch("photo_selector_toolbox.sharpness_gui.SharpnessTool.bind_all"),
            ):
                tool = SharpnessTool(parent)
                path = Path("/mock/folder/img1.jpg")

                # Test case 1: No scan result (or all scores are N/A)
                tool.files_map[path] = ScanResult(path=path)
                assert tool._get_candidate_listbox_text(path) == "img1.jpg"

                # Test case 2: Sharpness calculated, others not
                tool.files_map[path] = ScanResult(path=path, score=42.5)
                assert tool._get_candidate_listbox_text(path) == "img1.jpg (42.5)"

                # Test case 3: Sharpness and Noise calculated
                tool.files_map[path] = ScanResult(path=path, score=42.5, noise_score=1.2)
                assert tool._get_candidate_listbox_text(path) == "img1.jpg (42.5, 1.2)"

                # Test case 4: Sharpness, Noise, and Highlight/Shadow clipping calculated
                res = ScanResult(path=path, score=42.5, noise_score=1.2)
                res.scores["highlight_clipping"] = 0.5
                res.scores["shadow_clipping"] = 1.0
                tool.files_map[path] = res
                assert tool._get_candidate_listbox_text(path) == "img1.jpg (42.5, 1.2, 0.5%, 1.0%)"
        finally:
            sg.format_score = original_format_score
    finally:
        # Re-mock models
        if mock_models:
            sys.modules["photo_selector_toolbox.models"] = mock_models
        else:
            sys.modules.pop("photo_selector_toolbox.models", None)


def test_update_metadata_label_loads_exif():
    import sys
    from unittest.mock import patch, MagicMock
    from pathlib import Path

    # Temporarily restore real models, reader, and formatting modules to test properly
    orig_models = original_modules.get("photo_selector_toolbox.models")
    mock_models = sys.modules.get("photo_selector_toolbox.models")
    orig_reader = original_modules.get("photo_selector_toolbox.reader")
    mock_reader = sys.modules.get("photo_selector_toolbox.reader")
    orig_formatting = original_modules.get("photo_selector_toolbox.formatting")
    mock_formatting = sys.modules.get("photo_selector_toolbox.formatting")

    if orig_models:
        sys.modules["photo_selector_toolbox.models"] = orig_models
    else:
        sys.modules.pop("photo_selector_toolbox.models", None)

    if orig_reader:
        sys.modules["photo_selector_toolbox.reader"] = orig_reader
    else:
        sys.modules.pop("photo_selector_toolbox.reader", None)

    if orig_formatting:
        sys.modules["photo_selector_toolbox.formatting"] = orig_formatting
    else:
        sys.modules.pop("photo_selector_toolbox.formatting", None)

    try:
        from photo_selector_toolbox.formatting import format_score, format_meta
        import photo_selector_toolbox.sharpness_gui as sg
        from photo_selector_toolbox.sharpness_gui import SharpnessTool
        from photo_selector_toolbox.models import ScanResult, ExifData

        # Save original formatters and inject real ones
        orig_sg_format_score = sg.format_score
        orig_sg_format_meta = sg.format_meta
        sg.format_score = format_score
        sg.format_meta = format_meta

        parent = MagicMock()
        parent.register = MagicMock()

        try:
            with (
                patch("photo_selector_toolbox.sharpness_gui.tk.Toplevel"),
                patch("photo_selector_toolbox.sharpness_gui.SharpnessTool.bind_all"),
                patch("photo_selector_toolbox.sharpness_gui.get_exif_data") as mock_get_exif,
            ):
                tool = SharpnessTool(parent)
                tool.meta_lbl = MagicMock()

                path = Path("/mock/folder/img1.jpg")
                res = ScanResult(path=path)
                tool.files_map[path] = res

                # Mock get_exif_data to return a valid ExifData
                mock_exif = ExifData(iso=100.0, shutter_speed=0.005, aperture=2.8, focal_length=50.0)
                mock_get_exif.return_value = mock_exif

                tool.update_metadata_label(path)

                # Verify get_exif_data was called and res.exif was populated
                mock_get_exif.assert_called_once_with(path)
                assert res.exif == mock_exif

                # Verify meta_lbl.config was called with formatted string
                tool.meta_lbl.config.assert_called_once()
                call_args = tool.meta_lbl.config.call_args[1]
                assert "ISO: 100" in call_args["text"]
                assert "1/200s" in call_args["text"]
                assert "f/2.8" in call_args["text"]
                assert "50mm" in call_args["text"]
        finally:
            sg.format_score = orig_sg_format_score
            sg.format_meta = orig_sg_format_meta
    finally:
        if mock_models:
            sys.modules["photo_selector_toolbox.models"] = mock_models
        else:
            sys.modules.pop("photo_selector_toolbox.models", None)

        if mock_reader:
            sys.modules["photo_selector_toolbox.reader"] = mock_reader
        else:
            sys.modules.pop("photo_selector_toolbox.reader", None)

        if mock_formatting:
            sys.modules["photo_selector_toolbox.formatting"] = mock_formatting
        else:
            sys.modules.pop("photo_selector_toolbox.formatting", None)

def test_mac_metadata_ignored():
    from pathlib import Path
    from photo_selector_toolbox.sharpness_gui import SharpnessTool
    parent = MagicMock()
    parent.register = MagicMock()

    with (
        patch("photo_selector_toolbox.sharpness_gui.tk.Toplevel"),
        patch("photo_selector_toolbox.sharpness_gui.SharpnessTool.bind_all"),
    ):
        tool = SharpnessTool(parent)
        tool.config = MagicMock()
        tool.update = MagicMock()
        tool.folder_var.get.return_value = "/mock/folder"

        test_files = [
            Path("/mock/folder/img1.jpg"),
            Path("/mock/folder/._img1.jpg"),
            Path("/mock/folder/img2.arw"),
            Path("/mock/folder/._img2.arw"),
        ]
        walk_return = [("/mock/folder", [], ["img1.jpg", "._img1.jpg", "img2.arw", "._img2.arw"])]
        with patch("os.walk", return_value=walk_return):
            with patch("photo_selector_toolbox.reader.SUPPORTED_EXTENSIONS", {".jpg", ".arw"}):
                tool._load_folder_contents("/mock/folder")

                # Check sorted_files and candidates ignore dot_underscore
                assert len(tool.sorted_files) == 2
                assert len(tool.candidates) == 2
                filenames = {f.name for f in tool.candidates}
                assert filenames == {"img1.jpg", "img2.arw"}


def test_throttled_listbox_updates():
    from photo_selector_toolbox.sharpness_gui import SharpnessTool
    from pathlib import Path

    parent = MagicMock()
    parent.register = MagicMock()

    with (
        patch("photo_selector_toolbox.sharpness_gui.tk.Toplevel"),
        patch("photo_selector_toolbox.sharpness_gui.SharpnessTool.bind_all"),
    ):
        tool = SharpnessTool(parent)
        tool.parent = MagicMock()
        tool.candidate_listbox = MagicMock()
        tool.candidates = [Path("img1.jpg"), Path("img2.jpg")]
        tool._get_candidate_listbox_text = MagicMock(return_value="mock_text")

        # Test that calling update buffers the path and schedules flush only once
        path1 = Path("img1.jpg")
        path2 = Path("img2.jpg")

        res1 = MagicMock()
        res1.path = path1
        res2 = MagicMock()
        res2.path = path2

        tool._update_candidate_listbox_ui(res1)
        tool._update_candidate_listbox_ui(res2)

        assert path1 in tool.pending_listbox_updates
        assert path2 in tool.pending_listbox_updates
        assert len(tool.pending_listbox_updates) == 2
        assert tool.listbox_update_loop_active is True

        # parent.after should have been called exactly once to schedule the loop
        tool.parent.after.assert_called_once_with(250, tool._flush_listbox_updates)

        # Now mock the listbox calls
        tool.candidate_listbox.curselection.return_value = ()

        # Call flush
        tool._flush_listbox_updates()

        # The pending updates should be processed and cleared
        assert len(tool.pending_listbox_updates) == 0
        assert tool.candidate_listbox.delete.call_count == 2
        assert tool.candidate_listbox.insert.call_count == 2


def test_sorting_flat_list():
    from photo_selector_toolbox.sharpness_gui import SharpnessTool
    from photo_selector_toolbox.models import ScanResult
    from pathlib import Path

    parent = MagicMock()
    parent.register = MagicMock()

    with (
        patch("photo_selector_toolbox.sharpness_gui.tk.Toplevel"),
        patch("photo_selector_toolbox.sharpness_gui.SharpnessTool.bind_all"),
    ):
        tool = SharpnessTool(parent)
        tool.candidate_listbox = MagicMock()
        tool.folder_var = MagicMock()
        tool.folder_var.get.return_value = "/mock"

        path1 = Path("img1.jpg")
        path2 = Path("img2.jpg")
        path3 = Path("img3.jpg")

        tool.sorted_files = [path1, path2, path3]
        tool.files_map = {
            path1: ScanResult(path=path1, score=150.0),
            path2: ScanResult(path=path2, score=500.0),
            path3: ScanResult(path=path3, score=300.0),
        }

        # Test Descending order (default for Sharpness Score)
        tool.sort_by_var.set("Sharpness Score")
        tool.sort_order_var.set("Descending")
        tool.apply_grouping_and_refresh()

        # Should be sorted: path2 (500.0), path3 (300.0), path1 (150.0)
        assert tool.candidates == [path2, path3, path1]

        # Test Ascending order
        tool.sort_order_var.set("Ascending")
        tool.apply_grouping_and_refresh()

        # Should be sorted: path1 (150.0), path3 (300.0), path2 (500.0)
        assert tool.candidates == [path1, path3, path2]


def test_sorting_na_scores():
    from photo_selector_toolbox.sharpness_gui import SharpnessTool
    from photo_selector_toolbox.models import ScanResult
    from pathlib import Path

    parent = MagicMock()
    parent.register = MagicMock()

    with (
        patch("photo_selector_toolbox.sharpness_gui.tk.Toplevel"),
        patch("photo_selector_toolbox.sharpness_gui.SharpnessTool.bind_all"),
    ):
        tool = SharpnessTool(parent)
        tool.candidate_listbox = MagicMock()
        tool.folder_var = MagicMock()
        tool.folder_var.get.return_value = "/mock"

        path1 = Path("img1.jpg")
        path2 = Path("img2.jpg")
        path3 = Path("img3.jpg")

        tool.sorted_files = [path1, path2, path3]
        tool.files_map = {
            path1: ScanResult(path=path1, score="N/A"),
            path2: ScanResult(path=path2, score=500.0),
            path3: ScanResult(path=path3, score=300.0),
        }

        # Test Descending order - N/A should go to the end
        tool.sort_by_var.set("Sharpness Score")
        tool.sort_order_var.set("Descending")
        tool.apply_grouping_and_refresh()

        # Should be: path2 (500.0), path3 (300.0), path1 (N/A)
        assert tool.candidates == [path2, path3, path1]

        # Test Ascending order - N/A should still go to the end
        tool.sort_order_var.set("Ascending")
        tool.apply_grouping_and_refresh()

        # Should be: path3 (300.0), path2 (500.0), path1 (N/A)
        assert tool.candidates == [path3, path2, path1]


def test_sorting_grouped_list():
    from photo_selector_toolbox.sharpness_gui import SharpnessTool
    from photo_selector_toolbox.models import ScanResult
    from pathlib import Path

    parent = MagicMock()
    parent.register = MagicMock()

    with (
        patch("photo_selector_toolbox.sharpness_gui.tk.Toplevel"),
        patch("photo_selector_toolbox.sharpness_gui.SharpnessTool.bind_all"),
        patch("photo_selector_toolbox.utils.group_files_by_similarity") as mock_group,
    ):
        tool = SharpnessTool(parent)
        tool.candidate_listbox = MagicMock()
        tool.group_similar_var.set(True)

        path1 = Path("img1.jpg")
        path2 = Path("img2.jpg")
        path3 = Path("img3.jpg")
        path4 = Path("img4.jpg")

        tool.sorted_files = [path1, path2, path3, path4]
        tool.files_map = {
            path1: ScanResult(path=path1, score=100.0), # Group A
            path2: ScanResult(path=path2, score=400.0), # Group A
            path3: ScanResult(path=path3, score=500.0), # Group B
            path4: ScanResult(path=path4, score=200.0), # Group B
        }

        # Group A has path1 & path2
        # Group B has path3 & path4
        mock_group.return_value = [[path1, path2], [path3, path4]]

        # Sort by Sharpness Score, Descending
        tool.sort_by_var.set("Sharpness Score")
        tool.sort_order_var.set("Descending")

        tool.apply_grouping_and_refresh()

        # 1. Group A internally: path2 (400.0) first, path1 (100.0) second. Rep is path2.
        # 2. Group B internally: path3 (500.0) first, path4 (200.0) second. Rep is path3.
        # 3. Sort groups themselves: Group B (rep 500.0) before Group A (rep 400.0).
        # Since not expanded, candidates should just be the representatives of the sorted groups: path3, path2.
        assert tool.candidates == [path3, path2]

        # Let's expand both groups to check internal sorting
        tool.image_groups[0].expanded = True # Group B
        tool.image_groups[1].expanded = True # Group A
        tool.apply_grouping_and_refresh()

        # Candidates should be:
        # Group B: rep (path3), followed by other member (path4)
        # Group A: rep (path2), followed by other member (path1)
        assert tool.candidates == [path3, path4, path2, path1]








