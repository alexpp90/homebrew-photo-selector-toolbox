"""
Aesthetic scoring for the desktop app.

Historically the aesthetic score came exclusively from a local Ollama vision
language model (LLaVA). That approach is heavy (multi-GB model, seconds per
image, high battery/CPU cost) and non-deterministic — a very large generative
model used to produce a single scalar.

This module introduces a lightweight, purpose-built scoring stack and keeps
Ollama available as an optional *advanced* engine:

* ``apple_vision`` — Apple's on-device Vision aesthetics request
  (``VNCalculateImageAestheticsScoresRequest``, macOS 15+). Neural-Engine
  accelerated, ~milliseconds per image, no model to ship. Primary on modern
  macOS.
* ``nima_onnx`` — a small NIMA (MobileNet) regressor run via ONNX Runtime.
  Deterministic, dependency-light, portable to older macOS / Linux / Windows.
* ``ollama`` — the original LLaVA VLM, retained as an opt-in "deep critique"
  engine for users who already run Ollama.

The engine is chosen by the ``aesthetic_engine`` config key. In ``auto`` mode
the best available engine is selected at runtime, and the stack degrades
gracefully to Ollama so existing setups keep working until the new engines are
provisioned.

Native/model-backed paths (PyObjC Vision bridge, ONNX Runtime, model files)
cannot be exercised in a headless CI sandbox — they are guarded behind
availability checks and unit-tested at the pure-logic level (score mapping,
distribution→score, engine selection). Verify the live engines on a Mac /
target machine.
"""

import logging
import platform
from pathlib import Path
from typing import Any, Dict, Optional, Sequence, Tuple

from photo_selector_toolbox.tools import AnalysisTool, ToolRegistry
from photo_selector_toolbox.config import DEFAULT_CONFIG, load_config

logger = logging.getLogger(__name__)

# Engine identifiers (also the accepted values of the ``aesthetic_engine`` key).
ENGINE_AUTO = "auto"
ENGINE_APPLE_VISION = "apple_vision"
ENGINE_NIMA_ONNX = "nima_onnx"
ENGINE_OLLAMA = "ollama"

VALID_ENGINES = (ENGINE_AUTO, ENGINE_APPLE_VISION, ENGINE_NIMA_ONNX, ENGINE_OLLAMA)


# --------------------------------------------------------------------------- #
# Pure helpers (unit-tested without any native dependency)
# --------------------------------------------------------------------------- #

def _clamp10(value: float) -> float:
    """Clamp a score into the app's canonical 1.0-10.0 range."""
    return max(1.0, min(10.0, value))


def map_apple_score_to_10(overall_score: float) -> float:
    """Map Apple Vision's ``overallScore`` onto the app's 1.0-10.0 scale.

    Apple's ``overallScore`` is a float in roughly ``[-1.0, 1.0]`` where higher
    is more aesthetically pleasing. We linearly rescale that to ``[1.0, 10.0]``.

    TODO(device-calibration): confirm the real observed range on macOS 15+ and
    tighten the mapping if Apple's distribution is skewed.
    """
    normalized = (float(overall_score) + 1.0) / 2.0  # [-1, 1] -> [0, 1]
    return round(_clamp10(1.0 + normalized * 9.0), 1)


def nima_distribution_to_score(probs: Sequence[float]) -> float:
    """Convert a NIMA rating distribution to a single aesthetic score.

    NIMA outputs a probability distribution over the discrete ratings 1..10.
    The score is the expected value (mean) of that distribution, matching the
    original NIMA paper.
    """
    if probs is None or len(probs) == 0:
        raise ValueError("empty NIMA distribution")
    total = float(sum(probs))
    if total <= 0:
        raise ValueError("invalid NIMA distribution (non-positive sum)")
    mean = sum((i + 1) * float(p) for i, p in enumerate(probs)) / total
    return round(_clamp10(mean), 1)


# --------------------------------------------------------------------------- #
# Availability probes
# --------------------------------------------------------------------------- #

def _macos_version_tuple() -> Optional[Tuple[int, ...]]:
    if platform.system() != "Darwin":
        return None
    try:
        parts = platform.mac_ver()[0].split(".")
        return tuple(int(p) for p in parts if p != "")
    except Exception:
        return None


def apple_vision_available() -> bool:
    """True when the Apple Vision aesthetics request can be used (macOS 15+ with
    the PyObjC Vision bridge importable)."""
    ver = _macos_version_tuple()
    if ver is None or ver[0] < 15:
        return False
    try:
        import Vision  # noqa: F401  (PyObjC framework)
        return True
    except Exception:
        return False


def onnxruntime_available() -> bool:
    try:
        import onnxruntime  # noqa: F401
        return True
    except Exception:
        return False


def _nima_model_path(config: Dict[str, Any]) -> str:
    return str(config.get("nima_model_path") or DEFAULT_CONFIG.get("nima_model_path", "") or "")


def select_engine(
    config: Dict[str, Any],
    *,
    apple_ok: Optional[bool] = None,
    onnx_ok: Optional[bool] = None,
    nima_model_exists: Optional[bool] = None,
) -> str:
    """Resolve which engine to use.

    An explicit ``aesthetic_engine`` other than ``auto`` is honoured verbatim.
    In ``auto`` mode we prefer Apple Vision, then a NIMA ONNX model (only if a
    model file is present), and finally fall back to Ollama so existing
    installs keep working.

    The availability flags are injectable purely so this decision can be
    unit-tested without native dependencies.
    """
    engine = str(config.get("aesthetic_engine", ENGINE_AUTO) or ENGINE_AUTO)
    if engine not in VALID_ENGINES:
        logger.warning("Unknown aesthetic_engine '%s'; falling back to auto.", engine)
        engine = ENGINE_AUTO
    if engine != ENGINE_AUTO:
        return engine

    if apple_ok is None:
        apple_ok = apple_vision_available()
    if onnx_ok is None:
        onnx_ok = onnxruntime_available()
    if nima_model_exists is None:
        mp = _nima_model_path(config)
        nima_model_exists = bool(mp) and Path(mp).is_file()

    if apple_ok:
        return ENGINE_APPLE_VISION
    if onnx_ok and nima_model_exists:
        return ENGINE_NIMA_ONNX
    return ENGINE_OLLAMA


# --------------------------------------------------------------------------- #
# Engines
# --------------------------------------------------------------------------- #

class AppleVisionAestheticEngine:
    """On-device aesthetics via Apple's Vision framework (macOS 15+).

    Requires the PyObjC ``Vision`` framework. Returns ``(score, tag)`` where the
    tag flags "utility" images (screenshots, receipts, documents) which are not
    memorable photos even if technically clean.
    """

    def analyze(self, filepath: Path, **kwargs: Any) -> Tuple[float, str]:
        try:
            import Vision
            from Foundation import NSURL
        except Exception as e:  # pragma: no cover - requires macOS + PyObjC
            raise RuntimeError(
                "Apple Vision engine unavailable (needs macOS 15+ and "
                f"pyobjc-framework-Vision): {e}"
            )

        url = NSURL.fileURLWithPath_(str(filepath))
        handler = Vision.VNImageRequestHandler.alloc().initWithURL_options_(url, {})
        request = Vision.VNCalculateImageAestheticsScoresRequest.alloc().init()

        success, error = handler.performRequests_error_([request], None)
        if not success:
            raise RuntimeError(f"Vision aesthetics request failed: {error}")

        results = request.results()
        if not results:
            raise RuntimeError("Vision returned no aesthetics observation")

        obs = results[0]
        overall = float(obs.overallScore())
        try:
            is_utility = bool(obs.isUtility())
        except Exception:
            is_utility = False

        score = map_apple_score_to_10(overall)
        tag = "Utility/Screenshot" if is_utility else "N/A"
        return score, tag


class NimaOnnxAestheticEngine:
    """Small NIMA (MobileNet) aesthetic regressor via ONNX Runtime.

    The model is not bundled; provide a path via the ``nima_model_path`` setting
    (a NIMA model exported to ONNX that outputs a 10-way rating distribution).
    """

    _session = None
    _session_path: Optional[str] = None

    def _get_session(self, model_path: str):  # pragma: no cover - needs onnxruntime
        import onnxruntime

        if self._session is None or self._session_path != model_path:
            self._session = onnxruntime.InferenceSession(
                model_path, providers=onnxruntime.get_available_providers()
            )
            self._session_path = model_path
        return self._session

    def analyze(self, filepath: Path, **kwargs: Any) -> Tuple[float, str]:
        config = load_config()
        model_path = _nima_model_path(config)
        if not model_path or not Path(model_path).is_file():
            raise RuntimeError(
                "NIMA ONNX model not found. Set 'nima_model_path' in settings.json "
                "to a NIMA model exported to ONNX."
            )
        try:  # pragma: no cover - needs numpy + onnxruntime + a model
            import numpy as np
        except Exception as e:
            raise RuntimeError(f"numpy is required for the NIMA engine: {e}")

        from photo_selector_toolbox.utils import load_image_preview

        img = load_image_preview(filepath, max_size=(224, 224))
        if img is None:
            raise RuntimeError("failed to load image for NIMA scoring")

        img = img.convert("RGB").resize((224, 224))
        arr = np.asarray(img).astype("float32") / 255.0
        # ImageNet normalisation (typical for MobileNet-based NIMA exports).
        mean = np.array([0.485, 0.456, 0.406], dtype="float32")
        std = np.array([0.229, 0.224, 0.225], dtype="float32")
        arr = (arr - mean) / std
        # NCHW batch of 1. TODO(device): some NIMA exports expect NHWC — adjust
        # to match the specific model you provide.
        arr = np.transpose(arr, (2, 0, 1))[None, ...]

        session = self._get_session(model_path)
        input_name = session.get_inputs()[0].name
        outputs = session.run(None, {input_name: arr})
        probs = np.asarray(outputs[0]).reshape(-1).tolist()
        score = nima_distribution_to_score(probs)
        return score, "N/A"


# --------------------------------------------------------------------------- #
# Dispatcher tool (the single registered "aesthetic" tool)
# --------------------------------------------------------------------------- #

@ToolRegistry.register
class AestheticTool(AnalysisTool):
    """Aesthetic scoring tool that delegates to the configured engine."""

    name = "aesthetic"
    display_name = "AI Aesthetic Evaluation"

    def analyze(self, filepath: Path, **kwargs: Any) -> Tuple[float, str]:
        config = load_config()
        engine = select_engine(config)
        logger.info("Aesthetic scoring engine: %s", engine)

        if engine == ENGINE_APPLE_VISION:
            return AppleVisionAestheticEngine().analyze(filepath, **kwargs)
        if engine == ENGINE_NIMA_ONNX:
            return NimaOnnxAestheticEngine().analyze(filepath, **kwargs)

        # Default / advanced fallback: the original Ollama VLM.
        from photo_selector_toolbox.ollama_tool import OllamaAestheticTool

        return OllamaAestheticTool().analyze(filepath, **kwargs)
