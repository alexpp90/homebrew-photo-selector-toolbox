import importlib
from unittest.mock import patch
import pytest
import photo_selector_toolbox

@pytest.fixture
def reload_module():
    yield
    # Restore the original module state after the test
    importlib.reload(photo_selector_toolbox)

def test_version_from_metadata(reload_module):
    with patch("importlib.metadata.version") as mock_version:
        mock_version.return_value = "9.9.9"
        importlib.reload(photo_selector_toolbox)
        assert photo_selector_toolbox.__version__ == "9.9.9"

def test_version_fallback_on_exception(reload_module):
    # Extract the hardcoded version from the source file dynamically to avoid
    # assertion errors if the version is bumped
    with open(photo_selector_toolbox.__file__, "r") as f:
        content = f.read()
    fallback_version = "0.4.0"
    for line in content.splitlines():
        if "__version__ =" in line and "version(" not in line:
            fallback_version = line.split("=")[1].strip().strip("\"'")
            break

    with patch("importlib.metadata.version") as mock_version:
        mock_version.side_effect = Exception("Not found")
        importlib.reload(photo_selector_toolbox)
        assert photo_selector_toolbox.__version__ == fallback_version
