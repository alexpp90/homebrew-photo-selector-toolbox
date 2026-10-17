# Photo Selector Toolbox — E2E Device Lab (`scripts/lab`)

Local device lab for automated multi-step UI journeys, deterministic layout & filesystem oracles, and AI visual review across Android Desktop, PhotoTok, macOS Desktop, and Linux Desktop.

## Quick Start

```bash
# Show CLI help
python3 scripts/lab/lab.py --help

# Generate synthetic test fixtures
python3 scripts/lab/lab.py fixtures --out /tmp/pstlab

# Run layout lint on a captured tree
python3 scripts/lab/lab.py layout-lint path/to/checkpoint.tree.json --rules scripts/lab/rules/android-desktop.yaml

# Run journeys across all available targets or a single product
python3 scripts/lab/lab.py run --all
python3 scripts/lab/lab.py run --product macos --journey J-MAC-01 --config w1440

# Build AI review bundles & ingest findings
python3 scripts/lab/lab.py review-bundle private/lab-runs/<run-id>
python3 scripts/lab/lab.py review-ingest private/lab-runs/<run-id>

# Render HTML report
python3 scripts/lab/lab.py report private/lab-runs/<run-id>
```

## Safety Guarantees

1. **Path Guard (`lab/paths.py`):** Every host filesystem write or deletion is restricted by `ensure_under()` to `/tmp/pstlab`, `~/PSTLab`, or `private/lab-runs`. Every Android `adb` deletion/push is restricted by `ensure_android_lab_path()` to `/sdcard/PSTLab/`.
2. **Android Secondary User & `.lab` Build Type:** Lab runs target the `.lab` application ID (`com.phototok.lab`, `com.photoselectortoolbox.lab`) inside Android secondary user `10`, keeping personal photographs and settings completely untouched.
3. **No Desktop Disturbance:** Linux journeys run inside a private `Xvfb` + `dbus-run-session`. macOS journeys run with `.accessory` activation policy behind the active window unless `PST_SHOW_GUI=1` is set.

## Weekly Systemd User Timer (Linux Hub — C-60)

To install the weekly automated run (Sunday 03:00, Linux + AVD configurations):

```bash
mkdir -p ~/.config/systemd/user
cp scripts/lab/systemd/pstlab-weekly.service scripts/lab/systemd/pstlab-weekly.timer ~/.config/systemd/user/
systemctl --user daemon-reload
systemctl --user enable --now pstlab-weekly.timer
systemctl --user list-timers pstlab-weekly.timer
```

## Headless Wayland Evaluation Notes (C-43)

Evaluated options for running the GTK4/Libadwaita journeys under a headless Wayland compositor (`mutter`) in addition to the default deterministic Xvfb + `xdotool` session (`C-41`):

1. **`dogtail >= 2.1.0` (`HermeticSession`):** Boots a bare `mutter` with a virtual monitor and private D-Bus + AT-SPI accessibility bus without requiring a logged-in desktop session. Recommended when testing Wayland-specific fractional scaling (`scale-monitor-framebuffer`), installed in a dedicated venv since Debian 13 packages `dogtail 1.0.x`.
2. **`waydriver` (0.3.x):** Provides headless `mutter` + AT-SPI + PipeWire screenshots and an MCP server, suitable for exploratory sessions (`C-70`), though less mature than Xvfb + `xdotool` for deterministic CI/lab gating.
3. **Plain `mutter --headless --wayland --no-x11 --virtual-monitor 1920x1080`:** Launches cleanly under `dbus-run-session` with `GDK_BACKEND=wayland`, and `lab-checkpoint` (`Gtk.WidgetPaintable` + renderer texture) captures screenshots directly in-process without needing PipeWire; only key injection requires `org.gnome.Mutter.RemoteDesktop` over D-Bus.
