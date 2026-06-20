import json
import urllib.error
from unittest.mock import MagicMock, patch
import pytest
from PIL import Image

from photo_selector_toolbox.ollama_tool import (
    OllamaAestheticTool,
    load_config,
    save_config,
    DEFAULT_CONFIG,
)
import photo_selector_toolbox.ollama_tool as ot


@pytest.fixture
def temp_config_dir(tmp_path):
    """Fixture to mock CONFIG_DIR and CONFIG_FILE to a temporary path."""
    import photo_selector_toolbox.config as cfg_mod
    with patch.object(cfg_mod, "CONFIG_DIR", tmp_path), \
         patch.object(cfg_mod, "CONFIG_FILE", tmp_path / "settings.json"), \
         patch.object(ot, "CONFIG_DIR", tmp_path), \
         patch.object(ot, "CONFIG_FILE", tmp_path / "settings.json"):
        yield tmp_path


def test_load_save_config(temp_config_dir):
    # Test loading when no file exists (should create defaults)
    config = load_config()
    assert config == DEFAULT_CONFIG
    assert ot.CONFIG_FILE.exists()

    # Modify and save
    config["ollama_model"] = "test-vlm-model"
    save_config(config)

    # Reload and verify
    reloaded = load_config()
    assert reloaded["ollama_model"] == "test-vlm-model"


@pytest.fixture
def dummy_image_file(tmp_path):
    """Create a temporary dummy JPEG image."""
    img_path = tmp_path / "test_photo.jpg"
    img = Image.new("RGB", (100, 100), color="red")
    img.save(img_path, format="JPEG")
    return img_path


@patch("urllib.request.urlopen")
def test_ollama_tool_success(mock_urlopen, dummy_image_file, temp_config_dir):
    # Mock Ollama HTTP response payload
    mock_resp_payload = {"response": "[SCORE: 8.5] [ANALYSIS: Great lighting] The photo has nice lighting."}

    mock_response = MagicMock()
    mock_response.read.return_value = json.dumps(mock_resp_payload).encode("utf-8")
    mock_response.__enter__.return_value = mock_response
    mock_urlopen.return_value = mock_response

    tool = OllamaAestheticTool()
    score, tag = tool.analyze(dummy_image_file)

    assert score == 8.5
    assert tag == "Great lighting"
    mock_urlopen.assert_called_once()
    # Check that URL used default
    req = mock_urlopen.call_args[0][0]
    assert req.full_url == "http://localhost:11434/api/generate"
    assert req.method == "POST"


@patch("urllib.request.urlopen")
def test_ollama_tool_custom_config(mock_urlopen, dummy_image_file, temp_config_dir):
    # Setup custom configuration
    custom_config = {
        "ollama_url": "http://192.168.1.50:11434/",
        "ollama_model": "qwen-vl",
        "ollama_prompt": "Rate the aesthetics of the photo. Score: ",
    }
    save_config(custom_config)

    # Mock response
    mock_resp_payload = {"response": "[SCORE: 9.3] [ANALYSIS: Sharp] Score: 9.3 out of 10"}
    mock_response = MagicMock()
    mock_response.read.return_value = json.dumps(mock_resp_payload).encode("utf-8")
    mock_response.__enter__.return_value = mock_response
    mock_urlopen.return_value = mock_response

    tool = OllamaAestheticTool()
    score, tag = tool.analyze(dummy_image_file)

    assert score == 9.3
    assert tag == "Sharp"
    req = mock_urlopen.call_args[0][0]
    assert req.full_url == "http://192.168.1.50:11434/api/generate"

    # Assert JSON payload parameters match config
    body = json.loads(req.data.decode("utf-8"))
    assert body["model"] == "qwen-vl"
    assert body["prompt"] == "Rate the aesthetics of the photo. Score: "


@patch("urllib.request.urlopen")
def test_ollama_tool_connection_error(mock_urlopen, dummy_image_file, temp_config_dir):
    # Mock connection failure (URLError)
    mock_urlopen.side_effect = urllib.error.URLError("Connection refused")

    tool = OllamaAestheticTool()
    with pytest.raises(RuntimeError) as exc_info:
        tool.analyze(dummy_image_file)

    assert "Ollama server connection error" in str(exc_info.value)


@patch("urllib.request.urlopen")
def test_ollama_tool_parsing_error(mock_urlopen, dummy_image_file, temp_config_dir):
    # Mock response with no float scores in text
    mock_resp_payload = {"response": "This is a photo of a sunset, it looks great but I can't give a number."}

    mock_response = MagicMock()
    mock_response.read.return_value = json.dumps(mock_resp_payload).encode("utf-8")
    mock_response.__enter__.return_value = mock_response
    mock_urlopen.return_value = mock_response

    tool = OllamaAestheticTool()
    with pytest.raises(RuntimeError) as exc_info:
        tool.analyze(dummy_image_file)

    assert "Could not parse a numeric score from Ollama output" in str(exc_info.value)


def test_load_config_migration(temp_config_dir):
    # Write an old config file directly
    old_prompt = (
        "Rate this photo's aesthetic quality on a scale of 1.0 (horrible) to 10.0 (outstanding / perfect). "
        "Consider composition, lighting, focus, and subject. "
        "Provide your reasoning in one short sentence, then end your response "
        "with the score in the format [SCORE: X.Y]."
    )
    old_config = {
        "ollama_url": "http://localhost:11434",
        "ollama_model": "llava",
        "ollama_prompt": old_prompt,
    }
    with open(ot.CONFIG_FILE, "w", encoding="utf-8") as f:
        json.dump(old_config, f, indent=4)

    # Call load_config, which should trigger the migration
    loaded = load_config()
    assert loaded["ollama_prompt"] == DEFAULT_CONFIG["ollama_prompt"]

    # Now test the second legacy default prompt format
    old_prompt_2 = (
        "Rate this photo's aesthetic quality on a scale of 1.0 (horrible) to 10.0 (outstanding / perfect). "
        "Consider composition, lighting, focus, and subject. "
        "Start your response with the score in the format [SCORE: X.Y], followed by a short sentence of reasoning."
    )
    old_config_2 = {
        "ollama_url": "http://localhost:11434",
        "ollama_model": "llava",
        "ollama_prompt": old_prompt_2,
    }
    with open(ot.CONFIG_FILE, "w", encoding="utf-8") as f:
        json.dump(old_config_2, f, indent=4)

    loaded_2 = load_config()
    assert loaded_2["ollama_prompt"] == DEFAULT_CONFIG["ollama_prompt"]

    # Now test the third legacy default prompt format (previous version)
    old_prompt_3 = (
        "Rate this photo's aesthetic quality on a scale of 1.0 (horrible) to 10.0 (outstanding / perfect). "
        "Consider composition, lighting, focus, and subject.\n\n"
        "Use this calibration scale:\n"
        "- 1.0 - 3.0: Very poor (blurry, out of focus, extremely poor lighting/exposure, or no clear subject).\n"
        "- 4.0 - 6.0: Average (acceptable quality but has noticeable flaws in composition, lighting, or sharpness).\n"
        "- 7.0 - 8.0: Good (sharp, well-composed, appealing lighting/subject).\n"
        "- 9.0 - 10.0: Outstanding (perfect exposure/lighting, creative composition, "
        "excellent sharpness, professional grade).\n\n"
        "Start your response in this exact format:\n"
        "[SCORE: X.Y] [ANALYSIS: tag]\n"
        "where tag is exactly a 1 or 2 word description of the main reason "
        "(e.g. 'Blurry', 'Good composition', 'Under-exposed', 'Great lighting', 'Soft focus', 'Well-composed'). "
        "Follow it with a short sentence explaining your reasoning."
    )
    old_config_3 = {
        "ollama_url": "http://localhost:11434",
        "ollama_model": "llava",
        "ollama_prompt": old_prompt_3,
    }
    with open(ot.CONFIG_FILE, "w", encoding="utf-8") as f:
        json.dump(old_config_3, f, indent=4)

    loaded_3 = load_config()
    assert loaded_3["ollama_prompt"] == DEFAULT_CONFIG["ollama_prompt"]

    # Verify that it is also written back to disk
    with open(ot.CONFIG_FILE, "r", encoding="utf-8") as f:
        on_disk = json.load(f)
    assert on_disk["ollama_prompt"] == DEFAULT_CONFIG["ollama_prompt"]


@patch("urllib.request.urlopen")
def test_ollama_tool_fallback_analysis(mock_urlopen, dummy_image_file, temp_config_dir):
    # Mock response with SCORE but no ANALYSIS tag
    mock_resp_payload = {"response": "The aesthetic quality score of this photo is 8.5."}

    mock_response = MagicMock()
    mock_response.read.return_value = json.dumps(mock_resp_payload).encode("utf-8")
    mock_response.__enter__.return_value = mock_response
    mock_urlopen.return_value = mock_response

    tool = OllamaAestheticTool()
    score, tag = tool.analyze(dummy_image_file)

    assert score == 8.5
    assert tag == "N/A"

