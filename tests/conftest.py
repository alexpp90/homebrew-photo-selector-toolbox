import os
import sys
import pytest


# ── Platform markers ─────────────────────────────────────────────────
# Usage:  @pytest.mark.linux_only / @pytest.mark.mac_only / etc.

def pytest_configure(config):
    config.addinivalue_line("markers", "linux_only: skip unless running on Linux")
    config.addinivalue_line("markers", "mac_only: skip unless running on macOS")
    config.addinivalue_line("markers", "windows_only: skip unless running on Windows")
    config.addinivalue_line("markers", "gui_required: skip when no display is available")


def pytest_collection_modifyitems(config, items):
    platform = sys.platform
    for item in items:
        if "linux_only" in item.keywords and platform != "linux":
            item.add_marker(pytest.mark.skip(reason="Linux only"))
        if "mac_only" in item.keywords and platform != "darwin":
            item.add_marker(pytest.mark.skip(reason="macOS only"))
        if "windows_only" in item.keywords and platform != "win32":
            item.add_marker(pytest.mark.skip(reason="Windows only"))
        if "gui_required" in item.keywords and not _display_available():
            item.add_marker(pytest.mark.skip(reason="No display available"))


def _display_available():
    """Check if a display is available for GUI tests."""
    if sys.platform == "win32" or sys.platform == "darwin":
        return True  # Windows/macOS always have a display context
    return bool(os.environ.get("DISPLAY"))


# ── Fixtures ─────────────────────────────────────────────────────────

@pytest.fixture(autouse=True)
def test_db_env(tmp_path):
    """Ensure ScoreCache uses a temp database during tests."""
    try:
        from photo_selector_toolbox.cache import set_default_db_path
        db_path = tmp_path / "test_scores_cache.db"
        set_default_db_path(db_path)
        yield
        set_default_db_path(None)
    except ImportError:
        # If the cache module isn't loaded/created yet during initial test setups
        yield
