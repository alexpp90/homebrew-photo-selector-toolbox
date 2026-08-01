#!/usr/bin/env bash
#
# run_tests.sh — CI-parity test runner for Photo Selector Toolbox
#
# This script is the LOCAL MIRROR of .github/workflows/desktop.yml and
# .github/workflows/android.yml. Every gate CI enforces is enforced here, in
# the same order, with the same flags. If this script is green and it reports
# no SKIPPED gates, CI is expected to be green.
#
# The inverse is the point: a gate that exists only in CI is a gate an agent
# cannot satisfy before pushing, which turns the PR into a push-and-pray loop.
# When you add a gate to a workflow, add it here in the same commit.
#
# Usage:
#   ./scripts/run_tests.sh                  # everything runnable on this machine
#   ./scripts/run_tests.sh --python         # Python gates only (flake8 + pytest + coverage)
#   ./scripts/run_tests.sh --android        # Android gates that need no device
#   ./scripts/run_tests.sh --android-device # Android instrumented tests (needs emulator/device)
#   ./scripts/run_tests.sh --all            # all of the above
#   ./scripts/run_tests.sh --strict         # treat SKIPPED gates as failures
#   ./scripts/run_tests.sh --quick          # pytest only, no lint/coverage (inner-loop use)
#
# Exit codes: 0 all attempted gates passed; 1 a gate failed; 2 --strict and a gate was skipped.
#
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
DIM='\033[2m'
NC='\033[0m'

# Keep in sync with `--cov-fail-under` in .github/workflows/desktop.yml.
COV_FAIL_UNDER=60

run_python=false
run_android=false
run_android_device=false
strict=false
quick=false

if [ $# -eq 0 ]; then
    run_python=true
    run_android=true
fi

for arg in "$@"; do
    case "$arg" in
        --python)         run_python=true ;;
        --android|--android-unit) run_android=true ;;
        --android-device) run_android_device=true ;;
        --strict)         strict=true ;;
        --quick)          quick=true; run_python=true ;;
        --all)
            run_python=true
            run_android=true
            run_android_device=true
            ;;
        -h|--help)
            sed -n '2,24p' "$0" | sed 's/^# \{0,1\}//'
            exit 0
            ;;
        *)
            echo -e "${RED}Unknown argument: $arg${NC}"
            exit 1
            ;;
    esac
done

EXIT_CODE=0
SKIPPED_ANY=false
RESULTS_FILE="$(mktemp)"
trap 'rm -f "$RESULTS_FILE"' EXIT

# ── Result helpers ────────────────────────────────────────────────
# Every gate reports exactly one line so the closing summary can state, per
# gate, whether it actually ran. A gate that silently did not run is the
# failure mode this script exists to prevent.

record() { printf '%s|%s|%s\n' "$1" "$2" "$3" >> "$RESULTS_FILE"; }

pass() { record PASS "$1" "$2"; echo -e "${GREEN}  ✔ $1${NC}"; }
fail() { record FAIL "$1" "$2"; echo -e "${RED}  ✘ $1${NC}"; EXIT_CODE=1; }
warn() { record WARN "$1" "$2"; echo -e "${YELLOW}  ! $1${NC}"; }
skip() { record SKIP "$1" "$3"; SKIPPED_ANY=true; echo -e "${YELLOW}  ⊘ $1 — $3${NC}"; }

section() { echo -e "\n${BLUE}━━━ $1 ━━━${NC}"; }

# Run a command, reporting pass/fail under a gate name.
gate() {
    local name="$1"; shift
    local ci_ref="$1"; shift
    if "$@"; then
        pass "$name" "$ci_ref"
    else
        fail "$name" "$ci_ref"
    fi
}

have() { command -v "$1" &>/dev/null; }

# Prefer poetry when a lockfile-managed venv exists, else fall back to bare tools.
PY_RUNNER=()
if have poetry && [ -f "$ROOT_DIR/poetry.lock" ]; then
    PY_RUNNER=(poetry run)
fi
py() { "${PY_RUNNER[@]}" "$@"; }

# ── Python gates (mirror: desktop.yml `lint`, `test`, `visual`) ────
if $run_python; then
    cd "$ROOT_DIR" || exit 1

    if ! have poetry && ! have pytest; then
        section "Python"
        skip "Python gates" "" "neither poetry nor pytest found (pip install poetry)"
    else
        # ── flake8 ────────────────────────────────────────────────
        # CI runs this as a *separate job that gates every other job*. A flake8
        # error therefore fails the whole pipeline before a single test runs,
        # which is why it must be the first thing checked locally too.
        section "Lint (flake8)"
        if $quick; then
            skip "flake8" "" "--quick"
        elif py flake8 --version &>/dev/null; then
            gate "flake8 src/ tests/" "desktop.yml:lint" \
                py flake8 src/ tests/ --count --show-source --statistics
        else
            skip "flake8" "" "flake8 not installed in the active environment"
        fi

        # ── pytest + coverage ─────────────────────────────────────
        # `-m "not visual"` matches CI's split: visual regression tests run in
        # their own job. Without the marker filter the local run and the CI run
        # execute different test sets.
        section "Python tests"
        if $quick; then
            gate "pytest (quick, no coverage gate)" "desktop.yml:test (partial)" \
                py pytest --tb=short -q -m "not visual"
        else
            # The coverage gate is authoritative on Linux (CI enforces it there
            # only, because linux_only/mac_only/windows_only markers skip
            # different tests per platform). Running it here still catches
            # genuine coverage regressions early.
            gate "pytest -m 'not visual' --cov-fail-under=$COV_FAIL_UNDER" "desktop.yml:test" \
                py pytest --tb=short -q \
                    --cov=photo_selector_toolbox \
                    --cov-report=term-missing:skip-covered \
                    --cov-fail-under="$COV_FAIL_UNDER" \
                    -m "not visual"
        fi

        # ── visual regression ─────────────────────────────────────
        # Marked `linux_only` in tests/visual/test_visual_regression.py: the
        # baselines are rendered under Xvfb, so macOS/Windows font metrics
        # produce false diffs. This gate is genuinely un-runnable off Linux —
        # it is reported as SKIPPED rather than silently omitted.
        section "Visual regression"
        if $quick; then
            skip "visual tests" "" "--quick"
        elif [ "$(uname -s)" != "Linux" ]; then
            skip "visual tests" "" "linux_only — baselines are Xvfb-rendered; CI job 'Visual regression tests' covers this"
        elif [ -z "${DISPLAY:-}" ]; then
            skip "visual tests" "" "no DISPLAY; start Xvfb: Xvfb :99 & export DISPLAY=:99"
        else
            gate "pytest tests/visual/ -m visual" "desktop.yml:visual" \
                py pytest tests/visual/ --tb=short -q -m visual
        fi
    fi
fi

# ── Android gates (mirror: android.yml `unit-tests`, `lint`) ──────
if $run_android; then
    cd "$ROOT_DIR/android" || exit 1

    if [ ! -f "./gradlew" ]; then
        section "Android"
        skip "Android gates" "" "gradlew not found in android/"
    else
        chmod +x ./gradlew 2>/dev/null || true

        section "Android unit tests (JVM)"
        gate "gradlew testDebugUnitTest" "android.yml:unit-tests" \
            ./gradlew testDebugUnitTest --stacktrace

        # ── androidTest compilation ───────────────────────────────
        # THIS IS THE GATE THAT USED TO ONLY EXIST IN CI.
        # `testDebugUnitTest` and `lintDebug` never compile src/androidTest, so
        # an unresolved reference there (e.g. `hasTag` instead of `hasTestTag`)
        # survives every cheap gate and only fails inside the emulator job —
        # where it surfaces as a bare "process '/usr/bin/sh' failed with exit
        # code 1". Compiling the test APKs here validates the same Kotlin +
        # KSP/Hilt sources in ~2 minutes with no emulator.
        # Ordered AFTER the unit tests so a test-source compile break never
        # masks unit-test results.
        section "Android instrumented-test compilation"
        gate "gradlew assembleDebugAndroidTest (:app, :phototok)" "android.yml:unit-tests" \
            ./gradlew :app:assembleDebugAndroidTest :phototok:assembleDebugAndroidTest --stacktrace

        # ── Android lint (advisory) ───────────────────────────────
        # Deliberately non-blocking: the CI job is `continue-on-error: true`
        # and both modules set `abortOnError = false`. Mirrored as advisory so
        # local and CI agree; the report is still printed for review.
        section "Android lint (advisory)"
        if $quick; then
            skip "android lint" "" "--quick"
        elif ./gradlew :app:lintDebug :phototok:lintDebug --continue --stacktrace; then
            pass "gradlew lintDebug (advisory)" "android.yml:lint"
        else
            warn "gradlew lintDebug (advisory — does not fail CI)" "android.yml:lint"
        fi
    fi
fi

# ── Android instrumented tests (mirror: android.yml `instrumented-tests`) ──
# These require a real Android runtime. They are the ONLY gate that cannot be
# satisfied without an emulator or device — see docs/CI_PARITY.md for why the
# Compose semantics-tree assertions in them genuinely need one.
if $run_android_device; then
    section "Android instrumented tests (device/emulator)"
    cd "$ROOT_DIR/android" || exit 1

    if ! have adb; then
        skip "instrumented tests" "" "adb not found — install Android SDK platform-tools"
    else
        DEVICE_COUNT=$(adb devices | awk 'NR>1 && $2=="device"' | wc -l | tr -d ' ')
        if [ "${DEVICE_COUNT:-0}" -lt 1 ]; then
            skip "instrumented tests" "" "no device/emulator attached — see docs/CI_PARITY.md for the emulator start command"
        else
            chmod +x ./gradlew 2>/dev/null || true
            gate "gradlew connectedDebugAndroidTest" "android.yml:instrumented-tests" \
                ./gradlew :app:connectedDebugAndroidTest :phototok:connectedDebugAndroidTest --stacktrace
        fi
    fi
fi

# ── Summary ───────────────────────────────────────────────────────
echo ""
echo -e "${BLUE}━━━ Summary (gate → CI equivalent) ━━━${NC}"
while IFS='|' read -r status name ci_ref; do
    [ -z "${status:-}" ] && continue
    case "$status" in
        PASS) printf "${GREEN}  ✔ %-52s${NC} ${DIM}%s${NC}\n" "$name" "$ci_ref" ;;
        FAIL) printf "${RED}  ✘ %-52s${NC} ${DIM}%s${NC}\n" "$name" "$ci_ref" ;;
        WARN) printf "${YELLOW}  ! %-52s${NC} ${DIM}%s${NC}\n" "$name" "$ci_ref" ;;
        SKIP) printf "${YELLOW}  ⊘ %-52s${NC} ${DIM}%s${NC}\n" "$name" "$ci_ref" ;;
    esac
done < "$RESULTS_FILE"

echo ""
if [ $EXIT_CODE -ne 0 ]; then
    echo -e "${RED}FAILED — one or more gates failed. CI will fail too.${NC}"
    exit 1
fi

if $SKIPPED_ANY; then
    echo -e "${YELLOW}PASSED WITH SKIPS — some gates could not run on this machine.${NC}"
    echo -e "${DIM}CI will still run them. Review the ⊘ lines above before assuming green.${NC}"
    if $strict; then
        echo -e "${RED}--strict: treating skipped gates as failure.${NC}"
        exit 2
    fi
    exit 0
fi

echo -e "${GREEN}ALL GATES PASSED — this machine reproduced the full CI gate set.${NC}"
exit 0
