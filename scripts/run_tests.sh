#!/usr/bin/env bash
#
# run_tests.sh — unified test runner for Photo Selector Toolbox
#
# Usage:
#   ./scripts/run_tests.sh                  # run everything possible locally
#   ./scripts/run_tests.sh --python         # Python tests only
#   ./scripts/run_tests.sh --android-unit   # Android JVM unit tests only
#   ./scripts/run_tests.sh --android-device # Android instrumented tests (needs emulator/device)
#   ./scripts/run_tests.sh --all            # all of the above
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

run_python=false
run_android_unit=false
run_android_device=false

if [ $# -eq 0 ]; then
    # Default: run python + android unit (skip device tests which need emulator)
    run_python=true
    run_android_unit=true
fi

for arg in "$@"; do
    case "$arg" in
        --python)         run_python=true ;;
        --android-unit)   run_android_unit=true ;;
        --android-device) run_android_device=true ;;
        --all)
            run_python=true
            run_android_unit=true
            run_android_device=true
            ;;
        -h|--help)
            echo "Usage: $0 [--python] [--android-unit] [--android-device] [--all]"
            echo ""
            echo "  --python          Run Python pytest suite (Mac/Linux/Windows)"
            echo "  --android-unit    Run Android JVM unit tests (no emulator needed)"
            echo "  --android-device  Run Android instrumented tests (requires emulator or device)"
            echo "  --all             Run all test suites"
            echo ""
            echo "  With no flags, runs --python and --android-unit."
            exit 0
            ;;
        *)
            echo -e "${RED}Unknown argument: $arg${NC}"
            exit 1
            ;;
    esac
done

EXIT_CODE=0

# ── Python tests ──────────────────────────────────────────────────
if $run_python; then
    echo -e "\n${YELLOW}━━━ Python Tests ━━━${NC}"
    cd "$ROOT_DIR"
    if command -v poetry &>/dev/null; then
        poetry run pytest --tb=short -q || EXIT_CODE=1
    elif command -v pytest &>/dev/null; then
        pytest --tb=short -q || EXIT_CODE=1
    else
        echo -e "${RED}Neither poetry nor pytest found. Install with: pip install poetry${NC}"
        EXIT_CODE=1
    fi
fi

# ── Android JVM unit tests ───────────────────────────────────────
if $run_android_unit; then
    echo -e "\n${YELLOW}━━━ Android Unit Tests (JVM) ━━━${NC}"
    cd "$ROOT_DIR/android"
    if [ -f "./gradlew" ]; then
        chmod +x ./gradlew
        ./gradlew testDebugUnitTest --stacktrace || EXIT_CODE=1
    else
        echo -e "${RED}gradlew not found in android/. Skipping.${NC}"
        EXIT_CODE=1
    fi
fi

# ── Android instrumented tests ───────────────────────────────────
if $run_android_device; then
    echo -e "\n${YELLOW}━━━ Android Instrumented Tests (device/emulator) ━━━${NC}"
    cd "$ROOT_DIR/android"

    # Check for connected device or running emulator
    if command -v adb &>/dev/null; then
        DEVICE_COUNT=$(adb devices | grep -cw "device" || true)
        if [ "$DEVICE_COUNT" -lt 1 ]; then
            echo -e "${RED}No Android device or emulator detected.${NC}"
            echo "Start an emulator or connect a device via USB, then retry."
            echo ""
            echo "Quick start:"
            echo "  emulator -avd <avd_name> -no-audio -no-boot-anim &"
            echo "  adb wait-for-device"
            EXIT_CODE=1
        else
            chmod +x ./gradlew
            ./gradlew connectedDebugAndroidTest --stacktrace || EXIT_CODE=1
        fi
    else
        echo -e "${RED}adb not found. Install Android SDK platform-tools.${NC}"
        EXIT_CODE=1
    fi
fi

# ── Summary ──────────────────────────────────────────────────────
echo ""
if [ $EXIT_CODE -eq 0 ]; then
    echo -e "${GREEN}All requested test suites passed.${NC}"
else
    echo -e "${RED}One or more test suites failed.${NC}"
fi

exit $EXIT_CODE
