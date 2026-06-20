"""
Centralized configuration management for Photo Selector Toolbox.

Handles loading, saving, and migrating settings.json, plus recent folder tracking.
"""

import json
import logging
import os
import stat
from pathlib import Path
from typing import Dict, List, Optional, Union

logger = logging.getLogger(__name__)

CONFIG_DIR = Path.home() / ".photo_selector_toolbox"
CONFIG_FILE = CONFIG_DIR / "settings.json"

DEFAULT_CONFIG: Dict[str, Union[str, bool, List[str]]] = {
    "ollama_url": "http://localhost:11434",
    "ollama_model": "llava",
    "ollama_prompt": (
        "You are an expert professional photographer and art critic. "
        "Critically analyze this image and score its aesthetic quality.\n\n"
        "Follow these analysis steps:\n"
        "1. Subject & Intent: Identify the subject. Is this an empty, blank, uniform, or accidental shot "
        "(e.g., plain grey sky, solid wall, ground)?\n"
        "2. Technical Quality: Assess focus accuracy, sharpness, exposure, dynamic range, and digital noise.\n"
        "3. Composition & Lighting: Assess framing, rule of thirds, leading lines, "
        "light direction/quality, and background distractions.\n\n"
        "Use this calibration scale:\n"
        "- 1.0 - 2.5: Extremely Poor. Accidental clicks, empty/blank/uniform contents "
        "(like a grey sky or lens cap), or severe technical failures (heavy blur, missed focus, bad exposure).\n"
        "- 3.0 - 5.5: Average. Typical casual snapshots. Technically acceptable but "
        "boring composition, flat lighting, or cluttered backgrounds.\n"
        "- 6.0 - 8.0: Good. Sharp focus on a clear subject, good composition, and appealing lighting.\n"
        "- 8.5 - 10.0: Outstanding. Professional-grade quality, creative composition, "
        "dramatic/storytelling lighting, and high emotional or visual impact.\n\n"
        "CRITICAL RULE: Any image that is completely uniform or blank (e.g., a plain grey sky, "
        "a solid floor, a blank wall) lacks composition and a subject. "
        "You MUST rate these as extremely poor (1.0 to 1.5) regardless of how clean the pixels are.\n\n"
        "Provide your step-by-step reasoning first, and conclude your response in this exact format on a new line:\n"
        "[SCORE: X.Y] [ANALYSIS: tag]\n"
        "where tag is exactly a 1 or 2 word description of the main reason "
        "(e.g., 'Empty Sky', 'Blurry', 'Soft Focus', 'Under-exposed', 'Great Lighting', 'Good Composition')."
    ),
    "selection_folder": "Selection",
    "separate_raw_jpeg": True,
    "recent_folders": [],
}

# Maximum number of recent folders to remember
MAX_RECENT_FOLDERS = 10

# Old prompts that should be auto-migrated to the current default
_OLD_PROMPTS = [
    (
        "Rate this photo's aesthetic quality on a scale of 1.0 (horrible) to 10.0 (outstanding / perfect). "
        "Consider composition, lighting, focus, and subject. "
        "Provide your reasoning in one short sentence, then end your response "
        "with the score in the format [SCORE: X.Y]."
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
        "- 9.0 - 10.0: Outstanding (perfect exposure/lighting, creative composition, "
        "excellent sharpness, professional grade).\n\n"
        "Start your response in this exact format:\n"
        "[SCORE: X.Y] [ANALYSIS: tag]\n"
        "where tag is exactly a 1 or 2 word description of the main reason "
        "(e.g. 'Blurry', 'Good composition', 'Under-exposed', 'Great lighting', 'Soft focus', 'Well-composed'). "
        "Follow it with a short sentence explaining your reasoning."
    ),
]


def _set_secure_permissions(path: Path) -> None:
    """Set file permissions to owner read/write only (600)."""
    try:
        os.chmod(path, stat.S_IRUSR | stat.S_IWUSR)
    except OSError:
        pass  # Best-effort on platforms that don't support chmod


def load_config() -> Dict[str, Union[str, bool, List[str]]]:
    """Loads settings.json config file, creating it with defaults if it doesn't exist."""
    import sys
    if "pytest" in sys.modules and CONFIG_DIR == Path.home() / ".photo_selector_toolbox":
        return DEFAULT_CONFIG.copy()
    try:
        if not CONFIG_DIR.exists():
            CONFIG_DIR.mkdir(parents=True, exist_ok=True)

        if not CONFIG_FILE.exists():
            with open(CONFIG_FILE, "w", encoding="utf-8") as f:
                json.dump(DEFAULT_CONFIG, f, indent=4)
            _set_secure_permissions(CONFIG_FILE)
            return DEFAULT_CONFIG.copy()

        with open(CONFIG_FILE, "r", encoding="utf-8") as f:
            user_config = json.load(f)
            # Ensure all keys exist
            config = DEFAULT_CONFIG.copy()
            config.update(user_config)

            # Auto-migrate prompt if it uses any of the old default formats
            if config.get("ollama_prompt") in _OLD_PROMPTS:
                config["ollama_prompt"] = DEFAULT_CONFIG["ollama_prompt"]
                save_config(config)

            return config
    except Exception as e:
        logger.warning(f"Failed to load or write config file: {e}")
        return DEFAULT_CONFIG.copy()


def save_config(config: Dict[str, Union[str, bool, List[str]]]) -> None:
    """Saves config dict to settings.json."""
    try:
        if not CONFIG_DIR.exists():
            CONFIG_DIR.mkdir(parents=True, exist_ok=True)
        with open(CONFIG_FILE, "w", encoding="utf-8") as f:
            json.dump(config, f, indent=4)
        _set_secure_permissions(CONFIG_FILE)
    except Exception as e:
        logger.error(f"Failed to save config: {e}")


def add_recent_folder(folder_path: str) -> None:
    """Adds a folder to the recent folders list (most recent first, max 10)."""
    config = load_config()
    recent = list(config.get("recent_folders", []))

    # Remove if already present, then prepend
    folder_str = str(folder_path)
    recent = [f for f in recent if f != folder_str]
    recent.insert(0, folder_str)
    recent = recent[:MAX_RECENT_FOLDERS]

    config["recent_folders"] = recent
    save_config(config)


def get_recent_folders() -> List[str]:
    """Returns the list of recently opened folders."""
    config = load_config()
    return list(config.get("recent_folders", []))


def is_ollama_url_external(url: Optional[str] = None) -> bool:
    """Check if the Ollama URL points to a non-localhost address."""
    if url is None:
        config = load_config()
        url = config.get("ollama_url", DEFAULT_CONFIG["ollama_url"])
    try:
        from urllib.parse import urlparse
        parsed = urlparse(url)
        hostname = parsed.hostname or ""
        return hostname not in ("localhost", "127.0.0.1", "::1", "0.0.0.0")
    except Exception:
        return True
