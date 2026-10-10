from pathlib import Path
from unittest.mock import MagicMock, patch
import pytest

class MockDirEntry:
    def __init__(self, path, is_dir_val=False, is_file_val=True):
        self.path = path
        self.name = __import__('os').path.basename(path)
        self._is_dir = is_dir_val
        self._is_file = is_file_val

    def is_dir(self, follow_symlinks=False):
        return self._is_dir

    def is_file(self):
        return self._is_file

class MockScandirContextManager:
    def __init__(self, entries):
        self.entries = entries

    def __enter__(self):
        return iter(self.entries)

    def __exit__(self, exc_type, exc_val, exc_tb):
        pass

def mock_scandir(walk_return):
    def _scandir_mock(path):
        # Convert path to posix string for cross-platform comparison with mock paths
        path_str = __import__('pathlib').Path(path).as_posix()
        entries = []
        for dirpath, dirnames, filenames in walk_return:
            if dirpath == path_str:
                for d in dirnames:
                    p = __import__('os').path.join(dirpath, d)
                    entries.append(MockDirEntry(p, is_dir_val=True, is_file_val=False))
                for f in filenames:
                    p = __import__('os').path.join(dirpath, f)
                    entries.append(MockDirEntry(p, is_dir_val=False, is_file_val=True))
                break
        return MockScandirContextManager(entries)
    return _scandir_mock

# Save original modules to prevent test pollution
original_modules = {}


@pytest.fixture(scope="module", autouse=True)
def mock_sys_modules():
    import sys
    import importlib

    modules_to_mock = [
        "rawpy",
        "photo_selector_toolbox.gui.controllers",
        "photo_selector_toolbox.core.models",
        "photo_selector_toolbox.core.sharpness",
        "photo_selector_toolbox.exif.reader",
        "photo_selector_toolbox.core.utils",
        "photo_selector_toolbox.core.formatting",
        "send2trash",
    ]
    for name in modules_to_mock:
        original_modules[name] = sys.modules.get(name)
        mock_mod = MagicMock()
        if name == "photo_selector_toolbox.core.models":
            orig_mod = importlib.import_module("photo_selector_toolbox.core.models")
            mock_mod.ScanResult.side_effect = orig_mod.ScanResult
            mock_mod.ExifData.side_effect = orig_mod.ExifData
        elif name == "photo_selector_toolbox.core.utils":
            orig_mod = importlib.import_module("photo_selector_toolbox.core.utils")
            mock_mod.select_representative.side_effect = orig_mod.select_representative
            mock_mod.is_excluded_subfolder.return_value = False
        sys.modules[name] = mock_mod

    yield

    for name in modules_to_mock:
        orig = original_modules.get(name)
        if orig is None:
            sys.modules.pop(name, None)
        else:
            sys.modules[name] = orig


@pytest.fixture(autouse=True)
def mock_tkinter_vars():
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
        patch("photo_selector_toolbox.gui.sharpness_tool.tk.Tk"),
        patch(
            "photo_selector_toolbox.gui.sharpness_tool.tk.StringVar",
            side_effect=lambda value=None: DummyVar(value),
        ),
        patch(
            "photo_selector_toolbox.gui.sharpness_tool.tk.IntVar",
            side_effect=lambda value=None: DummyVar(value),
        ),
        patch(
            "photo_selector_toolbox.gui.sharpness_tool.tk.DoubleVar",
            side_effect=lambda value=None: DummyVar(value),
        ),
        patch(
            "photo_selector_toolbox.gui.sharpness_tool.tk.BooleanVar",
            side_effect=lambda value=None: DummyVar(value),
        ),
        patch("photo_selector_toolbox.gui.sharpness_tool.ttk.Frame"),
        patch("photo_selector_toolbox.gui.sharpness_tool.ttk.LabelFrame"),
        patch("photo_selector_toolbox.gui.sharpness_tool.ttk.Label"),
        patch("photo_selector_toolbox.gui.sharpness_tool.ttk.Button"),
        patch("photo_selector_toolbox.gui.sharpness_tool.ttk.Notebook"),
        patch("photo_selector_toolbox.gui.sharpness_tool.ttk.Treeview"),
        patch("photo_selector_toolbox.gui.sharpness_tool.ttk.Scrollbar"),
        patch("photo_selector_toolbox.gui.sharpness_tool.ImageTk.PhotoImage"),
        patch("photo_selector_toolbox.gui.controllers.ImageCacheManager"),
    ):
        yield


def _create_mock_tool():
    from photo_selector_toolbox.gui.sharpness_tool import SharpnessTool

    parent = MagicMock()
    parent.register = MagicMock()
    with (
        patch("photo_selector_toolbox.gui.sharpness_tool.tk.Toplevel"),
        patch("photo_selector_toolbox.gui.sharpness_tool.SharpnessTool.bind_all"),
    ):
        tool = SharpnessTool(parent)
        tool.config = MagicMock()
        tool.update = MagicMock()
        return tool


def test_file_type_filter_persists_across_folder_reload():
    """Verify that when a user filters by .JPG, reloading the folder retains the .JPG filter."""
    tool = _create_mock_tool()
    tool.folder_var.set("/mock/photos")
    tool.file_type_var.set("All Supported")

    walk_return = [("/mock/photos", [], ["photo1.jpg", "photo2.arw", "photo3.jpg", "photo4.png"])]

    with (
        patch("os.walk", return_value=walk_return),
        patch("os.scandir", side_effect=mock_scandir(walk_return)),
        patch("photo_selector_toolbox.exif.reader.SUPPORTED_EXTENSIONS", {".jpg", ".arw", ".png"}),
    ):
        tool._load_folder_contents("/mock/photos")
        assert len(tool.sorted_files) == 4
        assert len(tool.candidates) == 4

        # User selects .JPG filter
        tool.file_type_var.set(".JPG")
        tool.on_file_type_change()
        assert len(tool.candidates) == 2
        assert all(f.suffix.upper() == ".JPG" for f in tool.candidates)

        # External reload / FocusIn / user refresh occurs
        tool._load_folder_contents("/mock/photos")

        # The active filter must STILL be .JPG, and candidates must still be 2
        assert tool.file_type_var.get() == ".JPG"
        assert len(tool.candidates) == 2
        assert all(f.suffix.upper() == ".JPG" for f in tool.candidates)


def test_file_type_filter_fallback_when_extension_absent():
    """Verify graceful fallback to All Supported when target folder contains no matching files."""
    tool = _create_mock_tool()
    tool.file_type_var.set(".CR3")

    walk_return = [("/mock/photos", [], ["photo1.jpg", "photo2.png"])]

    with (
        patch("os.walk", return_value=walk_return),
        patch("os.scandir", side_effect=mock_scandir(walk_return)),
        patch("photo_selector_toolbox.exif.reader.SUPPORTED_EXTENSIONS", {".jpg", ".png"}),
    ):
        tool._load_folder_contents("/mock/photos")

        # Because .CR3 is not among [photo1.jpg, photo2.png], it falls back to All Supported
        assert tool.file_type_var.get() == "All Supported"
        assert len(tool.candidates) == 2


def test_file_type_and_sort_persisted_to_config():
    """Verify on_file_type_change and on_sort_change persist settings to config."""
    tool = _create_mock_tool()

    with patch("photo_selector_toolbox.gui.sharpness_tool.save_config") as mock_save:
        with patch("photo_selector_toolbox.gui.sharpness_tool.load_config", return_value={}):
            tool.file_type_var.set(".NEF")
            tool.on_file_type_change()

            mock_save.assert_called_with({"file_type_filter": ".NEF"})

    with patch("photo_selector_toolbox.gui.sharpness_tool.save_config") as mock_save:
        with patch("photo_selector_toolbox.gui.sharpness_tool.load_config", return_value={}):
            tool.sort_by_var.set("Noise Level")
            tool.sort_order_var.set("Descending")
            tool.on_sort_change()

            mock_save.assert_called_with({
                "sort_by": "Noise Level",
                "sort_order": "Descending",
            })


def test_tool_initializes_with_saved_preferences():
    """Verify SharpnessTool initializes its variables from saved configuration."""
    from photo_selector_toolbox.gui.sharpness_tool import SharpnessTool

    saved_cfg = {
        "file_type_filter": ".ARW",
        "sort_by": "Sharpness Score",
        "sort_order": "Descending",
        "group_similar": False,
        "group_level": "Time & Filename",
    }
    parent = MagicMock()
    parent.register = MagicMock()

    with (
        patch("photo_selector_toolbox.gui.sharpness_tool.load_config", return_value=saved_cfg),
        patch("photo_selector_toolbox.gui.sharpness_tool.tk.Toplevel"),
        patch("photo_selector_toolbox.gui.sharpness_tool.SharpnessTool.bind_all"),
    ):
        tool = SharpnessTool(parent)
        assert tool.file_type_var.get() == ".ARW"
        assert tool.sort_by_var.get() == "Sharpness Score"
        assert tool.sort_order_var.get() == "Descending"


def test_check_and_reload_folder_if_changed_preserves_filter_and_selection():
    """Verify window focus / auto-timer check preserves filter and active selection."""
    tool = _create_mock_tool()
    tool.folder_var.set("/mock/photos")
    tool.file_type_var.set("All Supported")

    walk_1 = [("/mock/photos", [], ["img1.jpg", "img2.jpg", "img3.arw"])]

    with (
        patch("os.walk", return_value=walk_1),
        patch("os.scandir", side_effect=mock_scandir(walk_1)),
        patch("photo_selector_toolbox.exif.reader.SUPPORTED_EXTENSIONS", {".jpg", ".arw"}),
        patch("photo_selector_toolbox.gui.sharpness_tool.Path.exists", return_value=True),
        patch("photo_selector_toolbox.gui.sharpness_tool.Path.is_dir", return_value=True),
    ):
        tool._load_folder_contents("/mock/photos")
        tool.file_type_var.set(".JPG")
        tool.on_file_type_change()

        assert len(tool.candidates) == 2
        selected_file = tool.candidates[1]  # img2.jpg
        tool.candidate_listbox = MagicMock()
        tool.candidate_listbox.curselection.return_value = (1,)

        # Now simulate an external file addition
        walk_2 = [("/mock/photos", [], ["img1.jpg", "img2.jpg", "img3.arw", "img4.jpg"])]
        with patch("os.walk", return_value=walk_2), patch("os.scandir", side_effect=mock_scandir(walk_2)):
            tool._check_and_reload_folder_if_changed()

        # Filter must still be .JPG
        assert tool.file_type_var.get() == ".JPG"
        assert len(tool.candidates) == 3  # img1, img2, img4
        assert all(f.suffix.upper() == ".JPG" for f in tool.candidates)
        # selection_set should be called with 1 (the index of img2.jpg among [img1, img2, img4])
        tool.candidate_listbox.selection_set.assert_called_with(1)
        assert tool.candidates[1] == selected_file


def test_execute_delete_cleans_up_companion_files():
    """Verify execute_delete removes the file and companions from internal collections."""
    tool = _create_mock_tool()

    main_raw = Path("/mock/photos/DSC001.ARW")
    comp_jpg = Path("/mock/photos/DSC001.JPG")
    comp_xmp = Path("/mock/photos/DSC001.xmp")

    tool.sorted_files = [main_raw, comp_jpg]
    tool.candidates = [main_raw, comp_jpg]
    tool.files_map = {main_raw: MagicMock(), comp_jpg: MagicMock()}

    with patch(
        "photo_selector_toolbox.gui.sharpness_tool.find_related_files",
        return_value=[main_raw, comp_jpg, comp_xmp],
    ):
        tool.execute_delete(main_raw, 0)

        # Both main file and companion file must be removed from sorted_files & files_map
        assert main_raw not in tool.sorted_files
        assert comp_jpg not in tool.sorted_files
        assert main_raw not in tool.files_map
        assert comp_jpg not in tool.files_map
