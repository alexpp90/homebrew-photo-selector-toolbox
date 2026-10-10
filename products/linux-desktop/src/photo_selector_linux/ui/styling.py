"""Studio dark CSS styling and visual design tokens for Photo Selector Linux.

Adheres to:
- R-LINUX-UI-02: Studio Dark Scheme & High-Contrast Typography
- R-LINUX-UI-06: 2.5px solid blue active focus border (#3584E4)
"""

STUDIO_DARK_CSS: str = """
/* Photo Selector Studio Dark Scheme */

window, .studio-dark-surface {
    background-color: #141417;
    color: #FFFFFF;
}

/* Card & Comparison Panes */
.card-pane {
    background-color: #1C1C20;
    border: 1px solid #3A3A42;
    border-radius: 8px;
    padding: 4px;
}

/* Center Active Focus Slot Border (2.5px solid #3584E4) */
.accent-border {
    border: 2.5px solid #3584E4;
    border-radius: 8px;
}

/* Boundary Slot Pane (First / Last Photograph) */
.boundary-slot-pane {
    background-color: #1C1C20;
    border: 1px solid #3A3A42;
    border-radius: 8px;
    color: #D9D9D9;
}

.boundary-slot-title {
    color: #FFFFFF;
    font-size: 16px;
    font-weight: bold;
}

.boundary-slot-subtitle {
    color: #D9D9D9;
    font-size: 13px;
}

/* Active Pill Badge */
.active-pill {
    background-color: #3584E4;
    color: #FFFFFF;
    font-weight: bold;
    font-size: 11px;
    border-radius: 10px;
    padding: 2px 8px;
}

/* Photo Counter Pill */
.photo-counter-pill {
    background-color: #1C1C20;
    border: 1px solid #3A3A42;
    color: #D9D9D9;
    border-radius: 12px;
    padding: 4px 12px;
    font-weight: 500;
    font-size: 13px;
}

/* Floating Info HUD Overlay */
.floating-hud {
    background-color: rgba(20, 20, 23, 0.88);
    border: 1px solid #3A3A42;
    border-radius: 6px;
    padding: 8px 12px;
    color: #D9D9D9;
}

.optical-strip-primary {
    color: #FFFFFF;
    font-weight: bold;
    font-size: 14px;
}

.optical-strip-secondary {
    color: #D9D9D9;
    font-size: 12px;
}

/* Quality Score Badges */
.score-badge-sharp {
    background-color: #2EC27E;
    color: #FFFFFF;
    border-radius: 6px;
    padding: 2px 6px;
    font-weight: bold;
    font-size: 11px;
}

.score-badge-acceptable {
    background-color: #E5A50A;
    color: #141417;
    border-radius: 6px;
    padding: 2px 6px;
    font-weight: bold;
    font-size: 11px;
}

.score-badge-blurry {
    background-color: #E01B24;
    color: #FFFFFF;
    border-radius: 6px;
    padding: 2px 6px;
    font-weight: bold;
    font-size: 11px;
}

/* Bottom Action Bar Buttons */
.action-btn-move {
    background-color: #2EC27E;
    color: #FFFFFF;
    font-weight: bold;
    border-radius: 6px;
    padding: 6px 14px;
}

.action-btn-copy {
    background-color: #3584E4;
    color: #FFFFFF;
    font-weight: bold;
    border-radius: 6px;
    padding: 6px 14px;
}

.action-btn-trash {
    background-color: #E01B24;
    color: #FFFFFF;
    font-weight: bold;
    border-radius: 6px;
    padding: 6px 14px;
}

.status-counter-badge {
    background-color: #1C1C20;
    border: 1px solid #3A3A42;
    color: #D9D9D9;
    border-radius: 6px;
    padding: 4px 8px;
    font-size: 12px;
}
"""
