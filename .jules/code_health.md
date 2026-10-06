## 2026-07-27 - Dataclass bundling for UI context parameters
**Learning:** Functions accepting paths, ScanResult, and ExifData can be simplified with an ImageAnalysisContext dataclass while supporting union type checks or duck typing for backward compatibility.
**Action:** Use dataclasses like ImageAnalysisContext when multiple related domain models are passed together to display or overlay functions.
