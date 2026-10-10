#!/usr/bin/env bash
# Photo Selector Linux — APT Repository Setup Script for Debian 13 (Trixie)
set -euo pipefail

if [ "$(id -u)" -ne 0 ]; then
    echo "Error: This script must be run as root (e.g. curl -fsSL ... | sudo bash)." >&2
    exit 1
fi

echo "━━━ Setting up Photo Selector APT Repository for Debian 13 (Trixie) ━━━"

# 1. Install prerequisites
apt-get update -qq || true
apt-get install -y -qq curl ca-certificates gpg >/dev/null 2>&1 || true

# 2. Configure dedicated keyring directory
install -m 0755 -d /etc/apt/keyrings

# 3. Import repository public key
KEYRING_URL="https://alexanderpatz.github.io/homebrew-photo-selector-toolbox/apt/photo-selector-archive-keyring.gpg"
if curl -fsSL "$KEYRING_URL" -o /etc/apt/keyrings/photo-selector-archive-keyring.gpg 2>/dev/null; then
    chmod 0644 /etc/apt/keyrings/photo-selector-archive-keyring.gpg
    echo "✔ GPG archive keyring imported into /etc/apt/keyrings/"
else
    echo "Notice: Repository keyring not reachable online, using fallback."
fi

# 4. Install deb822 sources file
SOURCES_URL="https://alexanderpatz.github.io/homebrew-photo-selector-toolbox/apt/photo-selector.sources"
if curl -fsSL "$SOURCES_URL" -o /etc/apt/sources.list.d/photo-selector.sources 2>/dev/null; then
    chmod 0644 /etc/apt/sources.list.d/photo-selector.sources
else
    cat <<'EOF' > /etc/apt/sources.list.d/photo-selector.sources
Types: deb
URIs: https://alexanderpatz.github.io/homebrew-photo-selector-toolbox/apt/
Suites: trixie
Components: main
Architectures: amd64 all
Signed-By: /etc/apt/keyrings/photo-selector-archive-keyring.gpg
EOF
    chmod 0644 /etc/apt/sources.list.d/photo-selector.sources
fi
echo "✔ Repository source installed: /etc/apt/sources.list.d/photo-selector.sources"

# 5. Refresh repository indices
echo "Updating APT package index..."
apt-get update -qq

echo "━━━ Setup Complete ━━━"
echo "Install Photo Selector with:"
echo "  sudo apt install -y photo-selector-linux"
