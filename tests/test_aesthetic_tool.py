"""Unit tests for the pure logic of the aesthetic scoring dispatcher.

Native/model-backed engines (Apple Vision, ONNX) are not exercised here — they
require macOS/PyObjC or an ONNX model and are verified on-device. These tests
cover the deterministic pieces: score mapping, distribution reduction, and
engine selection.
"""

import pytest

from photo_selector_toolbox.aesthetic_tool import (
    ENGINE_APPLE_VISION,
    ENGINE_NIMA_ONNX,
    ENGINE_OLLAMA,
    map_apple_score_to_10,
    nima_distribution_to_score,
    select_engine,
)


def test_map_apple_score_endpoints_and_midpoint():
    # [-1, 1] -> [1, 10]
    assert map_apple_score_to_10(-1.0) == 1.0
    assert map_apple_score_to_10(1.0) == 10.0
    assert map_apple_score_to_10(0.0) == 5.5


def test_map_apple_score_is_clamped():
    assert map_apple_score_to_10(-5.0) == 1.0
    assert map_apple_score_to_10(5.0) == 10.0


def test_nima_distribution_expected_value():
    # All mass on rating 10 -> score 10; uniform -> 5.5.
    peaked = [0.0] * 9 + [1.0]
    assert nima_distribution_to_score(peaked) == 10.0
    uniform = [0.1] * 10
    assert nima_distribution_to_score(uniform) == 5.5


def test_nima_distribution_normalises_unnormalised_input():
    # Weights need not sum to 1; the mean is still well defined.
    assert nima_distribution_to_score([0, 0, 0, 0, 0, 0, 0, 0, 0, 2]) == 10.0


def test_nima_distribution_rejects_bad_input():
    with pytest.raises(ValueError):
        nima_distribution_to_score([])
    with pytest.raises(ValueError):
        nima_distribution_to_score([0.0, 0.0])


def test_select_engine_honours_explicit_choice():
    assert select_engine({"aesthetic_engine": "ollama"}) == ENGINE_OLLAMA
    assert select_engine({"aesthetic_engine": "apple_vision"}) == ENGINE_APPLE_VISION
    assert select_engine({"aesthetic_engine": "nima_onnx"}) == ENGINE_NIMA_ONNX


def test_select_engine_auto_prefers_apple_vision():
    engine = select_engine(
        {"aesthetic_engine": "auto"},
        apple_ok=True,
        onnx_ok=True,
        nima_model_exists=True,
    )
    assert engine == ENGINE_APPLE_VISION


def test_select_engine_auto_uses_nima_when_no_apple_and_model_present():
    engine = select_engine(
        {"aesthetic_engine": "auto"},
        apple_ok=False,
        onnx_ok=True,
        nima_model_exists=True,
    )
    assert engine == ENGINE_NIMA_ONNX


def test_select_engine_auto_falls_back_to_ollama():
    # No Apple Vision, and no usable NIMA model -> stay on Ollama.
    engine = select_engine(
        {"aesthetic_engine": "auto"},
        apple_ok=False,
        onnx_ok=True,
        nima_model_exists=False,
    )
    assert engine == ENGINE_OLLAMA


def test_select_engine_unknown_value_falls_back_to_auto_resolution():
    engine = select_engine(
        {"aesthetic_engine": "bogus"},
        apple_ok=False,
        onnx_ok=False,
        nima_model_exists=False,
    )
    assert engine == ENGINE_OLLAMA
