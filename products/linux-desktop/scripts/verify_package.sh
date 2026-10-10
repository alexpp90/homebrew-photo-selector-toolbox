#!/usr/bin/env bash
# products/linux-desktop/scripts/verify_package.sh — Packaging & APT Verification Gate
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PRODUCT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT="$(cd "$PRODUCT_DIR/../.." && pwd)"

# Run verification engine
python3 "$SCRIPT_DIR/verify_package.py" \
    --product-dir "$PRODUCT_DIR" \
    --repo-root "$REPO_ROOT"
