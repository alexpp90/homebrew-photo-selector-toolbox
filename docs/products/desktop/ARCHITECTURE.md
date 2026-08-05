# Desktop — Architecture

> Product: **Desktop** (`products/desktop/src/photo_selector_toolbox/`, Python + Tkinter).

## Package layout

The package is layered. The layering is enforced by one rule: **dependencies point inwards**,
and `core/`, `exif/` and `tools/` must never import `tkinter`.

```
products/desktop/src/photo_selector_toolbox/
  __init__.py          Version resolution only
  cli.py               Command-line entry point (photo-selector-toolbox)

  core/                Headless domain logic — no tkinter, importable from anywhere
    models.py            ExifData, ScanResult — the data contract
    config.py            Config load/save, recent folders, secure permissions
    cache.py             ScoreCache (persistent score cache, MRU-pruned)
    utils.py             Preview loading, path resolution, SSRF-safe HTTP handlers, aggregation
    formatting.py        Score and metadata formatting for display
    analyzer.py          Metadata aggregation and statistics
    sharpness.py         Sharpness/noise scoring, related-file discovery
    duplicates.py        Content-hash duplicate detection, trash handling
    visualizer.py        Matplotlib plot construction

  exif/                Metadata extraction
    reader.py            Public entry point: get_exif_data, SUPPORTED_EXTENSIONS, RAW_EXTENSIONS
    readers/             Interchangeable backends, registered in preference order
      base.py              Strategy protocol + registry
      exiftool.py          ExifTool (preferred)
      exifread_reader.py   exifread
      pillow.py            Pillow (fallback)

  tools/               Pluggable analysis tools
    registry.py          AnalysisTool protocol, ToolRegistry
    aesthetic.py         Aesthetic scoring engines (NIMA / Apple Vision)
    ollama.py            Ollama VLM aesthetic tool (Desktop-only feature)

  gui/                 Tkinter presentation layer — the only place tkinter appears
    app.py               Main application window (photo-selector-gui entry point)
    sharpness_tool.py    The selector tool: comparison, scanning, actions
    controllers.py       ScanController, ImageCacheManager — keep work off the UI thread
    image_panels.py      Image panel composition
    fullscreen_viewer.py Fullscreen viewer
    widgets.py           Shared dialogs and widget helpers
```

`products/desktop/tests/` mirrors this layout: `products/desktop/tests/unit/core/`, `products/desktop/tests/unit/exif/`, `products/desktop/tests/unit/tools/`, `products/desktop/tests/unit/gui/`, with
cross-cutting tests (`test_cli.py`, `test_init.py`, `test_platform.py`) and `conftest.py` at
the top level and visual regression tests in `products/desktop/tests/visual/`.

## Rules

1. **`core/`, `exif/` and `tools/` never import `tkinter`.** This is what makes them usable
   from the CLI, from benchmarks and from tests without a display. `gui/` may import any of
   them; none of them may import `gui/`.
2. **`core/models.py` is the data contract.** `ExifData` and `ScanResult` are the shapes every
   other layer agrees on — and the shape the Android products mirror in
   `com.photoselector.core.model.ExifData`. Changing a field here is a cross-product change:
   see [`../../shared/ANDROID_PLATFORM.md`](../../shared/ANDROID_PLATFORM.md).
3. **EXIF backends are strategies, not branches.** New metadata sources are added as a reader
   in `exif/readers/` and registered in `exif/readers/__init__.py`, never as an `if` inside
   `reader.py`.
4. **Analysis engines are tools, not branches.** New scoring engines implement the
   `AnalysisTool` protocol and register with `ToolRegistry`.
5. **Long work goes through a controller.** GUI code must not block the UI thread; scanning and
   image loading go through `gui/controllers.py`. Thread-pool sizing conventions are in
   [`../../../ai/skills/refactoring-guide/SKILL.md`](../../../ai/skills/refactoring-guide/SKILL.md).
6. **Constants are centralised.** Extensions, thresholds and theme values live with the module
   that owns them, not inlined at call sites.

## Known structural debt

`gui/sharpness_tool.py` is ~3 300 lines and `gui/app.py` ~1 700 — both are far past the point
where they should be split into feature modules. This overhaul moved them without splitting
them, deliberately: relocating a file is verifiable, carving up a 3 300-line Tkinter class is
not, and mixing the two would make the whole change unreviewable. The split is filed in
[`../../../ai/memory/code_health.md`](../../../ai/memory/code_health.md).
