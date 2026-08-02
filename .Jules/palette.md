
## 2026-08-02 - [Accessibility Role for Compose Settings Items]
**Learning:** In Compose, wrapping row items with appropriate role semantic modifiers (e.g., toggleable/selectable with Role.Switch/Role.RadioButton) alongside mergeDescendants = true on the Row improves screen reader cohesion. When doing so, disable the inner widget's onCheckedChange/onClick callback to avoid duplicate focusable elements.
**Action:** When creating settings lists, enforce merged semantics and toggleable/selectable on the parent instead of plain clickable.
