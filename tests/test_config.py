

import json
from pathlib import Path
from unittest.mock import patch, MagicMock
from photo_selector_toolbox.config import (
    load_config,
    save_config,
    add_recent_folder,
    get_recent_folders,
    is_ollama_url_external,
    _set_secure_permissions,
    DEFAULT_CONFIG,
    _OLD_PROMPTS
)

def test_load_config_exception_handling():
    """Test that load_config handles exceptions and returns DEFAULT_CONFIG."""
    with patch("photo_selector_toolbox.config.Path.exists", side_effect=Exception("Test Exception")):
        config = load_config()
        assert config == DEFAULT_CONFIG

def test_save_config_exception_handling():
    """Test that save_config handles exceptions gracefully."""
    with patch("photo_selector_toolbox.config.Path.exists", side_effect=Exception("Test Exception")):
        # This shouldn't raise an exception because of the try...except
        save_config(DEFAULT_CONFIG)

def test_load_config_migration():
    """Test that load_config migrates old prompts and saves."""
    old_config = DEFAULT_CONFIG.copy()
    old_config["ollama_prompt"] = _OLD_PROMPTS[0]

    with patch("photo_selector_toolbox.config.Path.exists", return_value=True):
        with patch("builtins.open", MagicMock()) as mock_open:
            mock_file = MagicMock()
            mock_file.read.return_value = json.dumps(old_config)
            mock_open.return_value.__enter__.return_value = mock_file

            with patch("photo_selector_toolbox.config.json.load", return_value=old_config):
                with patch("photo_selector_toolbox.config.save_config") as mock_save:
                    config = load_config()

                    assert config["ollama_prompt"] == DEFAULT_CONFIG["ollama_prompt"]
                    mock_save.assert_called_once()

def test_load_config_not_exists(tmp_path):
    """Test load_config when directory and file do not exist."""
    with patch("photo_selector_toolbox.config.CONFIG_DIR", tmp_path / "config"):
        with patch("photo_selector_toolbox.config.CONFIG_FILE", tmp_path / "config" / "settings.json"):
            config = load_config()
            assert config == DEFAULT_CONFIG
            assert (tmp_path / "config" / "settings.json").exists()

def test_load_config_exists(tmp_path):
    """Test load_config when file exists with custom config."""
    config_dir = tmp_path / "config"
    config_dir.mkdir()
    config_file = config_dir / "settings.json"

    custom_config = {"ollama_url": "http://custom:11434"}
    with open(config_file, "w") as f:
        json.dump(custom_config, f)

    with patch("photo_selector_toolbox.config.CONFIG_DIR", config_dir):
        with patch("photo_selector_toolbox.config.CONFIG_FILE", config_file):
            config = load_config()
            assert config["ollama_url"] == "http://custom:11434"
            # Should have other default keys
            assert config["ollama_model"] == DEFAULT_CONFIG["ollama_model"]

def test_save_config_success(tmp_path):
    """Test successful save_config."""
    config_dir = tmp_path / "config"
    config_file = config_dir / "settings.json"

    with patch("photo_selector_toolbox.config.CONFIG_DIR", config_dir):
        with patch("photo_selector_toolbox.config.CONFIG_FILE", config_file):
            save_config({"test_key": "test_val"})

            assert config_file.exists()
            with open(config_file, "r") as f:
                saved = json.load(f)
                assert saved["test_key"] == "test_val"

def test_set_secure_permissions_oserror():
    """Test _set_secure_permissions handles OSError."""
    with patch("os.chmod", side_effect=OSError("Not supported")):
        # Should not raise exception
        _set_secure_permissions(Path("dummy"))

def test_add_and_get_recent_folders():
    """Test adding and getting recent folders."""
    mock_config = DEFAULT_CONFIG.copy()

    def mock_load():
        return mock_config.copy()

    def mock_save(c):
        mock_config.update(c)

    with patch("photo_selector_toolbox.config.load_config", side_effect=mock_load):
        with patch("photo_selector_toolbox.config.save_config", side_effect=mock_save):
            add_recent_folder("/test/folder1")
            assert get_recent_folders() == ["/test/folder1"]

            add_recent_folder("/test/folder2")
            assert get_recent_folders() == ["/test/folder2", "/test/folder1"]

            # Test duplicate adds it to the front
            add_recent_folder("/test/folder1")
            assert get_recent_folders() == ["/test/folder1", "/test/folder2"]

            # Test max folders
            for i in range(15):
                add_recent_folder(f"/test/f{i}")

            recent = get_recent_folders()
            assert len(recent) == 10
            assert recent[0] == "/test/f14"

def test_is_ollama_url_external():
    """Test is_ollama_url_external with various URLs."""
    # Local URLs
    assert not is_ollama_url_external("http://localhost:11434")
    assert not is_ollama_url_external("http://127.0.0.1:11434")
    assert not is_ollama_url_external("http://[::1]:11434")
    assert not is_ollama_url_external("http://0.0.0.0:11434")

    # External URLs
    assert is_ollama_url_external("http://example.com:11434")
    assert is_ollama_url_external("http://192.168.1.100:11434")

    # None fallback to load_config
    with patch("photo_selector_toolbox.config.load_config", return_value={"ollama_url": "http://example.com:11434"}):
        assert is_ollama_url_external(None)

    # Exception handling
    with patch("urllib.parse.urlparse", side_effect=Exception("Parse error")):
        assert is_ollama_url_external("http://localhost:11434")
