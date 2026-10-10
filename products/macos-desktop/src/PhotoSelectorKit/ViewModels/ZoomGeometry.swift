import CoreGraphics

/// Pure zoom arithmetic shared by every image viewport.
public enum ZoomGeometry {

    /// Aspect-fit size of `content` inside `viewport`.
    public static func fittedSize(content: CGSize, in viewport: CGSize) -> CGSize {
        guard content.width > 0, content.height > 0, viewport.width > 0, viewport.height > 0 else {
            return .zero
        }
        let scale = min(viewport.width / content.width, viewport.height / content.height)
        return CGSize(width: content.width * scale, height: content.height * scale)
    }

    /// Magnification (relative to aspect-fit) at which one **original** image pixel maps to
    /// one device pixel — an honest "100 %". Never below 1: a photo smaller than the viewport
    /// is already shown at (or above) 100 % when fitted.
    public static func oneToOneScale(
        originalPixelSize: CGSize,
        viewport: CGSize,
        backingScale: CGFloat
    ) -> CGFloat {
        let fitted = fittedSize(content: originalPixelSize, in: viewport)
        guard fitted.width > 0, backingScale > 0 else { return 1 }
        return max(1, originalPixelSize.width / (fitted.width * backingScale))
    }

    /// Effective magnification for a zoom state.
    public static func scale(
        for state: ZoomState,
        originalPixelSize: CGSize?,
        viewport: CGSize,
        backingScale: CGFloat
    ) -> CGFloat {
        switch state {
        case .fit:
            return 1
        case .magnified(let factor):
            return max(1, CGFloat(factor))
        case .oneToOne:
            guard let originalPixelSize else { return 1 }
            return oneToOneScale(originalPixelSize: originalPixelSize, viewport: viewport, backingScale: backingScale)
        }
    }

    /// Clamps a pan offset so the magnified image always covers the viewport (no panning the
    /// photo out of view, which previously made zoom feel like a trap).
    public static func clampedPan(
        _ offset: CGSize,
        scale: CGFloat,
        fittedSize: CGSize,
        viewport: CGSize
    ) -> CGSize {
        let maxX = max(0, (fittedSize.width * scale - viewport.width) / 2)
        let maxY = max(0, (fittedSize.height * scale - viewport.height) / 2)
        return CGSize(
            width: min(max(offset.width, -maxX), maxX),
            height: min(max(offset.height, -maxY), maxY)
        )
    }
}
