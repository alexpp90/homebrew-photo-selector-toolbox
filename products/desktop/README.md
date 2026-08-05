# Desktop

Python + Tkinter culling suite for macOS, Linux and Windows. The most complete of the three
products: it has the CLI, the statistics plots, the duplicate finder and the only local-VLM
aesthetic scoring.

```
products/desktop/
  src/photo_selector_toolbox/   core/ exif/ tools/ gui/ cli.py
  tests/                        conftest.py, unit/{core,exif,tools,gui}/, visual/
  benchmarks/
  scripts/                      build.py, generate_icons.py, generate_notices.py,
                                update_formula.py, install-{mac,linux}.sh
  pyproject.toml  poetry.lock  .flake8
```

Package layout and the layering rules: [`../../docs/products/desktop/ARCHITECTURE.md`](../../docs/products/desktop/ARCHITECTURE.md).
Behaviour specification: [`../../docs/products/desktop/REQUIREMENTS.md`](../../docs/products/desktop/REQUIREMENTS.md).

## Run

```bash
cd products/desktop
poetry install
poetry run photo-selector-gui          # GUI
poetry run photo-selector-toolbox ...  # CLI
```

## Test

```bash
./scripts/run_tests.sh                 # from the repository root — the CI mirror, use this
cd products/desktop && poetry run pytest   # subset only; not sufficient evidence
```

GUI tests need a display; on headless Linux use `xvfb-run`. Visual regression tests are marked
`visual` and are Linux-only — see [`../../docs/build/CI_PARITY.md`](../../docs/build/CI_PARITY.md).

## Things that deliberately live elsewhere

- **`Formula/` and `Casks/` are at the repository root**, not here. This repository is a
  Homebrew tap and `brew tap` only looks for formulae at the tap root; moving them would break
  installation for existing users. `scripts/update_formula.py` here is what edits them.
- **`assets/` is at the repository root** — the logo and screenshots are shared with the
  top-level README, and `scripts/generate_icons.py` reads them from there.
