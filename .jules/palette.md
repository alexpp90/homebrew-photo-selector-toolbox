## 2026-10-08 - Added keyboard focus support to Tooltips
**Learning:** Tooltips triggered only by mouse hover (<Enter>/<Leave>) are inaccessible to keyboard-only users who navigate via the Tab key.
**Action:** Always bind <FocusIn> and <FocusOut> events alongside hover events to ensure custom interactive elements like tooltips are keyboard accessible.
