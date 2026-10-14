import CoreGraphics

/// Geometry of the single-photo view: the metadata panel **reserves its own space** and the
/// image viewport takes whatever is left — metadata never covers the photograph
/// (REQ-MAC-LAYOUT.05).
///
/// Which constraint binds decides the arrangement. A landscape photo in a wide canvas is
/// height-bound, so a bottom strip would cost it height while a side column costs nothing
/// it could use; a portrait photo is width-bound and prefers the side column. This solver
/// compares the aspect-fitted photo area for both arrangements and returns the winner; the
/// view and the UI self-test consume the rectangles, never re-derive them.
public struct MetadataPanelLayout: Equatable, Sendable {

    public enum Placement: Equatable, Sendable {
        case hidden
        case bottomStrip
        case sideColumn
    }

    /// Preferred thickness of the bottom strip (two text rows).
    public static let stripHeight: CGFloat = 64
    /// Preferred width of the side column.
    public static let sideWidth: CGFloat = 244
    /// The side column must beat the strip by this factor to be chosen, so the arrangement
    /// does not flip between near-equal photos while browsing.
    public static let sideColumnAdvantage: CGFloat = 1.05
    /// Aspect ratio assumed until the real one is known (3:2 landscape).
    public static let defaultAspectRatio: CGFloat = 1.5

    public let placement: Placement
    /// Rectangle (canvas coordinates) handed to the image viewport.
    public let image: CGRect
    /// Rectangle of the metadata panel; `.zero` only when hidden.
    public let panel: CGRect

    /// - Parameters:
    ///   - canvas: total area available for photo + panel.
    ///   - aspectRatio: width / height of the displayed photo, if known.
    ///   - isPanelVisible: the user's Info toggle (`I`).
    public init(canvas: CGSize, aspectRatio: CGFloat?, isPanelVisible: Bool) {
        let width = max(0, canvas.width)
        let height = max(0, canvas.height)
        let full = CGRect(x: 0, y: 0, width: width, height: height)

        guard isPanelVisible, width > 0, height > 0 else {
            self.placement = .hidden
            self.image = full
            self.panel = .zero
            return
        }

        // Floors/ceilings: the panel never vanishes (it holds the EXIF the user asked for)
        // and never eats more than a quarter / third of a small window.
        let strip = min(Self.stripHeight, height * 0.25)
        let side = min(Self.sideWidth, width * 0.33)
        let ratio = (aspectRatio ?? Self.defaultAspectRatio) > 0 ? (aspectRatio ?? Self.defaultAspectRatio) : Self.defaultAspectRatio

        let stripImage = CGSize(width: width, height: height - strip)
        let sideImage = CGSize(width: width - side, height: height)
        let stripArea = Self.fittedArea(aspectRatio: ratio, in: stripImage)
        let sideArea = Self.fittedArea(aspectRatio: ratio, in: sideImage)

        if sideArea > stripArea * Self.sideColumnAdvantage {
            self.placement = .sideColumn
            self.image = CGRect(x: 0, y: 0, width: sideImage.width, height: sideImage.height)
            self.panel = CGRect(x: sideImage.width, y: 0, width: side, height: height)
        } else {
            self.placement = .bottomStrip
            self.image = CGRect(x: 0, y: 0, width: stripImage.width, height: stripImage.height)
            self.panel = CGRect(x: 0, y: stripImage.height, width: width, height: strip)
        }
    }

    static func fittedArea(aspectRatio: CGFloat, in size: CGSize) -> CGFloat {
        let fitted = ZoomGeometry.fittedSize(content: CGSize(width: aspectRatio, height: 1), in: size)
        return fitted.width * fitted.height
    }
}
