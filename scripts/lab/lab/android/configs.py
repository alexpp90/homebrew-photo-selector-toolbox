"""Android configuration matrix applier and resetter."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any

import yaml

from .adb import AdbClient


CONFIGS_PATH = Path(__file__).resolve().parent.parent.parent / "configs" / "android.yaml"


class ConfigNotRunError(RuntimeError):
    """Raised when a configuration cannot run on the attached target (e.g. hardware keyboard AVD required)."""


@dataclass
class SavedDeviceState:
    """Recorded initial device state to restore in `reset_config`."""

    user_rotation: int = 0
    accelerometer_rotation: int = 0


def load_android_configs(path: Path | None = None) -> dict[str, dict[str, Any]]:
    """Load `scripts/lab/configs/android.yaml`."""
    target = path or CONFIGS_PATH
    return yaml.safe_load(target.read_text(encoding="utf-8")) or {}


def record_device_state(adb: AdbClient) -> SavedDeviceState:
    """Read current rotation settings so `reset_config` can restore them."""
    rot_out = adb.run("shell", "settings", "get", "system", "user_rotation").strip()
    acc_out = adb.run("shell", "settings", "get", "system", "accelerometer_rotation").strip()
    try:
        user_rot = int(rot_out)
    except ValueError:
        user_rot = 0
    try:
        acc_rot = int(acc_out)
    except ValueError:
        acc_rot = 0
    return SavedDeviceState(user_rotation=user_rot, accelerometer_rotation=acc_rot)


def set_verified_rotation(adb: AdbClient, orientation: str) -> None:
    """Set `portrait` or `landscape` and verify via `window_dp()`, flipping 0<->1 if needed."""
    adb.run("shell", "settings", "put", "system", "accelerometer_rotation", "0")
    candidates = [1, 0] if orientation == "landscape" else [0, 1]
    for rot_val in candidates:
        adb.run("shell", "settings", "put", "system", "user_rotation", str(rot_val))
        w, h = adb.window_dp()
        if orientation == "landscape" and w > h:
            return
        if orientation == "portrait" and h >= w:
            return
    raise RuntimeError(f"Could not achieve verified {orientation} rotation on {adb.serial}")


def apply_config(
    adb: AdbClient,
    config_name: str,
    configs: dict[str, dict[str, Any]] | None = None,
    *,
    is_avd_kbd: bool = False,
) -> None:
    """Apply `config_name` to `adb`, verifying rotation and checking keyboard requirements."""
    all_cfgs = configs or load_android_configs()
    if config_name not in all_cfgs:
        raise ValueError(f"Unknown Android config {config_name!r}")
    cfg = all_cfgs[config_name]

    if cfg.get("keyboard") and not is_avd_kbd:
        raise ConfigNotRunError("needs AVD with hw.keyboard=yes")

    if "size" in cfg:
        adb.run("shell", "wm", "size", str(cfg["size"]))
    else:
        adb.run("shell", "wm", "size", "reset")

    if "density" in cfg:
        adb.run("shell", "wm", "density", str(int(cfg["density"])))
    elif "density_factor" in cfg:
        phys = adb.physical_density()
        scaled = int(round(phys * float(cfg["density_factor"])))
        adb.run("shell", "wm", "density", str(scaled))
    else:
        adb.run("shell", "wm", "density", "reset")

    font_scale = float(cfg.get("font_scale", 1.0))
    adb.run("shell", "settings", "put", "system", "font_scale", f"{font_scale}")

    night_mode = "yes" if cfg.get("night") else "no"
    adb.run("shell", "cmd", "uimode", "night", night_mode)

    if "rotation" in cfg:
        set_verified_rotation(adb, str(cfg["rotation"]))


def reset_config(adb: AdbClient, saved: SavedDeviceState | None = None) -> None:
    """Reset display size, density, font scale, night mode, and rotation."""
    adb.run("shell", "wm", "size", "reset")
    adb.run("shell", "wm", "density", "reset")
    adb.run("shell", "settings", "put", "system", "font_scale", "1.0")
    adb.run("shell", "cmd", "uimode", "night", "no")
    if saved is not None:
        adb.run(
            "shell",
            "settings",
            "put",
            "system",
            "user_rotation",
            str(saved.user_rotation),
        )
        adb.run(
            "shell",
            "settings",
            "put",
            "system",
            "accelerometer_rotation",
            str(saved.accelerometer_rotation),
        )
