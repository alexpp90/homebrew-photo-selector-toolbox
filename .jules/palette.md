## 2025-02-28 - Hand Cursor Baseline Overwrites
**Learning:** When adding hover properties like `cursor="hand2"` in Tkinter, it does not visually change the static UI rendering. However, running visual tests with `UPDATE_BASELINES=1` in different local/CI environments can capture subtle sub-pixel font rendering differences as new baselines, polluting the diff.
**Action:** Do not run visual regression tests with the `UPDATE_BASELINES=1` flag for non-visual or interaction-only UX changes (like hover cursors or ARIA attributes) to prevent unnecessary baseline overwrites.
