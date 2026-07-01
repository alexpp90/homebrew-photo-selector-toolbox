import base64
import json
import logging
import re
import urllib.request
from urllib.error import URLError
import io
import threading
from pathlib import Path
from typing import Any, Tuple


from photo_selector_toolbox.tools import AnalysisTool, ToolRegistry
from photo_selector_toolbox.utils import load_image_preview

# Re-export for backward compatibility — existing code imports from here
from photo_selector_toolbox.config import (  # noqa: F401
    CONFIG_DIR,
    CONFIG_FILE,
    DEFAULT_CONFIG,
    load_config,
    save_config,
)

logger = logging.getLogger(__name__)


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
        from photo_selector_toolbox.config import is_ollama_url_external

        config = load_config()
        ollama_url = config.get("ollama_url", DEFAULT_CONFIG["ollama_url"])
        model_name = config.get("ollama_model", DEFAULT_CONFIG["ollama_model"])
        prompt = config.get("ollama_prompt", DEFAULT_CONFIG["ollama_prompt"])

        # Warn if sending images to a non-localhost server
        if is_ollama_url_external(ollama_url):
            logger.warning(
                f"Ollama URL '{ollama_url}' is not localhost. "
                "Images will be sent over the network. "
                "Set ollama_url to http://localhost:11434 to keep photos on-device."
            )

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
        if not ollama_url.lower().startswith(('http://', 'https://')):
            raise RuntimeError("Ollama URL must start with http:// or https://")

        from urllib.parse import urlparse
        import socket
        import ipaddress
        hostname = urlparse(ollama_url).hostname or ""

        try:
            ip = socket.gethostbyname(hostname)
            if ipaddress.ip_address(ip).is_link_local:
                raise RuntimeError("SSRF Protection: Cloud metadata IPs are not allowed.")
        except socket.gaierror:
            try:
                if ipaddress.ip_address(hostname).is_link_local:
                    raise RuntimeError("SSRF Protection: Cloud metadata IPs are not allowed.")
            except ValueError:
                pass

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
        except URLError as e:
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

