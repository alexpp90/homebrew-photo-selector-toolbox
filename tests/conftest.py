import pytest
from pathlib import Path

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
