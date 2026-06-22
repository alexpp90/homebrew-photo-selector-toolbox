import importlib
import pytest
from unittest.mock import patch
import photo_selector_toolbox

def test_init_version_success():
    """Test that __version__ is successfully loaded from importlib.metadata."""
    with patch('importlib.metadata.version') as mock_version:
        mock_version.return_value = "1.2.3"
        importlib.reload(photo_selector_toolbox)
        assert photo_selector_toolbox.__version__ == "1.2.3"
        mock_version.assert_called_once_with("photo-selector-toolbox")

def test_init_version_fallback():
    """Test the fallback mechanism when importlib.metadata.version raises an exception."""
    with patch('importlib.metadata.version') as mock_version:
        mock_version.side_effect = Exception("Mock exception")
        importlib.reload(photo_selector_toolbox)
        assert photo_selector_toolbox.__version__ == "0.2.0"
        mock_version.assert_called_once_with("photo-selector-toolbox")

# Restore original state
@pytest.fixture(autouse=True)
def restore_module():
    yield
    importlib.reload(photo_selector_toolbox)
