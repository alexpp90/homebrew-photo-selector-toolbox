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
#   ./scripts/run_tests.sh                  # auto-detects changed products (mirrors CI paths: filters)
#   ./scripts/run_tests.sh --python         # Python gates only (flake8 + pytest + coverage)
#   ./scripts/run_tests.sh --android        # Android gates that need no device
#   ./scripts/run_tests.sh --android-device # Android instrumented tests (needs emulator/device)
#   ./scripts/run_tests.sh --macos          # macOS Desktop native Swift gates (Darwin only)
#   ./scripts/run_tests.sh --linux          # Linux Desktop native gates (Python/GTK4 + Debian packaging)
#   ./scripts/run_tests.sh --all            # all products unconditionally
#   ./scripts/run_tests.sh --strict         # treat unrunnable SKIPPED gates as failures
#   ./scripts/run_tests.sh --quick          # tests only, no lint/coverage (inner-loop use)
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

have() { command -v "$1" &>/dev/null; }

run_python=false
run_android=false
run_android_device=false
run_macos=false
run_linux=false
explicit_product=false
strict=false
quick=false

for arg in "$@"; do
    case "$arg" in
        --python)         run_python=true; explicit_product=true ;;
        --android|--android-unit) run_android=true; explicit_product=true ;;
        --android-device) run_android_device=true; explicit_product=true ;;
        --macos)          run_macos=true; explicit_product=true ;;
        --linux)          run_linux=true; explicit_product=true ;;
        --strict)         strict=true ;;
        --quick)          quick=true ;;
        --all)
            run_python=true
            run_android=true
            run_android_device=true
            run_macos=true
            run_linux=true
            explicit_product=true
            ;;
        -h|--help)
            sed -n '2,25p' "$0" | sed 's/^# \{0,1\}//'
            exit 0
            ;;
        *)
            echo -e "${RED}Unknown argument: $arg${NC}"
            exit 1
            ;;
    esac
done

# If no product was explicitly requested, mirror CI paths: filters via git change detection
if ! $explicit_product; then
    CHANGED_FILES=()
    if have git && [ -d "$ROOT_DIR/.git" ]; then
        BASE_COMMIT=""
        if git rev-parse --verify origin/main &>/dev/null; then
            BASE_COMMIT="$(git merge-base HEAD origin/main 2>/dev/null || true)"
        elif git rev-parse --verify main &>/dev/null; then
            BASE_COMMIT="$(git merge-base HEAD main 2>/dev/null || true)"
        fi

        if [ -n "$BASE_COMMIT" ]; then
            while IFS= read -r f; do [ -n "$f" ] && CHANGED_FILES+=("$f"); done < <(git diff --name-only "$BASE_COMMIT..HEAD" 2>/dev/null || true)
        fi
        while IFS= read -r f; do [ -n "$f" ] && CHANGED_FILES+=("$f"); done < <(git diff --name-only HEAD 2>/dev/null || true)
        while IFS= read -r f; do [ -n "$f" ] && CHANGED_FILES+=("$f"); done < <(git ls-files --others --exclude-standard "$ROOT_DIR" 2>/dev/null || true)
    fi

    if [ ${#CHANGED_FILES[@]} -eq 0 ]; then
        # Pristine working tree: test all products by default
        run_python=true
        run_android=true
        if [ "$(uname -s)" = "Darwin" ]; then
            run_macos=true
        fi
        run_linux=true
    else
        has_desktop=false
        has_android=false
        has_macos=false
        has_linux=false
        has_cross=false

        for f in "${CHANGED_FILES[@]}"; do
            case "$f" in
                products/desktop/*|Formula/*|Casks/*|*.spec) has_desktop=true ;;
                products/android/*) has_android=true ;;
                products/macos-desktop/*) has_macos=true ;;
                products/linux-desktop/*) has_linux=true ;;
                .github/workflows/*|scripts/run_tests.sh) has_cross=true ;;
            esac
        done

        if $has_cross; then
            run_python=true
            run_android=true
            if [ "$(uname -s)" = "Darwin" ]; then run_macos=true; fi
            run_linux=true
        else
            if $has_desktop; then run_python=true; fi
            if $has_android; then run_android=true; fi
            if $has_macos; then run_macos=true; fi
            if $has_linux; then run_linux=true; fi
        fi
    fi
fi

EXIT_CODE=0
SKIPPED_ANY=false
SKIPPED_UNRUNNABLE=false
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
skip() {
    local name="$1"
    local ci_ref="$2"
    local reason="$3"
    local unrunnable="${4:-true}"
    record SKIP "$name" "$reason"
    SKIPPED_ANY=true
    if [ "$unrunnable" = "true" ]; then
        SKIPPED_UNRUNNABLE=true
    fi
    echo -e "${YELLOW}  ⊘ $name — $reason${NC}"
}

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

# Prefer poetry when a lockfile-managed venv exists, else fall back to bare tools.
PY_RUNNER=()
VENV_BIN=""
if have poetry && [ -f "$ROOT_DIR/products/desktop/poetry.lock" ]; then
    PY_RUNNER=(poetry run)
    VENV_BIN="$(poetry --directory "$ROOT_DIR/products/desktop" env info -p 2>/dev/null)/bin"
fi
py() {
    if [ -n "$VENV_BIN" ] && [ -x "$VENV_BIN/$1" ]; then
        "$VENV_BIN/$@"
    elif [ ${#PY_RUNNER[@]} -gt 0 ]; then
        "${PY_RUNNER[@]}" "$@"
    else
        "$@"
    fi
}

# ── Python gates (mirror: desktop.yml `lint`, `test`, `visual`) ────
if $run_python; then
    cd "$ROOT_DIR/products/desktop" || exit 1

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
elif ! $explicit_product; then
    section "Python gates (skipped)"
    skip "Python gates" "desktop.yml" "no changes under products/desktop/ (use --python or --all to force)" false
fi

# ── Android gates (mirror: android.yml `unit-tests`, `lint`) ──────
if $run_android; then
    cd "$ROOT_DIR/products/android" || exit 1

    if [ ! -f "./gradlew" ]; then
        section "Android"
        skip "Android gates" "" "gradlew not found in products/android/"
    else
        chmod +x ./gradlew 2>/dev/null || true

        section "Android unit tests (JVM)"
        gate "gradlew testDebugUnitTest" "android.yml:unit-tests" \
            ./gradlew testDebugUnitTest --stacktrace

        # ── androidTest compilation ───────────────────────────────
        # THIS IS THE GATE THAT USED TO ONLY EXIST IN CI.
        # `testDebugUnitTest` and `lintDebug` never compile tests/instrumented, so
        # an unresolved reference there (e.g. `hasTag` instead of `hasTestTag`)
        # survives every cheap gate and only fails inside the emulator job —
        # where it surfaces as a bare "process '/usr/bin/sh' failed with exit
        # code 1". Compiling the test APKs here validates the same Kotlin +
        # KSP/Hilt sources in ~2 minutes with no emulator.
        # Ordered AFTER the unit tests so a test-source compile break never
        # masks unit-test results.
        section "Android instrumented-test compilation"
        gate "gradlew assembleDebugAndroidTest (:android-desktop, :phototok)" "android.yml:unit-tests" \
            ./gradlew :android-desktop:assembleDebugAndroidTest :phototok:assembleDebugAndroidTest --stacktrace

        # ── Android lint (advisory) ───────────────────────────────
        # Deliberately non-blocking: the CI job is `continue-on-error: true`
        # and both modules set `abortOnError = false`. Mirrored as advisory so
        # local and CI agree; the report is still printed for review.
        section "Android lint (advisory)"
        if $quick; then
            skip "android lint" "" "--quick"
        elif ./gradlew :android-desktop:lintDebug :phototok:lintDebug --continue --stacktrace; then
            pass "gradlew lintDebug (advisory)" "android.yml:lint"
        else
            warn "gradlew lintDebug (advisory — does not fail CI)" "android.yml:lint"
        fi
    fi
elif ! $explicit_product; then
    section "Android gates (skipped)"
    skip "Android gates" "android.yml" "no changes under products/android/ (use --android or --all to force)" false
fi

# ── Android instrumented tests (mirror: android.yml `instrumented-tests`) ──
# These require a real Android runtime. They are the ONLY gate that cannot be
# satisfied without an emulator or device — see docs/build/CI_PARITY.md for why the
# Compose semantics-tree assertions in them genuinely need one.
if $run_android_device; then
    section "Android instrumented tests (device/emulator)"
    cd "$ROOT_DIR/products/android" || exit 1

    if ! have adb; then
        skip "instrumented tests" "" "adb not found — install Android SDK platform-tools"
    else
        DEVICE_COUNT=$(adb devices | awk 'NR>1 && $2=="device"' | wc -l | tr -d ' ')
        if [ "${DEVICE_COUNT:-0}" -lt 1 ]; then
            skip "instrumented tests" "" "no device/emulator attached — see docs/build/CI_PARITY.md for the emulator start command"
        else
            chmod +x ./gradlew 2>/dev/null || true
            gate "gradlew connectedDebugAndroidTest" "android.yml:instrumented-tests" \
                ./gradlew :android-desktop:connectedDebugAndroidTest :phototok:connectedDebugAndroidTest --stacktrace
        fi
    fi
fi

# ── macOS Desktop native Swift gates (mirror: macos.yml `swift-test`) ──
if $run_macos; then
    section "macOS Desktop native Swift tests"
    cd "$ROOT_DIR" || exit 1
    if [ "$(uname -s)" != "Darwin" ]; then
        skip "swift test (macos-desktop)" "macos.yml:swift-test" "macOS native product requires Darwin runner" false
    elif ! have swift; then
        skip "swift test (macos-desktop)" "macos.yml:swift-test" "swift toolchain not found"
    else
        gate "swift test (products/macos-desktop)" "macos.yml:swift-test" \
            swift test -c release --package-path "$ROOT_DIR/products/macos-desktop"

        # Local-only gate: drives the RUNNING app (activation policy, real key events,
        # rendered Focus 3-Up geometry, clicks while zoomed, time-to-first-photo). Unit
        # tests alone passed while arrow keys were dead in the real app.
        # See docs/build/CI_PARITY.md § macOS UI smoke test.
        if $quick; then
            skip "macOS UI smoke test" "local-only" "--quick skips the GUI smoke test" false
        else
            "$ROOT_DIR/products/macos-desktop/scripts/ui_smoke_test.sh"
            smoke_status=$?
            if [ "$smoke_status" -eq 0 ]; then
                pass "macOS UI smoke test (running app)" "local-only"
            elif [ "$smoke_status" -eq 3 ]; then
                skip "macOS UI smoke test" "local-only" "no GUI (Aqua) session — log in to the desktop to run it"
            else
                fail "macOS UI smoke test (running app)" "local-only"
            fi
        fi
    fi
elif ! $explicit_product; then
    section "macOS Desktop gates (skipped)"
    skip "macOS Desktop gates" "macos.yml" "no changes under products/macos-desktop/ (use --macos or --all to force)" false
fi

# ── Linux Desktop gates (mirror: linux.yml `lint`, `test`, `package`) ──
if $run_linux; then
    section "Linux Desktop gates"
    if [ ! -d "$ROOT_DIR/products/linux-desktop" ]; then
        skip "Linux Desktop gates" "linux.yml" "products/linux-desktop not yet created (planned for M3)" false
    else
        cd "$ROOT_DIR/products/linux-desktop" || exit 1

        # 1. Lint / Static analysis
        if [ -d "$ROOT_DIR/products/linux-desktop/src" ]; then
            section "Linux Desktop lint (flake8)"
            if $quick; then
                skip "flake8 (linux-desktop)" "linux.yml:lint" "--quick"
            elif py flake8 --version &>/dev/null; then
                gate "flake8 (products/linux-desktop)" "linux.yml:lint" \
                    py flake8 src/ tests/ --count --show-source --statistics
            elif have flake8; then
                gate "flake8 (products/linux-desktop)" "linux.yml:lint" \
                    flake8 src/ tests/ --count --show-source --statistics
            else
                skip "flake8 (linux-desktop)" "linux.yml:lint" "flake8 not installed in the active environment"
            fi
        fi

        # 2. Unit tests (pytest)
        section "Linux Desktop unit tests"
        if [ -d "$ROOT_DIR/products/linux-desktop/tests" ]; then
            if ! have pytest && ! have poetry; then
                skip "pytest (linux-desktop)" "linux.yml:test" "neither poetry nor pytest found"
            elif py pytest --version &>/dev/null; then
                gate "pytest (products/linux-desktop)" "linux.yml:test" \
                    py pytest tests/ --tb=short -q
            elif have pytest; then
                gate "pytest (products/linux-desktop)" "linux.yml:test" \
                    pytest tests/ --tb=short -q
            else
                skip "pytest (linux-desktop)" "linux.yml:test" "pytest runner not found"
            fi
        else
            skip "pytest (linux-desktop)" "linux.yml:test" "tests/ directory not yet created" false
        fi

        # 3. Debian packaging validation
        section "Linux Desktop packaging & APT validation"
        if [ -f "$ROOT_DIR/products/linux-desktop/scripts/verify_package.sh" ]; then
            gate "debian packaging (products/linux-desktop)" "linux.yml:package" \
                ./scripts/verify_package.sh
        else
            skip "debian packaging (linux-desktop)" "linux.yml:package" "packaging scripts not yet created (planned for M4)" false
        fi
    fi
elif ! $explicit_product; then
    section "Linux Desktop gates (skipped)"
    skip "Linux Desktop gates" "linux.yml" "no changes under products/linux-desktop/ (use --linux or --all to force)" false
fi

# ── Agent framework gates (no CI equivalent yet — see docs/build/CI_PARITY.md) ──
# The framework is treated as code: naming, registration, roster and the paths that
# instructions point at must hold, or agents silently follow stale directions.
section "Agent framework"
cd "$ROOT_DIR" || exit 1
if have python3; then
    gate "toolchain sync" "ai/skills/sync-framework" \
        python3 ai/skills/sync-framework/scripts/sync_framework.py --check
    gate "framework validation" "ai/skills/sync-framework" \
        python3 ai/skills/sync-framework/scripts/validate_framework.py
    gate ".gemini/settings.json in sync" "ai/skills/sync-framework" \
        python3 ai/skills/sync-framework/scripts/gen_gemini_settings.py --check
    gate "requirements traceability" "scripts/verify_requirements_traceability.py" \
        python3 scripts/verify_requirements_traceability.py --check --strict
else
    skip "toolchain sync" "" "python3 not found"
    skip "framework validation" "" "python3 not found"
    skip ".gemini/settings.json in sync" "" "python3 not found"
    skip "requirements traceability" "" "python3 not found"
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
    echo -e "${YELLOW}PASSED WITH SKIPS — some gates were not run.${NC}"
    echo -e "${DIM}Review the ⊘ lines above before assuming full verification.${NC}"
    if $strict && $SKIPPED_UNRUNNABLE; then
        echo -e "${RED}--strict: treating unrunnable skipped gates as failure.${NC}"
        exit 2
    fi
    exit 0
fi

echo -e "${GREEN}ALL GATES PASSED — this machine reproduced the full CI gate set.${NC}"
exit 0
