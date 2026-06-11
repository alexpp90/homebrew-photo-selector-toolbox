import base64
import json
import logging
import re
import urllib.request
import urllib.error
import io
import threading
from pathlib import Path
from typing import Any, Dict
from PIL import Image

from photo_selector_toolbox.tools import AnalysisTool, ToolRegistry
from photo_selector_toolbox.utils import load_image_preview

logger = logging.getLogger(__name__)

CONFIG_DIR = Path.home() / ".photo_selector_toolbox"
CONFIG_FILE = CONFIG_DIR / "settings.json"

DEFAULT_CONFIG = {
    "ollama_url": "http://localhost:11434",
    "ollama_model": "llava",
    "ollama_prompt": (
        "Rate this photo's aesthetic quality on a scale of 1.0 (horrible) to 10.0 (outstanding / perfect). "
        "Consider composition, lighting, focus, and subject. "
        "Start your response with the score in the format [SCORE: X.Y], followed by a short sentence of reasoning."
    ),
}


def load_config() -> Dict[str, str]:
    """Loads settings.json config file, creating it with defaults if it doesn't exist."""
    try:
        if not CONFIG_DIR.exists():
            CONFIG_DIR.mkdir(parents=True, exist_ok=True)

        if not CONFIG_FILE.exists():
            with open(CONFIG_FILE, "w", encoding="utf-8") as f:
                json.dump(DEFAULT_CONFIG, f, indent=4)
            return DEFAULT_CONFIG.copy()

        with open(CONFIG_FILE, "r", encoding="utf-8") as f:
            user_config = json.load(f)
            # Ensure all keys exist
            config = DEFAULT_CONFIG.copy()
            config.update(user_config)

            # Auto-migrate prompt if it uses the old default format
            old_prompt = (
                "Rate this photo's aesthetic quality on a scale of 1.0 (horrible) to 10.0 (outstanding / perfect). "
                "Consider composition, lighting, focus, and subject. "
                "Provide your reasoning in one short sentence, then end your response with the score in the format [SCORE: X.Y]."
            )
            if config.get("ollama_prompt") == old_prompt:
                config["ollama_prompt"] = DEFAULT_CONFIG["ollama_prompt"]
                save_config(config)

            return config
    except Exception as e:
        logger.warning(f"Failed to load or write config file: {e}")
        return DEFAULT_CONFIG.copy()


def save_config(config: Dict[str, str]) -> None:
    """Saves config dict to settings.json."""
    try:
        if not CONFIG_DIR.exists():
            CONFIG_DIR.mkdir(parents=True, exist_ok=True)
        with open(CONFIG_FILE, "w", encoding="utf-8") as f:
            json.dump(config, f, indent=4)
    except Exception as e:
        logger.error(f"Failed to save config: {e}")


@ToolRegistry.register
class OllamaAestheticTool(AnalysisTool):
    """
    Optional analysis tool that queries a local Ollama VLM (e.g., LLaVA)
    to estimate the aesthetic score of an image.
    """

    name = "aesthetic"
    display_name = "AI Aesthetic Evaluation"

    # Class-level lock to serialize local Ollama inference requests
    _lock = threading.Lock()

    def analyze(self, filepath: Path, **kwargs: Any) -> float:
        config = load_config()
        ollama_url = config.get("ollama_url", DEFAULT_CONFIG["ollama_url"])
        model_name = config.get("ollama_model", DEFAULT_CONFIG["ollama_model"])
        prompt = config.get("ollama_prompt", DEFAULT_CONFIG["ollama_prompt"])

        # 1. Resize and convert image to base64 JPEG
        try:
            rgb_img = load_image_preview(filepath, max_size=(400, 400))
            if rgb_img is None:
                raise RuntimeError("load_image_preview returned None")
                
            buffer = io.BytesIO()
            rgb_img.save(buffer, format="JPEG", quality=85)
            img_bytes = buffer.getvalue()
            img_b64 = base64.b64encode(img_bytes).decode("utf-8")
        except Exception as e:
            raise RuntimeError(f"Failed to process image bytes: {e}")

        # 2. Query Ollama REST API
        url = f"{ollama_url.rstrip('/')}/api/generate"
        payload = {
            "model": model_name,
            "prompt": prompt,
            "images": [img_b64],
            "stream": False,
        }
        
        try:
            req = urllib.request.Request(
                url,
                data=json.dumps(payload).encode("utf-8"),
                headers={"Content-Type": "application/json"},
                method="POST",
            )
            # Serialize requests to avoid overloading local Ollama server
            with self._lock:
                with urllib.request.urlopen(req, timeout=60) as response:
                    res_data = json.loads(response.read().decode("utf-8"))
                    response_text = res_data.get("response", "")
        except urllib.error.URLError as e:
            raise RuntimeError(
                f"Ollama server connection error at {ollama_url}. Is Ollama running? ({e})"
            )
        except Exception as e:
            raise RuntimeError(f"Ollama API request failed: {e}")

        # 3. Parse score from output
        match = re.search(r"\[SCORE:\s*(\d+(?:\.\d+)?)\]", response_text, re.IGNORECASE)
        if match:
            score = float(match.group(1))
        else:
            # Fallback to parsing first numeric score found
            matches = re.findall(r"\d+(?:\.\d+)?", response_text)
            if not matches:
                raise RuntimeError(f"Could not parse a numeric score from Ollama output: '{response_text}'")
            score = float(matches[0])

        return max(1.0, min(10.0, score))

