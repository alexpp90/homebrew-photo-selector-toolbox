#!/bin/bash
set -euo pipefail

echo "🍺 Installing Photo Selector Toolbox via Homebrew..."

# Check for Homebrew
if ! command -v brew &>/dev/null; then
  echo "Homebrew not found. Installing..."
  /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
fi

# Tap the repository and install the formula
brew tap alexpp90/photo-selector-toolbox
brew install photo-selector-toolbox

echo ""
echo "✅ Installed! You can now run:"
echo "   • 'photo-selector-toolbox' (CLI) from the terminal"
echo "   • 'photo-selector-toolbox-gui' (GUI) from the terminal"
echo ""
echo "To update later: brew upgrade photo-selector-toolbox"
