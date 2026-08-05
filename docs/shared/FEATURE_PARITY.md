# Shared — Feature Parity & Sync Policy

> How a feature added to one product is evaluated for the other two.
> The at-a-glance matrix lives in the root [`README.md`](../../README.md);
> this file holds the policy and the permanent exclusions.

## Feature Sync Policy

When a new feature is added to the desktop application, it must be evaluated for inclusion in the Android app:
*   **Tablet/DeX mode:** Should include the feature if technically feasible on Android.
*   **PhotoTok:** Should include the feature if it works well on small screens; may omit with documented rationale.
*   **Excluded features** (Ollama VLM, CLI, ExifTool, SMB paths) are permanently excluded regardless of desktop changes.
*   Feature sync evaluations are documented in the mapping table below, and the resulting
    behaviour in the owning product's `docs/products/<product>/REQUIREMENTS.md`.

## 5. Feature Mapping: Desktop → Android

| Desktop Feature | Android Tablet/DeX | PhotoTok | Notes |
|----------------|-------------------|---------------|-------|
| Photo Selector (review & cull) | ✅ Full | ✅ Simplified | Phone uses swipe gestures instead of prev/next panels |
| Focus Mode (comparison) | ✅ Side-by-side | ❌ Omitted | Not practical on phone screens; tablet gets swipe-between comparison |
| Fullscreen Viewer | ✅ Full + gestures | ✅ Full + gestures | Pinch-to-zoom, swipe to navigate, double-tap zoom |
| Sharpness Analysis | ✅ Full | ✅ Full | Same algorithm, WorkManager for long scans |
| Noise Analysis | ✅ Full | ✅ Full | |
| Highlight/Shadow Clipping | ✅ Full | ✅ Full | |
| Image Library Statistics | ✅ Full charts | ✅ Simplified charts | Phone shows single-column scrollable charts |
| Duplicate Finder | ✅ Full | ✅ Grid view | Phone uses compact grid with batch select |
| Move/Copy to Selection | ✅ Full | ✅ Full | SAF-based folder selection |
| Collection Sorting (RAW/JPEG) | ✅ Full | ✅ Full | |
| Image Grouping (similarity) | ✅ Full | ✅ Time-only default | Phone defaults to fast time+filename grouping |
| Persistent Score Cache | ✅ Room DB | ✅ Room DB | Same 10,000 MRU limit |
| Local AI (Ollama VLM) | ❌ Excluded | ❌ Excluded | Battery/compute constraints |
| CLI | ❌ N/A | ❌ N/A | Android has no CLI equivalent |
| Homebrew Distribution | ❌ N/A | ❌ N/A | Distributed via APK/Play Store |
| SMB Path Resolution | ❌ Excluded | ❌ Excluded | Android handles network shares via SAF providers |
| ExifTool (bundled) | ❌ Excluded | ❌ Excluded | Replaced by AndroidX ExifInterface |
