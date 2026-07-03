---
name: refactoring-guide
description: "Refactoring patterns and conventions for the Photo Selector Toolbox project. Read this before performing any refactoring work."
---

# Refactoring Guide for Photo Selector Toolbox

This skill documents the established patterns and conventions that all agents must follow when refactoring code in this project.

## Pattern 1: Centralized Constants

**Problem:** Constants like RAW file extensions are duplicated across multiple modules.

**Rule:** Define shared constants in ONE canonical location and import everywhere else.

| Constant | Canonical Location | Importers |
|----------|-------------------|-----------|
| `SUPPORTED_EXTENSIONS` | `reader.py` | `duplicates.py`, `gui.py`, `cli.py` |
| `FORCE_EXIFTOOL_EXTENSIONS` | `reader.py` | Internal to `reader.py` |
| RAW extensions for preview/sharpness | Should import from `reader.py` | `utils.py`, `sharpness.py` |

**Anti-pattern (DO NOT):**
```python
# In sharpness.py — duplicating the set
raw_exts = {".arw", ".nef", ".cr2", ...}
```

**Correct pattern:**
```python
# In reader.py — single source of truth
RAW_EXTENSIONS = {".arw", ".nef", ".cr2", ...}

# In sharpness.py — import it
from photo_selector_toolbox.reader import RAW_EXTENSIONS
```

## Pattern 2: Controller/View Separation (GUI)

**Problem:** GUI classes embed business logic (file scanning, metadata extraction, threading orchestration).

**Rule:** GUI classes should be thin views. Business logic goes in controllers.

**Architecture:**
```
View (gui.py / sharpness_gui.py)
  ↓ calls
Controller (controllers.py)
  ↓ calls
Backend (reader.py, analyzer.py, sharpness.py, etc.)
```

**The view should only:**
- Receive user input (button clicks, folder selection)
- Display data (labels, images, plots)
- Schedule `after()` callbacks for thread-safe GUI updates

**The controller should:**
- Manage background threads
- Call backend functions
- Report progress via callbacks
- Handle error recovery

## Pattern 3: Thread Pool Sizing

**Rule:** Use a consistent thread pool sizing formula across all entry points.

```python
import os
max_workers = min(8, (os.cpu_count() or 1) + 4)
```

This caps at 8 threads, which balances I/O performance with system overhead. The CLI may use a higher cap (e.g., 32) since it runs without a GUI consuming resources.

## Pattern 4: Image Loading Safety

**Rule:** All image loading paths must convert to RGB mode to handle 16-bit RAW data.

```python
img = Image.open(path)
img = img.convert("RGB")  # REQUIRED — prevents I;16 crashes in ImageTk
```

This applies to:
- `utils.load_image_preview()`
- Any new image loading functions

## Pattern 5: EXIF Data Contract (Typed Data Model)

**Rule:** The output of `get_exif_data()` must return a typed `ExifData` dataclass instance (defined in `models.py`) rather than a raw dictionary:

```python
@dataclass
class ExifData:
    shutter_speed: Optional[float] = None
    aperture: Optional[float] = None
    focal_length: Optional[float] = None
    focal_length_35mm: Optional[float] = None
    is_fallback: bool = False
    iso: Optional[float] = None
    lens: str = "Unknown"
```

All downstream consumers (GUI, analyzer, visualizer) MUST access EXIF values using typed attribute access on the `ExifData` model (e.g. `exif.shutter_speed` instead of `exif["Shutter Speed"]`).

## Pattern 6: Error Handling in Backend Utilities

**Rule:** Backend utilities that can fail on I/O should raise exceptions, not return boolean status codes.

```python
# CORRECT — let the caller decide how to handle
def move_to_trash(filepath):
    send2trash(str(filepath))  # Raises on failure

# WRONG — swallowing the error
def move_to_trash(filepath):
    try:
        send2trash(str(filepath))
        return True
    except Exception:
        return False
```

The GUI layer catches exceptions and prompts the user for fallback actions.
