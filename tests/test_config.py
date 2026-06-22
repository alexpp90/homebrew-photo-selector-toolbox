import json
import os
import stat
from pathlib import Path
from unittest.mock import patch, MagicMock

import pytest

from photo_selector_toolbox.config import (
    _set_secure_permissions,
    load_config,
    save_config,
    DEFAULT_CONFIG,
    CONFIG_FILE,
)


def test_set_secure_permissions_oserror():
    """Test that OSError is safely ignored when setting permissions."""
    dummy_path = Path("/dummy/path")
    with patch("os.chmod", side_effect=OSError("Permission denied")) as mock_chmod:
        _set_secure_permissions(dummy_path)
        mock_chmod.assert_called_once_with(dummy_path, stat.S_IRUSR | stat.S_IWUSR)


def test_load_config_creates_dir(tmp_path):
    """Test that load_config creates the configuration directory if it doesn't exist."""
    with patch("photo_selector_toolbox.config.CONFIG_DIR", tmp_path / "new_dir"), \
         patch("photo_selector_toolbox.config.CONFIG_FILE", tmp_path / "new_dir" / "settings.json"):

        config_dir = tmp_path / "new_dir"
        assert not config_dir.exists()

        # This will hit the missing coverage on lines 97-98
        loaded = load_config()

        assert config_dir.exists()
        assert loaded == DEFAULT_CONFIG


def test_save_config_creates_dir(tmp_path):
    """Test that save_config creates the configuration directory if it doesn't exist."""
    with patch("photo_selector_toolbox.config.CONFIG_DIR", tmp_path / "save_dir"), \
         patch("photo_selector_toolbox.config.CONFIG_FILE", tmp_path / "save_dir" / "settings.json"):

        config_dir = tmp_path / "save_dir"
        assert not config_dir.exists()

        # This will hit the missing coverage on lines 126-127
        save_config(DEFAULT_CONFIG)

        assert config_dir.exists()
        assert (config_dir / "settings.json").exists()


def test_is_ollama_url_external_exception():
    """Test is_ollama_url_external exception handling (lines 166-167)."""
    from photo_selector_toolbox.config import is_ollama_url_external
    with patch("urllib.parse.urlparse", side_effect=Exception("Parse error")):
        # Should return True on exception
        result = is_ollama_url_external("http://some-url")
        assert result is True
