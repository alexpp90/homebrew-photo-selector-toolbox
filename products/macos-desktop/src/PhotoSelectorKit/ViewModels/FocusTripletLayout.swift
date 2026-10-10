import CoreGraphics

/// Geometry of the **Focus 3-Up** comparison mode: one-over-two.
///
/// ```
/// ┌──────────────────────────────┐
/// │            CURRENT           │  ← full width, `currentRowShare` of the height
/// ├──────────────┬───────────────┤
/// │   PREVIOUS   │     NEXT      │  ← two equal halves of the remaining height
/// └──────────────┴───────────────┘
/// ```
///
/// Most photographs are landscape. Three landscape frames in a row are width-bound and
/// leave the current photo no larger than its neighbours; stacking the current frame on top
/// gives it the full canvas width while the previous/next frames stay readable underneath,
/// in chronological left-to-right order. This is the single source of truth for the
/// arrangement — the SwiftUI view only consumes these rectangles (REQ-MAC-LAYOUT.02).
public struct FocusTripletLayout: Equatable, Sendable {

    /// Share of the usable height given to the current (top) row.
    public static let currentRowShare: CGFloat = 0.6

    public let current: CGRect
    public let previous: CGRect
    public let next: CGRect

    public init(
        canvas: CGSize,
        spacing: CGFloat = 6,
        currentRowShare: CGFloat = FocusTripletLayout.currentRowShare
    ) {
        let width = max(0, canvas.width)
        let usableHeight = max(0, canvas.height - spacing)
        let share = min(max(currentRowShare, 0.5), 0.8)
        let topHeight = (usableHeight * share).rounded(.down)
        let bottomHeight = usableHeight - topHeight
        let halfWidth = max(0, (width - spacing) / 2)
        let bottomY = topHeight + spacing

        self.current = CGRect(x: 0, y: 0, width: width, height: topHeight)
        self.previous = CGRect(x: 0, y: bottomY, width: halfWidth, height: bottomHeight)
        self.next = CGRect(x: halfWidth + spacing, y: bottomY, width: halfWidth, height: bottomHeight)
    }

    /// Largest area an image of `aspectRatio` (width / height) occupies when aspect-fitted
    /// into `rect`.
    public static func fittedArea(aspectRatio: CGFloat, in rect: CGRect) -> CGFloat {
        let size = ZoomGeometry.fittedSize(
            content: CGSize(width: aspectRatio, height: 1),
            in: rect.size
        )
        return size.width * size.height
    }
}
