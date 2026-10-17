#!/usr/bin/env bash
# Private headless X11 + D-Bus laboratory session wrapper for Linux Desktop (C-41).
# Never touches DISPLAY=:0 or the user's real session.
set -euo pipefail

DISPLAY_NUM=99
SIZE="1920x1080"
SCALE=1
COLOR_SCHEME="default"
TEXT_SCALING="1.0"
LAB_DIR="/tmp/pstlab"
ARTIFACTS_DIR="/tmp/pstlab-artifacts"
FIXTURE_DIR=""
USE_INSTALLED=0
SCRIPT_FILE=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --display-num) DISPLAY_NUM="$2"; shift 2 ;;
    --size) SIZE="$2"; shift 2 ;;
    --scale) SCALE="$2"; shift 2 ;;
    --color-scheme) COLOR_SCHEME="$2"; shift 2 ;;
    --text-scaling) TEXT_SCALING="$2"; shift 2 ;;
    --lab-dir) LAB_DIR="$2"; shift 2 ;;
    --artifacts) ARTIFACTS_DIR="$2"; shift 2 ;;
    --fixture) FIXTURE_DIR="$2"; shift 2 ;;
    --installed) USE_INSTALLED=1; shift ;;
    --script) SCRIPT_FILE="$2"; shift 2 ;;
    *) echo "Unknown option: $1" >&2; exit 2 ;;
  esac
done

case "$LAB_DIR" in
  /tmp/pstlab* | /var/tmp/pstlab*) ;;
  *) echo "Refusing to use non-lab directory: $LAB_DIR" >&2; exit 2 ;;
esac

mkdir -p "$LAB_DIR/xdg-data" "$LAB_DIR/xdg-config" "$ARTIFACTS_DIR"

Xvfb ":${DISPLAY_NUM}" -screen 0 "${SIZE}x24" -nolisten tcp >/dev/null 2>&1 &
XVFB_PID=$!
cleanup() {
  kill "$XVFB_PID" >/dev/null 2>&1 || true
}
trap cleanup EXIT

sleep 0.3

export DISPLAY=":${DISPLAY_NUM}"
export GDK_BACKEND=x11
export GDK_SCALE="${SCALE}"
export XDG_DATA_HOME="${LAB_DIR}/xdg-data"
export XDG_CONFIG_HOME="${LAB_DIR}/xdg-config"
export PST_LAB=1
# Xvfb has no GPU: GTK's default Vulkan/GL renderer paints black there; cairo is software and
# deterministic. No portals: in a private bus the desktop portal blocks ~25 s on a missing
# secrets service before the window shows, and the lab never uses portal dialogs.
export GSK_RENDERER="${GSK_RENDERER:-cairo}"
export GDK_DEBUG="${GDK_DEBUG:+$GDK_DEBUG,}no-portals"
export GTK_USE_PORTAL=0
export PST_LAB_ARTIFACTS="${ARTIFACTS_DIR}"

if [[ "$USE_INSTALLED" -eq 1 ]]; then
  APP_CMD=(photo-selector-linux "$FIXTURE_DIR")
else
  # PST_LAB_PYTHON: an interpreter with the distribution's PyGObject (the lab runner sets it).
  APP_CMD=("${PST_LAB_PYTHON:-python3}" -m photo_selector_linux "$FIXTURE_DIR")
fi

dbus-run-session -- bash -c '
  set -euo pipefail
  gsettings set org.gnome.desktop.interface color-scheme "'"${COLOR_SCHEME}"'" >/dev/null 2>&1 || true
  gsettings set org.gnome.desktop.interface text-scaling-factor "'"${TEXT_SCALING}"'" >/dev/null 2>&1 || true

  "$@" >"'"${ARTIFACTS_DIR}"'/journey.log" 2>&1 &
  APP_PID=$!
  trap "kill \$APP_PID >/dev/null 2>&1 || true" EXIT

  for _ in $(seq 1 300); do
    if xdotool search --onlyvisible --name "Photo Selector" >/dev/null 2>&1; then
      xdotool search --onlyvisible --name "Photo Selector" windowactivate --sync >/dev/null 2>&1 || true
      break
    fi
    sleep 0.1
  done
  sleep 1

  if [[ -n "'"${SCRIPT_FILE}"'" && -f "'"${SCRIPT_FILE}"'" ]]; then
    while IFS= read -r line || [[ -n "$line" ]]; do
      [[ -z "$line" || "$line" =~ ^# ]] && continue
      cmd="${line%% *}"
      arg="${line#* }"
      case "$cmd" in
        checkpoint)
          if ! gapplication action org.photoselector.Linux lab-checkpoint "'"'"'${arg}'"'"'" >/dev/null 2>&1; then
            gdbus call --session \
              --dest org.photoselector.Linux \
              --object-path /org/photoselector/Linux \
              --method org.gtk.Actions.Activate \
              "lab-checkpoint" "[<'"'"'${arg}'"'"'>]" "{}" >/dev/null 2>&1 || true
          fi
          ;;
        key)
          xdotool key --clearmodifiers "$arg"
          ;;
        sleep)
          sleep "$arg"
          ;;
      esac
    done < "'"${SCRIPT_FILE}"'"
  fi

  kill "$APP_PID" >/dev/null 2>&1 || true
  wait "$APP_PID" >/dev/null 2>&1 || true
' -- "${APP_CMD[@]}"
