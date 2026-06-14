import base64
import json
import logging
import re
import urllib.request
import urllib.error
import io
import threading
from pathlib import Path
from typing import Any, Dict, Tuple, Union
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
        "You are an expert professional photographer and art critic. Critically analyze this image and score its aesthetic quality.\n\n"
        "Follow these analysis steps:\n"
        "1. Subject & Intent: Identify the subject. Is this an empty, blank, uniform, or accidental shot (e.g., plain grey sky, solid wall, ground)?\n"
        "2. Technical Quality: Assess focus accuracy, sharpness, exposure, dynamic range, and digital noise.\n"
        "3. Composition & Lighting: Assess framing, rule of thirds, leading lines, light direction/quality, and background distractions.\n\n"
        "Use this calibration scale:\n"
        "- 1.0 - 2.5: Extremely Poor. Accidental clicks, empty/blank/uniform contents (like a grey sky or lens cap), or severe technical failures (heavy blur, missed focus, bad exposure).\n"
        "- 3.0 - 5.5: Average. Typical casual snapshots. Technically acceptable but boring composition, flat lighting, or cluttered backgrounds.\n"
        "- 6.0 - 8.0: Good. Sharp focus on a clear subject, good composition, and appealing lighting.\n"
        "- 8.5 - 10.0: Outstanding. Professional-grade quality, creative composition, dramatic/storytelling lighting, and high emotional or visual impact.\n\n"
        "CRITICAL RULE: Any image that is completely uniform or blank (e.g., a plain grey sky, a solid floor, a blank wall) lacks composition and a subject. You MUST rate these as extremely poor (1.0 to 1.5) regardless of how clean the pixels are.\n\n"
        "Provide your step-by-step reasoning first, and conclude your response in this exact format on a new line:\n"
        "[SCORE: X.Y] [ANALYSIS: tag]\n"
        "where tag is exactly a 1 or 2 word description of the main reason (e.g., 'Empty Sky', 'Blurry', 'Soft Focus', 'Under-exposed', 'Great Lighting', 'Good Composition')."
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

            # Auto-migrate prompt if it uses any of the old default formats
            old_prompts = [
                (
                    "Rate this photo's aesthetic quality on a scale of 1.0 (horrible) to 10.0 (outstanding / perfect). "
                    "Consider composition, lighting, focus, and subject. "
                    "Provide your reasoning in one short sentence, then end your response with the score in the format [SCORE: X.Y]."
                ),
                (
                    "Rate this photo's aesthetic quality on a scale of 1.0 (horrible) to 10.0 (outstanding / perfect). "
                    "Consider composition, lighting, focus, and subject. "
                    "Start your response with the score in the format [SCORE: X.Y], followed by a short sentence of reasoning."
                ),
                (
                    "Rate this photo's aesthetic quality on a scale of 1.0 (horrible) to 10.0 (outstanding / perfect). "
                    "Consider composition, lighting, focus, and subject.\n\n"
                    "Use this calibration scale:\n"
                    "- 1.0 - 3.0: Very poor (blurry, out of focus, extremely poor lighting/exposure, or no clear subject).\n"
                    "- 4.0 - 6.0: Average (acceptable quality but has noticeable flaws in composition, lighting, or sharpness).\n"
                    "- 7.0 - 8.0: Good (sharp, well-composed, appealing lighting/subject).\n"
                    "- 9.0 - 10.0: Outstanding (perfect exposure/lighting, creative composition, excellent sharpness, professional grade).\n\n"
                    "Start your response in this exact format:\n"
                    "[SCORE: X.Y] [ANALYSIS: tag]\n"
                    "where tag is exactly a 1 or 2 word description of the main reason (e.g. 'Blurry', 'Good composition', 'Under-exposed', 'Great lighting', 'Soft focus', 'Well-composed'). "
                    "Follow it with a short sentence explaining your reasoning."
                )
            ]
            if config.get("ollama_prompt") in old_prompts:
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

    def analyze(self, filepath: Path, **kwargs: Any) -> Tuple[float, str]:
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

        # 3. Parse score and analysis tag from output
        match_score = re.search(r"\[SCORE:\s*(\d+(?:\.\d+)?)\]", response_text, re.IGNORECASE)
        if match_score:
            score = float(match_score.group(1))
        else:
            # Fallback to parsing first numeric score found
            matches = re.findall(r"\d+(?:\.\d+)?", response_text)
            if not matches:
                raise RuntimeError(f"Could not parse a numeric score from Ollama output: '{response_text}'")
            score = float(matches[0])

        score = max(1.0, min(10.0, score))

        match_analysis = re.search(r"\[ANALYSIS:\s*([^\]]+)\]", response_text, re.IGNORECASE)
        analysis_tag = "N/A"
        if match_analysis:
            analysis_tag = match_analysis.group(1).strip()
            if len(analysis_tag) > 30:
                analysis_tag = analysis_tag[:27] + "..."
        
        return score, analysis_tag

