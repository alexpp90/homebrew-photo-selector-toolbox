"""Build the `.lab` APKs once per run and install them on each Android lab device."""

from __future__ import annotations

import os
import shutil
import subprocess
from pathlib import Path
from typing import Callable

from ..paths import repo_root
from .adb import AdbClient

# Gradle module and lab application id per Android product.
MODULES = {
    "phototok": (":phototok", "com.phototok.lab"),
    "android-desktop": (":android-desktop", "com.photoselectortoolbox.lab"),
}

RunFn = Callable[[list[str], Path], subprocess.CompletedProcess[str]]


def android_sdk_env() -> dict[str, str]:
    """Environment with `ANDROID_HOME` set when neither it nor `local.properties` provides one.

    Fresh worktrees and the systemd timer have no `local.properties`; the SDK is found from the
    `adb` on PATH (`<sdk>/platform-tools/adb`) or the Android Studio default `~/Android/Sdk`.
    """
    env = dict(os.environ)
    if env.get("ANDROID_HOME") or env.get("ANDROID_SDK_ROOT"):
        return env
    adb = shutil.which("adb")
    candidates = [Path(adb).resolve().parent.parent] if adb else []
    candidates.append(Path.home() / "Android" / "Sdk")
    for sdk in candidates:
        if (sdk / "platform-tools").is_dir():
            env["ANDROID_HOME"] = str(sdk)
            break
    return env


def _default_run(cmd: list[str], cwd: Path) -> subprocess.CompletedProcess[str]:
    return subprocess.run(cmd, cwd=cwd, capture_output=True, text=True, check=False, env=android_sdk_env())


def apk_path(product: str, root: Path | None = None) -> Path:
    """Expected location of the assembled `.lab` APK for `product`."""
    module = MODULES[product][0].lstrip(":")
    out = (root or repo_root()) / "products" / "android" / module / "build" / "outputs" / "apk" / "lab"
    matches = sorted(out.glob("*-lab.apk")) if out.is_dir() else []
    return matches[0] if matches else out / f"{module}-lab.apk"


def build_lab_apks(
    products: list[str],
    log_path: Path,
    *,
    root: Path | None = None,
    run: RunFn | None = None,
) -> tuple[bool, str | None]:
    """Run `./gradlew :<module>:assembleLab` for every requested Android product."""
    wanted = [p for p in products if p in MODULES]
    if not wanted:
        return True, None
    r = root or repo_root()
    android_root = r / "products" / "android"
    tasks = [f"{MODULES[p][0]}:assembleLab" for p in wanted]
    res = (run or _default_run)(["./gradlew", "--console=plain", *tasks], android_root)
    log_path.parent.mkdir(parents=True, exist_ok=True)
    log_path.write_text(f"$ ./gradlew {' '.join(tasks)}\n{res.stdout}\n{res.stderr}", encoding="utf-8")
    if res.returncode != 0:
        return False, f"gradle assembleLab failed (rc={res.returncode}); see {log_path.name}"
    missing = [p for p in wanted if not apk_path(p, r).is_file()]
    if missing:
        return False, f"assembleLab succeeded but no APK for {', '.join(missing)}"
    return True, None


def install_lab_apk(adb: AdbClient, product: str, *, user: int = 0, root: Path | None = None) -> None:
    """Install (or update in place) the `.lab` APK; the persisted SAF grant survives `-r`.

    A `.lab` build signed with another machine's debug key cannot be updated in place; only
    that lab package (never the user's Play install) is uninstalled and installed fresh.
    """
    apk = apk_path(product, root)
    try:
        adb.install(apk, user=user)
    except subprocess.CalledProcessError as exc:
        text = (exc.stdout or b"").decode(errors="replace") + (exc.stderr or b"").decode(errors="replace")
        if "UPDATE_INCOMPATIBLE" not in text:
            raise RuntimeError(f"adb install {apk.name} failed: {text.strip()[-300:]}") from exc
        adb.run("uninstall", MODULES[product][1], timeout=60)
        adb.install(apk, user=user)
