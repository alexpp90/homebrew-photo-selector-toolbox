import json
import stat
from unittest.mock import patch

import pytest

from photo_selector_toolbox.config import (
    load_config,
    save_config,
    add_recent_folder,
    get_recent_folders,
    is_ollama_url_external,
    _set_secure_permissions,
    DEFAULT_CONFIG,
    _OLD_PROMPTS,
    MAX_RECENT_FOLDERS,
)


@pytest.fixture
def mock_config_paths(tmp_path):
    config_dir = tmp_path / ".photo_selector_toolbox"
    config_file = config_dir / "settings.json"

    with (
        patch("photo_selector_toolbox.config.CONFIG_DIR", config_dir),
        patch("photo_selector_toolbox.config.CONFIG_FILE", config_file),
    ):
        yield config_dir, config_file


def test_load_config_creates_default_when_missing(mock_config_paths):
    config_dir, config_file = mock_config_paths

    loaded_config = load_config()

    assert config_dir.exists()
    assert config_file.exists()
    assert loaded_config == DEFAULT_CONFIG

    with open(config_file, "r", encoding="utf-8") as f:
        saved_config = json.load(f)
    assert saved_config == DEFAULT_CONFIG


def test_load_config_merges_user_config(mock_config_paths):
    config_dir, config_file = mock_config_paths
    config_dir.mkdir()

    user_config = {"ollama_url": "http://custom-url:11434"}
    with open(config_file, "w", encoding="utf-8") as f:
        json.dump(user_config, f)

    loaded_config = load_config()

    assert loaded_config["ollama_url"] == "http://custom-url:11434"
    assert loaded_config["selection_folder"] == DEFAULT_CONFIG["selection_folder"]


def test_load_config_migrates_old_prompts(mock_config_paths):
    config_dir, config_file = mock_config_paths
    config_dir.mkdir()

    user_config = {"ollama_prompt": _OLD_PROMPTS[0]}
    with open(config_file, "w", encoding="utf-8") as f:
        json.dump(user_config, f)

    loaded_config = load_config()

    assert loaded_config["ollama_prompt"] == DEFAULT_CONFIG["ollama_prompt"]

    with open(config_file, "r", encoding="utf-8") as f:
        saved_config = json.load(f)
    assert saved_config["ollama_prompt"] == DEFAULT_CONFIG["ollama_prompt"]


def test_load_config_handles_invalid_json(mock_config_paths, caplog):
    config_dir, config_file = mock_config_paths
    config_dir.mkdir()

    with open(config_file, "w", encoding="utf-8") as f:
        f.write("invalid json")

    loaded_config = load_config()

    assert loaded_config == DEFAULT_CONFIG
    assert "Failed to load or write config file" in caplog.text


def test_save_config_creates_dir_and_saves(mock_config_paths):
    config_dir, config_file = mock_config_paths

    test_config = {"test_key": "test_value"}
    save_config(test_config)

    assert config_dir.exists()
    assert config_file.exists()

    with open(config_file, "r", encoding="utf-8") as f:
        saved_config = json.load(f)
    assert saved_config == test_config


def test_save_config_handles_exceptions(mock_config_paths, caplog):
    config_dir, config_file = mock_config_paths

    # Force an exception by making the directory an unwriteable file instead
    config_dir.parent.mkdir(parents=True, exist_ok=True)
    with open(config_dir, "w") as f:
        f.write("")

    save_config({"test": "data"})

    assert "Failed to save config" in caplog.text


@patch("photo_selector_toolbox.config.load_config")
@patch("photo_selector_toolbox.config.save_config")
def test_add_recent_folder_empty(mock_save, mock_load):
    mock_load.return_value = {"recent_folders": []}

    add_recent_folder("/path/to/folder")

    mock_save.assert_called_once_with({"recent_folders": ["/path/to/folder"]})


@patch("photo_selector_toolbox.config.load_config")
@patch("photo_selector_toolbox.config.save_config")
def test_add_recent_folder_duplicate_moves_to_front(mock_save, mock_load):
    mock_load.return_value = {
        "recent_folders": ["/folder1", "/path/to/folder", "/folder2"]
    }

    add_recent_folder("/path/to/folder")

    mock_save.assert_called_once_with(
        {"recent_folders": ["/path/to/folder", "/folder1", "/folder2"]}
    )


@patch("photo_selector_toolbox.config.load_config")
@patch("photo_selector_toolbox.config.save_config")
def test_add_recent_folder_max_limit(mock_save, mock_load):
    initial_folders = [f"/folder{i}" for i in range(MAX_RECENT_FOLDERS)]
    mock_load.return_value = {"recent_folders": initial_folders.copy()}

    add_recent_folder("/new/folder")

    expected_folders = ["/new/folder"] + initial_folders[:-1]
    mock_save.assert_called_once_with({"recent_folders": expected_folders})


@patch("photo_selector_toolbox.config.load_config")
def test_get_recent_folders(mock_load):
    mock_load.return_value = {"recent_folders": ["/folder1", "/folder2"]}

    folders = get_recent_folders()

    assert folders == ["/folder1", "/folder2"]


@patch("photo_selector_toolbox.config.load_config")
def test_get_recent_folders_empty(mock_load):
    mock_load.return_value = {}

    folders = get_recent_folders()

    assert folders == []


@pytest.mark.parametrize(
    "url,expected",
    [
        ("http://localhost:11434", False),
        ("http://127.0.0.1:11434", False),
        ("http://[::1]:11434", False),
        ("http://0.0.0.0:11434", False),
        ("http://example.com:11434", True),
        ("http://192.168.1.100:11434", True),
    ],
)
def test_is_ollama_url_external(url, expected):
    assert is_ollama_url_external(url) == expected


@patch("photo_selector_toolbox.config.load_config")
def test_is_ollama_url_external_fallback_internal(mock_load):
    mock_load.return_value = {"ollama_url": "http://localhost:11434"}

    assert not is_ollama_url_external()


@patch("photo_selector_toolbox.config.load_config")
def test_is_ollama_url_external_fallback_external(mock_load):
    mock_load.return_value = {"ollama_url": "http://192.168.1.100:11434"}

    assert is_ollama_url_external()


def test_is_ollama_url_external_exception():
    # Provide an invalid URL that will cause urlparse to fail or another exception
    # e.g., something that throws a ValueError during parsing, or we can mock it
    with patch("urllib.parse.urlparse", side_effect=ValueError("Invalid URL")):
        assert is_ollama_url_external("http://bad-url")


@patch("os.chmod")
def test_set_secure_permissions(mock_chmod, tmp_path):
    test_file = tmp_path / "test.txt"
    _set_secure_permissions(test_file)

    mock_chmod.assert_called_once_with(test_file, stat.S_IRUSR | stat.S_IWUSR)


@patch("os.chmod")
def test_set_secure_permissions_oserror(mock_chmod, tmp_path):
    mock_chmod.side_effect = OSError("chmod not supported")
    test_file = tmp_path / "test.txt"

    # This should not raise an exception
    _set_secure_permissions(test_file)

    mock_chmod.assert_called_once_with(test_file, stat.S_IRUSR | stat.S_IWUSR)
