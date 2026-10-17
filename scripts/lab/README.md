# Photo Selector Toolbox — E2E Device Lab (`scripts/lab`)

Local device lab for automated multi-step UI journeys, deterministic layout & filesystem oracles, and AI visual review across Android Desktop, PhotoTok, macOS Desktop, and Linux Desktop. It runs on demand on the Linux hub; it is **not** a CI gate.

## Run it

```bash
# What is reachable right now (add --with-personal to probe personal devices too)
python3 scripts/lab/lab.py devices

# Everything, on the dedicated devices (builds the .lab APKs, installs, runs, writes the report)
python3 scripts/lab/lab.py run --all

# Everything, including personal devices (your phone, tablet, Mac)
python3 scripts/lab/lab.py run --all --with-personal

# One product / journey / config / device
python3 scripts/lab/lab.py run --product android-desktop --journey J-AD-02 --config tab-landscape --device pixel-tablet
python3 scripts/lab/lab.py run --product phototok --no-build        # reuse installed .lab APKs
```

The run directory (`private/lab-runs/<date>-<time>-<sha>/`, or `/tmp/lab-runs/` without the
private overlay) holds `report.html`, `report.md`, `run.json`, `findings.det.json` and one
folder per `<product>/<config>@<device>/<journey>/` with every checkpoint's screenshot, UI tree,
file-system snapshot, timing and the Maestro logs.

## Devices

Devices are listed in the maintainer-private `private/lab/devices.yaml`, keyed by hardware
serial (wireless-debugging ports change on every reconnect). Each Android product fans out over
every selected, reachable device; results are labelled `<config>@<device>`. Devices marked
`personal: true` run only with `--with-personal` or when named with `--device`.

Pair a device once (Settings → Developer options → Wireless debugging → *Pair device with
pairing code*, keep the dialog open):

```bash
python3 scripts/lab/lab.py pair 123456                   # address found over mDNS
python3 scripts/lab/lab.py pair 123456 --device s25-ultra # several dialogs open at once
```

A device that pairs but never advertises its connect port over mDNS (seen: Pixel 4a) gets a
`host: <ip>` entry; the lab then finds the port with `nmap`. Reserve that IP in the router.

What a run changes on a device, and restores afterwards: display size and density, font scale,
dark mode, rotation, stay-awake, and Samsung DeX. Lab data lives only in `/sdcard/PSTLab/`; the
`.lab` build (`com.phototok.lab`, `com.photoselectortoolbox.lab`) is separate from any store
install.

- **One working folder.** Every journey's fixture is pushed into `/sdcard/PSTLab/work`, so one
  persisted folder grant serves all journeys. Before each journey that is not a fresh install,
  `products/android/<product>/tests/e2e/_ensure_folder.yaml` grants that folder if the app has
  none and brings the app to a photo.
- **Locale.** Picker flows use `android:id/button1`, not button text, so devices in any
  language work.
- **Rotation.** A docked Pixel Tablet ignores rotation requests; its portrait configs are
  reported *not run*.
- **DeX.** `dex-on-device` runs only on devices with `supports: [dex]` and switches Samsung DeX
  on through its Quick Settings tile; every other config switches it off. If the switch does
  not take, the config is reported *not run*.

## macOS (remote, `pst-lab` account)

The hub triggers the Mac over SSH; a LaunchAgent in the `pst-lab` user's GUI session builds the
app and runs the macOS journeys, and the hub syncs the results back. One-time setup on the Mac,
as `pst-lab`: `scripts/lab/macos/setup_pst_lab.sh` (its header lists the prerequisites).

## Weekly timer (Linux hub)

```bash
python3 scripts/lab/lab.py install-timer            # Sunday 03:00, dedicated devices only
python3 scripts/lab/lab.py install-timer --dry-run  # print the units instead
```

The units are written for the checkout you run this from, with the current `PATH` (adb,
Maestro), and pull `main` before each run.

## Host prerequisites

`python3 scripts/lab/lab.py preflight` lists them: `adb`, Maestro (`~/.maestro/bin`), `Xvfb`,
`xdotool` and `dbus-run-session` for Linux journeys, `nmap` for devices without mDNS, and
Debian's `/usr/bin/python3` with PyGObject for the Linux Desktop app (override with
`PST_LAB_PYTHON`). PyYAML and Pillow are optional; built-in fallbacks are used without them.

## Other commands

```bash
python3 scripts/lab/lab.py fixtures --out /tmp/pstlab
python3 scripts/lab/lab.py layout-lint path/to/checkpoint.tree.json --rules scripts/lab/rules/android-desktop.yaml
python3 scripts/lab/lab.py review-bundle private/lab-runs/<run-id>
python3 scripts/lab/lab.py review-ingest private/lab-runs/<run-id>
python3 scripts/lab/lab.py report private/lab-runs/<run-id>
```

## Safety Guarantees

1. **Path Guard (`lab/paths.py`):** Every host filesystem write or deletion is restricted by `ensure_under()` to `/tmp/pstlab`, `~/PSTLab`, or `private/lab-runs`. Every Android `adb` deletion/push is restricted by `ensure_android_lab_path()` to `/sdcard/PSTLab/`.
2. **`.lab` Build Type:** Lab runs target the `.lab` application ID (`com.phototok.lab`, `com.photoselectortoolbox.lab`), keeping personal photographs, settings and store installs untouched.
3. **No Desktop Disturbance:** Linux journeys run inside a private `Xvfb` + `dbus-run-session`. macOS journeys run in the separate `pst-lab` account.
4. **Personal devices are opt-in** and have every changed setting restored.

## Headless Wayland Evaluation Notes (C-43)

Evaluated options for running the GTK4/Libadwaita journeys under a headless Wayland compositor (`mutter`) in addition to the default deterministic Xvfb + `xdotool` session (`C-41`):

1. **`dogtail >= 2.1.0` (`HermeticSession`):** Boots a bare `mutter` with a virtual monitor and private D-Bus + AT-SPI accessibility bus without requiring a logged-in desktop session. Recommended when testing Wayland-specific fractional scaling (`scale-monitor-framebuffer`), installed in a dedicated venv since Debian 13 packages `dogtail 1.0.x`.
2. **`waydriver` (0.3.x):** Provides headless `mutter` + AT-SPI + PipeWire screenshots and an MCP server, suitable for exploratory sessions (`C-70`), though less mature than Xvfb + `xdotool` for deterministic CI/lab gating.
3. **Plain `mutter --headless --wayland --no-x11 --virtual-monitor 1920x1080`:** Launches cleanly under `dbus-run-session` with `GDK_BACKEND=wayland`, and `lab-checkpoint` (`Gtk.WidgetPaintable` + renderer texture) captures screenshots directly in-process without needing PipeWire; only key injection requires `org.gnome.Mutter.RemoteDesktop` over D-Bus.
