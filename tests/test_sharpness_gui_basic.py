import pytest
from unittest.mock import patch, MagicMock
import sys

# Save original modules to prevent test pollution
original_modules = {}

@pytest.fixture(scope="module", autouse=True)
def mock_sys_modules():
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
        if name == "photo_selector_toolbox.utils":
            mock_mod.is_excluded_subfolder.return_value = False
        sys.modules[name] = mock_mod
        
    yield
    
    for name in modules_to_mock:
        orig = original_modules[name]
        if orig is None:
            sys.modules.pop(name, None)
        else:
            sys.modules[name] = orig


@pytest.fixture(autouse=True)
def mock_sharpness_gui_deps():
    with (
        patch("photo_selector_toolbox.sharpness_gui.tk.Tk"),
        patch(
            "photo_selector_toolbox.sharpness_gui.tk.StringVar",
            return_value=MagicMock(),
        ),
        patch(
            "photo_selector_toolbox.sharpness_gui.tk.IntVar", return_value=MagicMock()
        ),
        patch(
            "photo_selector_toolbox.sharpness_gui.tk.DoubleVar",
            return_value=MagicMock(),
        ),
        patch(
            "photo_selector_toolbox.sharpness_gui.tk.BooleanVar",
            return_value=MagicMock(),
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
        
        with patch("photo_selector_toolbox.sharpness_gui.Path.rglob", return_value=test_files):
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
                assert tool._get_candidate_listbox_text(path) == "img1.jpg (Sharpness: 42.5)"
                
                # Test case 3: Sharpness and Noise calculated
                tool.files_map[path] = ScanResult(path=path, score=42.5, noise_score=1.2)
                assert tool._get_candidate_listbox_text(path) == "img1.jpg (Sharpness: 42.5, Noise: 1.2)"
                
                # Test case 4: Sharpness, Noise, and Highlight/Shadow clipping calculated
                res = ScanResult(path=path, score=42.5, noise_score=1.2)
                res.scores["highlight_clipping"] = 0.5
                res.scores["shadow_clipping"] = 1.0
                tool.files_map[path] = res
                assert tool._get_candidate_listbox_text(path) == "img1.jpg (Sharpness: 42.5, Noise: 1.2, HL: 0.5%, SD: 1.0%)"
        finally:
            sg.format_score = original_format_score
    finally:
        # Re-mock models
        if mock_models:
            sys.modules["photo_selector_toolbox.models"] = mock_models


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
    if orig_reader:
        sys.modules["photo_selector_toolbox.reader"] = orig_reader
    if orig_formatting:
        sys.modules["photo_selector_toolbox.formatting"] = orig_formatting
        
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
        if mock_reader:
            sys.modules["photo_selector_toolbox.reader"] = mock_reader
        if mock_formatting:
            sys.modules["photo_selector_toolbox.formatting"] = mock_formatting





