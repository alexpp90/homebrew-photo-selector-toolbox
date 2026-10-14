#!/usr/bin/env bash
# products/linux-desktop/scripts/verify_package.sh — Packaging & APT Verification Gate
# Usage: verify_package.sh [--apt-dir DIR]   (default: products/linux-desktop/dist/apt,
#        produced by package_debian.sh)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PRODUCT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

python3 "$SCRIPT_DIR/verify_package.py" --product-dir "$PRODUCT_DIR" "$@"
