#!/usr/bin/env bash
# products/linux-desktop/scripts/package_debian.sh — End-to-end Debian package & APT pipeline
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PRODUCT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT="$(cd "$PRODUCT_DIR/../.." && pwd)"

echo "━━━ Step 1: Building Debian Package (.deb) ━━━"
"$SCRIPT_DIR/build_deb.sh" "$@"

echo "━━━ Step 2: Generating In-Repo APT Repository Metadata ━━━"
python3 "$REPO_ROOT/apt/generate_apt_repo.py" --repo-root "$REPO_ROOT/apt"

echo "━━━ Step 3: Verifying Package & Repository Integrity ━━━"
"$SCRIPT_DIR/verify_package.sh"

echo "✔ Debian package compilation and APT repository generation complete!"
