#!/bin/bash
set -euo pipefail

echo "🍺 Installing Photo Selector Toolbox via Homebrew..."

# Check for Homebrew
if ! command -v brew &>/dev/null; then
  echo "Homebrew not found. Installing..."
  /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
fi

# Tap the repository and install the cask
brew tap alexpp90/photo-selector-toolbox
brew install --cask photo-selector-toolbox

echo ""
echo "✅ Installed! You can now:"
echo "   • Open 'Photo Selector Toolbox' from Applications"
echo "   • Run 'photo-selector-toolbox' from the terminal"
echo ""
echo "To update later: brew upgrade --cask --greedy photo-selector-toolbox"
