#!/usr/bin/env bash
# Assembles PhotoSelector.app from the SwiftPM executable.
#
# A bundled app gets activation policy `.regular` from Launch Services, shows in the Dock
# and receives keyboard focus normally. (The raw SwiftPM binary is also fixed at runtime by
# ForegroundActivation, but the bundle is the supported way to run the app.)
#
# Usage: products/macos-desktop/scripts/build_app.sh [debug|release]
# Prints the path of the assembled bundle on the last line.
set -euo pipefail

CONFIG="${1:-release}"
PRODUCT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

swift build -c "$CONFIG" --package-path "$PRODUCT_DIR" --product PhotoSelectorApp >&2
BIN_DIR="$(swift build -c "$CONFIG" --package-path "$PRODUCT_DIR" --show-bin-path)"

APP="$PRODUCT_DIR/.build/app/PhotoSelector.app"
rm -rf "$APP"
mkdir -p "$APP/Contents/MacOS" "$APP/Contents/Resources"
cp "$BIN_DIR/PhotoSelectorApp" "$APP/Contents/MacOS/PhotoSelectorApp"

cat > "$APP/Contents/Info.plist" <<'PLIST'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleDevelopmentRegion</key><string>en</string>
    <key>CFBundleExecutable</key><string>PhotoSelectorApp</string>
    <key>CFBundleIdentifier</key><string>com.photoselectortoolbox.macos</string>
    <key>CFBundleName</key><string>Photo Selector</string>
    <key>CFBundleDisplayName</key><string>Photo Selector</string>
    <key>CFBundlePackageType</key><string>APPL</string>
    <key>CFBundleShortVersionString</key><string>1.0</string>
    <key>CFBundleVersion</key><string>1</string>
    <key>LSMinimumSystemVersion</key><string>14.0</string>
    <key>LSApplicationCategoryType</key><string>public.app-category.photography</string>
    <key>NSHighResolutionCapable</key><true/>
    <key>NSPrincipalClass</key><string>NSApplication</string>
</dict>
</plist>
PLIST

# Ad-hoc signature so Gatekeeper/TCC treat the bundle as one stable identity.
codesign --force --sign - "$APP" >/dev/null 2>&1 || true
echo "$APP"
