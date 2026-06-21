import pytest
from unittest.mock import patch
from photo_selector_toolbox.ollama_tool import OllamaAestheticTool
from PIL import Image

@pytest.fixture
def dummy_image_file(tmp_path):
    img_path = tmp_path / "test_photo.jpg"
    img = Image.new("RGB", (100, 100), color="red")
    img.save(img_path, format="JPEG")
    return img_path

@pytest.fixture
def temp_config_dir(tmp_path):
    from photo_selector_toolbox import config
    from photo_selector_toolbox import ollama_tool
    import photo_selector_toolbox.sharpness_gui as sg
    import photo_selector_toolbox.cache as cache

    orig_dir = config.CONFIG_DIR
    orig_file = config.CONFIG_FILE

    config.CONFIG_DIR = tmp_path
    config.CONFIG_FILE = tmp_path / "settings.json"
    ollama_tool.CONFIG_DIR = config.CONFIG_DIR
    ollama_tool.CONFIG_FILE = config.CONFIG_FILE
    sg.CONFIG_DIR = config.CONFIG_DIR
    sg.CONFIG_FILE = config.CONFIG_FILE

    cache._DEFAULT_DB_PATH = tmp_path / "test_cache.db"

    yield tmp_path

    config.CONFIG_DIR = orig_dir
    config.CONFIG_FILE = orig_file
    ollama_tool.CONFIG_DIR = orig_dir
    ollama_tool.CONFIG_FILE = orig_file
    sg.CONFIG_DIR = orig_dir
    sg.CONFIG_FILE = orig_file
    cache._DEFAULT_DB_PATH = None

@patch("urllib.request.urlopen")
def test_ollama_tool_blocks_metadata_ip(mock_urlopen, dummy_image_file, temp_config_dir):
    from photo_selector_toolbox.config import save_config
    save_config({
        "ollama_url": "http://169.254.169.254/latest/meta-data/",
        "ollama_model": "test",
        "ollama_prompt": "test"
    })
    tool = OllamaAestheticTool()
    with pytest.raises(RuntimeError, match="SSRF Protection: Cloud metadata IPs are not allowed."):
        tool.analyze(dummy_image_file)
