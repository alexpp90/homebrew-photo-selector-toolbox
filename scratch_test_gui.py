import sys
from unittest.mock import MagicMock, patch
from pathlib import Path

# Setup mocks
sys.modules["rawpy"] = MagicMock()
sys.modules["photo_selector_toolbox.controllers"] = MagicMock()
sys.modules["photo_selector_toolbox.models"] = MagicMock()
sys.modules["photo_selector_toolbox.sharpness"] = MagicMock()
sys.modules["photo_selector_toolbox.reader"] = MagicMock()
sys.modules["photo_selector_toolbox.utils"] = MagicMock()
sys.modules["photo_selector_toolbox.formatting"] = MagicMock()
sys.modules["send2trash"] = MagicMock()

# Now restore real models/reader/formatting to test
sys.modules.pop("photo_selector_toolbox.models", None)
sys.modules.pop("photo_selector_toolbox.reader", None)
sys.modules.pop("photo_selector_toolbox.formatting", None)

from photo_selector_toolbox.models import ScanResult, ExifData
import photo_selector_toolbox.sharpness_gui as sg
from photo_selector_toolbox.sharpness_gui import SharpnessTool

parent = MagicMock()
parent.register = MagicMock()

with (
    patch("photo_selector_toolbox.sharpness_gui.tk.Toplevel"),
    patch("photo_selector_toolbox.sharpness_gui.SharpnessTool.bind_all"),
    patch("photo_selector_toolbox.sharpness_gui.get_exif_data") as mock_get_exif,
    # Mock Tkinter variables and ttk widgets to avoid GUI creation
    patch("photo_selector_toolbox.sharpness_gui.tk.StringVar", return_value=MagicMock()),
    patch("photo_selector_toolbox.sharpness_gui.tk.BooleanVar", return_value=MagicMock()),
    patch("photo_selector_toolbox.sharpness_gui.tk.DoubleVar", return_value=MagicMock()),
    patch("photo_selector_toolbox.sharpness_gui.tk.IntVar", return_value=MagicMock()),
    patch("photo_selector_toolbox.sharpness_gui.ttk.Notebook"),
    patch("photo_selector_toolbox.sharpness_gui.ttk.Frame"),
    patch("photo_selector_toolbox.sharpness_gui.ttk.LabelFrame"),
    patch("photo_selector_toolbox.sharpness_gui.ttk.Label"),
    patch("photo_selector_toolbox.sharpness_gui.ttk.Button"),
    patch("photo_selector_toolbox.sharpness_gui.ttk.Scrollbar"),
    patch("photo_selector_toolbox.sharpness_gui.ttk.Separator"),
):
    # Ensure get_exif_data is a mock
    tool = SharpnessTool(parent)
    tool.meta_lbl = MagicMock()
    
    path = Path("/mock/folder/img1.jpg")
    res = ScanResult(path=path)
    tool.files_map[path] = res
    
    mock_exif = ExifData(iso=100.0, shutter_speed=0.005, aperture=2.8, focal_length=50.0)
    mock_get_exif.return_value = mock_exif
    
    print("MOCKED get_exif_data in sg namespace:", sg.get_exif_data)
    print("MOCKED get_exif_data type:", type(sg.get_exif_data))
    print("is_mocked (hasattr):", hasattr(sg.get_exif_data, "assert_called_once_with"))
    print("is_mocked (name):", type(sg.get_exif_data).__name__ in ('MagicMock', 'Mock'))
    
    tool.update_metadata_label(path)
    
    print("Call count:", mock_get_exif.call_count)
    print("res.exif:", res.exif)
