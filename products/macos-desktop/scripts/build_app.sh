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
if [[ -f "$PRODUCT_DIR/Resources/AppIcon.icns" ]]; then
    cp "$PRODUCT_DIR/Resources/AppIcon.icns" "$APP/Contents/Resources/AppIcon.icns"
elif [[ -f "$PRODUCT_DIR/../../assets/logo.icns" ]]; then
    cp "$PRODUCT_DIR/../../assets/logo.icns" "$APP/Contents/Resources/AppIcon.icns"
else
    echo "ERROR: AppIcon.icns not found in $PRODUCT_DIR/Resources or assets/" >&2
    exit 1
fi

cat > "$APP/Contents/Info.plist" <<'PLIST'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleDevelopmentRegion</key><string>en</string>
    <key>CFBundleExecutable</key><string>PhotoSelectorApp</string>
    <key>CFBundleIconFile</key><string>AppIcon</string>
    <key>CFBundleIdentifier</key><string>com.photoselectortoolbox.macos</string>
    <key>CFBundleName</key><string>PhotoSelector Studio</string>
    <key>CFBundleDisplayName</key><string>PhotoSelector Studio</string>
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
