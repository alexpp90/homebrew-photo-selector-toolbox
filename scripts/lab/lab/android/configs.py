"""Android configuration matrix applier and resetter."""

from __future__ import annotations

import re
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Callable

import yaml

from .adb import AdbClient


ROTATION_POLLS = 10
ROTATION_POLL_S = 0.5

CONFIGS_PATH = Path(__file__).resolve().parent.parent.parent / "configs" / "android.yaml"


class ConfigNotRunError(RuntimeError):
    """Raised when a configuration cannot run on the attached target (e.g. hardware keyboard AVD required)."""


@dataclass
class SavedDeviceState:
    """Recorded initial device state to restore in `reset_config`.

    Every setting `apply_config` or `AdbClient.ensure_ready` touches is recorded, so a run on a
    personal device leaves the user's display size, font size, dark mode and stay-awake setting
    as they were. `None` means "no override was set" and restores to the device default.
    """

    user_rotation: int = 0
    accelerometer_rotation: int = 0
    size_override: str | None = None
    density_override: int | None = None
    font_scale: str = "1.0"
    night_mode: str = "no"
    stay_on: str = "0"
    dex: bool = False


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
    size_out = adb.run("shell", "wm", "size")
    m_size = re.search(r"Override size:\s*(\d+x\d+)", size_out)
    dens_out = adb.run("shell", "wm", "density")
    m_dens = re.search(r"Override density:\s*(\d+)", dens_out)
    font = adb.run("shell", "settings", "get", "system", "font_scale").strip()
    night_out = adb.run("shell", "cmd", "uimode", "night").strip().lower()
    night = "yes" if "night mode: yes" in night_out else ("auto" if "night mode: auto" in night_out else "no")
    stay_on = adb.run("shell", "settings", "get", "global", "stay_on_while_plugged_in").strip()
    return SavedDeviceState(
        dex=dex_active(adb),
        user_rotation=user_rot,
        accelerometer_rotation=acc_rot,
        size_override=m_size.group(1) if m_size else None,
        density_override=int(m_dens.group(1)) if m_dens else None,
        font_scale=font if re.fullmatch(r"\d+(\.\d+)?", font) else "1.0",
        night_mode=night,
        stay_on=stay_on if stay_on.isdigit() else "0",
    )


# Samsung DeX on the tablet's own screen is a launcher Quick Settings tile (One UI 8).
DEX_TILE = "com.sec.android.app.launcher/com.honeyspace.dexservice.DesktopModeTile"
DEX_POLLS = 20
DEX_POLL_S = 1.0


def _maestro_dex_tile(serial: str | None) -> int:
    """Tap the DeX tile in Quick Settings with Maestro (`scripts/lab/flows/samsung_dex_tile.yaml`)."""
    import tempfile

    from .runner import default_maestro_runner

    flow = Path(__file__).resolve().parent.parent.parent / "flows" / "samsung_dex_tile.yaml"
    with tempfile.TemporaryDirectory(prefix="pstlab-dex-") as tmp:
        return default_maestro_runner(serial, flow, Path(tmp) / "dex.xml")


# Fallback used by `set_dex` when `click-tile` does not switch DeX; tests replace it.
DEX_TILE_TAPPER: Callable[[str | None], int] | None = _maestro_dex_tile


def dex_active(adb: AdbClient) -> bool:
    """True while Samsung DeX runs on the default display (its windowing mode turns freeform).

    Only Samsung devices count: ChromeOS ARC is always freeform and has no DeX.
    """
    if adb.run("shell", "getprop", "ro.product.manufacturer").strip().lower() not in ("samsung", ""):
        return False
    out = adb.run("shell", "dumpsys", "window", "displays")
    m = re.search(r"mWindowingMode=([a-z]+)", out)
    return bool(m and m.group(1) == "freeform")


def set_dex(adb: AdbClient, on: bool, tile_tapper: Callable[[str | None], int] | None = None) -> None:
    """Switch DeX on or off through its Quick Settings tile and verify the windowing mode.

    Raises `ConfigNotRunError` when the switch does not take effect (tile missing, device
    locked, a first-use confirmation dialog), so DeX configs are reported "not run" with the
    reason instead of running in the wrong mode.
    """
    if dex_active(adb) == on:
        return
    adb.run("shell", "input", "keyevent", "KEYCODE_WAKEUP")

    def wait_for(state: bool) -> bool:
        for _ in range(DEX_POLLS):
            if DEX_POLL_S > 0:
                time.sleep(DEX_POLL_S)
            if dex_active(adb) == state:
                # DeX restarts the launcher and re-lays out every task; let it settle.
                if DEX_POLL_S > 0:
                    time.sleep(3 * DEX_POLL_S)
                return True
        return False

    adb.run("shell", "cmd", "statusbar", "click-tile", DEX_TILE)
    if wait_for(on):
        return
    # One UI ignores click-tile for the DeX tile; tap it in Quick Settings instead.
    tapper = tile_tapper or DEX_TILE_TAPPER
    if tapper is not None and tapper(adb.serial) == 0 and wait_for(on):
        return
    state = "on" if on else "off"
    raise ConfigNotRunError(
        f"could not switch Samsung DeX {state} automatically; switch it {state} on the device "
        f"and re-run with --config <the DeX config>"
    )


def set_verified_rotation(adb: AdbClient, orientation: str) -> None:
    """Set `portrait` or `landscape` and verify via `window_dp()`, trying each rotation value.

    Raises `ConfigNotRunError` when the display never changes orientation at all — a docked
    Pixel Tablet ignores every rotation request — so the journey is reported "not run" with
    the reason instead of as an error.
    """
    adb.run("shell", "settings", "put", "system", "accelerometer_rotation", "0")
    candidates = [1, 3, 0, 2] if orientation == "landscape" else [0, 2, 1, 3]
    seen: set[tuple[int, int]] = set()
    for rot_val in candidates:
        adb.run("shell", "settings", "put", "system", "user_rotation", str(rot_val))
        # The window manager applies a rotation (and a preceding `wm size`) asynchronously;
        # an immediate readout still shows the old geometry, so poll before trying the next value.
        for _ in range(ROTATION_POLLS):
            w, h = adb.window_dp()
            seen.add((w, h))
            if orientation == "landscape" and w > h:
                return
            if orientation == "portrait" and h >= w:
                return
            if ROTATION_POLL_S > 0:
                time.sleep(ROTATION_POLL_S)
    if len(seen) == 1:
        w, h = seen.pop()
        raise ConfigNotRunError(f"display rotation is locked at {w}x{h} dp (docked?); cannot show {orientation}")
    raise RuntimeError(f"Could not achieve verified {orientation} rotation on {adb.serial}")


def natural_size(adb: AdbClient, size: str) -> str:
    """`size` (written landscape-first in the configs) in the device's natural orientation.

    `wm size` takes the panel's natural orientation; a portrait-natural tablet (Pixel Tablet,
    1600x2560) needs 1848x2960 to show the 2960x1848 reference geometry in landscape.
    """
    w, h = (int(x) for x in size.split("x"))
    out = adb.run("shell", "wm", "size")
    m = re.search(r"Physical size:\s*(\d+)x(\d+)", out)
    if m and int(m.group(1)) < int(m.group(2)) and w > h:
        w, h = h, w
    return f"{w}x{h}"


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

    if cfg.get("native_display"):
        # ChromeOS ARC: the app runs in a ChromeOS window on a virtual display; size, density
        # and rotation belong to ChromeOS (and resetting ARC's own density override would
        # rescale every app). Only text and theme settings apply.
        adb.run("shell", "settings", "put", "system", "font_scale", f"{float(cfg.get('font_scale', 1.0))}")
        adb.run("shell", "cmd", "uimode", "night", "yes" if cfg.get("night") else "no")
        return
    if cfg.get("dex"):
        # Real DeX on the device screen: DeX owns size, density and rotation; only text and
        # theme settings apply.
        set_dex(adb, True)
        adb.run("shell", "settings", "put", "system", "font_scale", f"{float(cfg.get('font_scale', 1.0))}")
        adb.run("shell", "cmd", "uimode", "night", "yes" if cfg.get("night") else "no")
        return
    if dex_active(adb):
        set_dex(adb, False)

    if "size" in cfg:
        adb.run("shell", "wm", "size", natural_size(adb, str(cfg["size"])))
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
    """Restore display size, density, font scale, night mode, stay-awake and rotation."""
    saved_size = saved.size_override if saved else None
    saved_dens = saved.density_override if saved else None
    adb.run("shell", "wm", "size", saved_size or "reset")
    adb.run("shell", "wm", "density", str(saved_dens) if saved_dens else "reset")
    adb.run("shell", "settings", "put", "system", "font_scale", saved.font_scale if saved else "1.0")
    adb.run("shell", "cmd", "uimode", "night", saved.night_mode if saved else "no")
    if saved is not None:
        adb.run("shell", "settings", "put", "global", "stay_on_while_plugged_in", saved.stay_on)
        if dex_active(adb) != saved.dex:
            try:
                set_dex(adb, saved.dex)
            except ConfigNotRunError:
                pass
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
