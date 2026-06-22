import json
import logging
from unittest.mock import MagicMock, patch
import pytest

from photo_selector_toolbox.config import (
    load_config,
    save_config,
    is_ollama_url_external,
    DEFAULT_CONFIG,
    CONFIG_DIR,
    CONFIG_FILE,
)

@pytest.fixture
def temp_config_dir(tmp_path):
    with patch("photo_selector_toolbox.config.CONFIG_DIR", tmp_path), \
         patch("photo_selector_toolbox.config.CONFIG_FILE", tmp_path / "settings.json"):
        yield tmp_path

def test_load_config_mkdir(temp_config_dir):
    """Test load_config creates directory if it does not exist."""
    assert not temp_config_dir.exists() or not (temp_config_dir / "settings.json").exists()

    # Remove directory if it was somehow created
    if temp_config_dir.exists():
        import shutil
        shutil.rmtree(temp_config_dir)

    assert not temp_config_dir.exists()

    config = load_config()
    assert temp_config_dir.exists()
    assert config == DEFAULT_CONFIG

def test_save_config_mkdir(temp_config_dir):
    """Test save_config creates directory if it does not exist."""
    # Remove directory if it was somehow created
    if temp_config_dir.exists():
        import shutil
        shutil.rmtree(temp_config_dir)

    assert not temp_config_dir.exists()

    save_config(DEFAULT_CONFIG)
    assert temp_config_dir.exists()
    assert (temp_config_dir / "settings.json").exists()

def test_save_config_error(temp_config_dir, caplog):
    """Test save_config handles and logs exceptions properly."""
    with patch("builtins.open", side_effect=OSError("Mocked permission denied")):
        with caplog.at_level(logging.ERROR):
            save_config(DEFAULT_CONFIG)

    assert "Failed to save config" in caplog.text
    assert "Mocked permission denied" in caplog.text

def test_is_ollama_url_external_error():
    """Test is_ollama_url_external exception handling."""
    with patch("urllib.parse.urlparse", side_effect=ValueError("Invalid URL")):
        assert is_ollama_url_external("http://invalid-url") is True
