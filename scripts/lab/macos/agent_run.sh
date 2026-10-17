#!/usr/bin/env bash
# Executed inside pst-lab's Aqua session by com.pstlab.runner.plist when ~/pst/trigger/run.json changes (C-32).
set -euo pipefail

TRIGGER_JSON="$HOME/pst/trigger/run.json"
if [[ ! -f "$TRIGGER_JSON" ]]; then
  exit 0
fi

REPO_DIR="$HOME/pst/photo-selector"
RUN_ID="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["run_id"])' "$TRIGGER_JSON")"
COMMIT="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["commit"])' "$TRIGGER_JSON")"

OUT_DIR="$HOME/pst/out/$RUN_ID"
if [[ -f "$OUT_DIR/done" ]]; then
  exit 0
fi
mkdir -p "$OUT_DIR"

cd "$REPO_DIR"
git fetch --all --prune
git checkout "$COMMIT"

(cd products/macos-desktop && ./scripts/build_app.sh release)
mkdir -p "$HOME/Applications"
rm -rf "$HOME/Applications/PhotoSelector.app"
cp -R products/macos-desktop/.build/app/PhotoSelector.app "$HOME/Applications/PhotoSelector.app"

python3 scripts/lab/lab.py fixtures --out "$HOME/PSTLab"
python3 scripts/lab/lab.py run --product macos --out "$OUT_DIR"
touch "$OUT_DIR/done"
