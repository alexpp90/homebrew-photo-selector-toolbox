import json
import os
import stat
from pathlib import Path
from unittest.mock import patch, mock_open

import pytest

from photo_selector_toolbox.config import (
    load_config,
    save_config,
    add_recent_folder,
    get_recent_folders,
    is_ollama_url_external,
    DEFAULT_CONFIG,
    _OLD_PROMPTS
)
import photo_selector_toolbox.config as config_mod


@pytest.fixture
def temp_config_dir(tmp_path):
    """Fixture to mock CONFIG_DIR and CONFIG_FILE to a temporary path."""
    with patch.object(config_mod, "CONFIG_DIR", tmp_path), \
         patch.object(config_mod, "CONFIG_FILE", tmp_path / "settings.json"):
        yield tmp_path


def test_load_config_no_dir_no_file(temp_config_dir):
    """Test load_config when neither the config directory nor the config file exists."""
    # Ensure directory doesn't exist initially
    if config_mod.CONFIG_DIR.exists():
        config_mod.CONFIG_DIR.rmdir()

    config = load_config()

    assert config == DEFAULT_CONFIG
    assert config_mod.CONFIG_DIR.exists()
    assert config_mod.CONFIG_FILE.exists()

    with open(config_mod.CONFIG_FILE, "r", encoding="utf-8") as f:
        saved_config = json.load(f)
    assert saved_config == DEFAULT_CONFIG


def test_load_config_with_existing_file(temp_config_dir):
    """Test load_config when the config file already exists."""
    custom_config = {"ollama_model": "test-model"}

    config_mod.CONFIG_DIR.mkdir(parents=True, exist_ok=True)
    with open(config_mod.CONFIG_FILE, "w", encoding="utf-8") as f:
        json.dump(custom_config, f)

    config = load_config()

    assert config["ollama_model"] == "test-model"
    # Make sure default config properties not present in user config are loaded
    assert config["ollama_url"] == DEFAULT_CONFIG["ollama_url"]

def test_load_config_migration(temp_config_dir):
    """Test that old prompts are migrated to new ones."""
    for old_prompt in _OLD_PROMPTS:
        custom_config = {"ollama_prompt": old_prompt}

        config_mod.CONFIG_DIR.mkdir(parents=True, exist_ok=True)
        with open(config_mod.CONFIG_FILE, "w", encoding="utf-8") as f:
            json.dump(custom_config, f)

        config = load_config()

        # Verify it migrated in memory
        assert config["ollama_prompt"] == DEFAULT_CONFIG["ollama_prompt"]

        # Verify it migrated on disk
        with open(config_mod.CONFIG_FILE, "r", encoding="utf-8") as f:
            disk_config = json.load(f)
        assert disk_config["ollama_prompt"] == DEFAULT_CONFIG["ollama_prompt"]

def test_load_config_error_handling(temp_config_dir):
    """Test that load_config returns default config when there is an error."""
    # Create invalid JSON file
    config_mod.CONFIG_DIR.mkdir(parents=True, exist_ok=True)
    with open(config_mod.CONFIG_FILE, "w", encoding="utf-8") as f:
        f.write("invalid json")

    config = load_config()

    # Should fall back to default configuration
    assert config == DEFAULT_CONFIG

def test_save_config(temp_config_dir):
    """Test save_config writes to file correctly."""
    custom_config = DEFAULT_CONFIG.copy()
    custom_config["ollama_model"] = "new-model"

    save_config(custom_config)

    assert config_mod.CONFIG_FILE.exists()
    with open(config_mod.CONFIG_FILE, "r", encoding="utf-8") as f:
        saved_config = json.load(f)

    assert saved_config["ollama_model"] == "new-model"

def test_save_config_error_handling(temp_config_dir):
    """Test save_config handles errors gracefully."""
    custom_config = DEFAULT_CONFIG.copy()

    # Force an OSError by making directory readonly
    config_mod.CONFIG_DIR.mkdir(parents=True, exist_ok=True)

    with patch("builtins.open", side_effect=OSError("Read-only file system")):
        # Should catch the error and not crash
        save_config(custom_config)

def test_recent_folders(temp_config_dir):
    """Test add_recent_folder and get_recent_folders."""
    # Add one folder
    add_recent_folder("/path/to/folder1")
    assert get_recent_folders() == ["/path/to/folder1"]

    # Add another
    add_recent_folder("/path/to/folder2")
    assert get_recent_folders() == ["/path/to/folder2", "/path/to/folder1"]

    # Add existing, should move to front
    add_recent_folder("/path/to/folder1")
    assert get_recent_folders() == ["/path/to/folder1", "/path/to/folder2"]

    # Max size enforcement
    for i in range(15):
        add_recent_folder(f"/path/to/folder_{i}")

    recent = get_recent_folders()
    assert len(recent) == config_mod.MAX_RECENT_FOLDERS
    assert recent[0] == "/path/to/folder_14"

def test_is_ollama_url_external(temp_config_dir):
    """Test is_ollama_url_external function."""
    assert is_ollama_url_external("http://localhost:11434") is False
    assert is_ollama_url_external("http://127.0.0.1:11434") is False
    assert is_ollama_url_external("http://[::1]:11434") is False
    assert is_ollama_url_external("http://0.0.0.0:11434") is False

    assert is_ollama_url_external("http://192.168.1.100:11434") is True
    assert is_ollama_url_external("http://my-server:11434") is True

    # Test without argument, should load from config
    assert is_ollama_url_external() is False

    # Update config to have external URL
    config = DEFAULT_CONFIG.copy()
    config["ollama_url"] = "http://my-server:11434"
    save_config(config)
    assert is_ollama_url_external() is True

def test_is_ollama_url_external_invalid_url():
    """Test is_ollama_url_external handles invalid URLs."""
    # This should trigger the exception block and return True
    assert is_ollama_url_external("not-a-url") is True

def test_set_secure_permissions_error(temp_config_dir):
    """Test _set_secure_permissions handles OSError gracefully."""
    from photo_selector_toolbox.config import _set_secure_permissions

    with patch("os.chmod", side_effect=OSError("Not supported")):
        # Should not raise exception
        _set_secure_permissions(temp_config_dir / "fake_file")
