import Foundation

/// Photographic sharpness classification category based on standardized thresholds.
public enum SharpnessCategory: String, Sendable, Codable, CaseIterable {
    case blurry = "Blurry"         // score < 35.0
    case acceptable = "Acceptable" // 35.0 <= score < 70.0
    case sharp = "Sharp"           // score >= 70.0

    public static func from(score: Double) -> SharpnessCategory {
        if score < 35.0 {
            return .blurry
        } else if score < 70.0 {
            return .acceptable
        } else {
            return .sharp
        }
    }
}

/// Photographic quality scores encompassing focus, exposure clipping, noise, and aesthetics.
public struct QualityScores: Sendable, Codable, Equatable, Hashable {
    /// Sharpness score in range 0.0 ... 100.0.
    public let sharpness: Double

    /// Sensor noise metric in range 0.0 ... 100.0.
    public let noise: Double

    /// Highlight clipping percentage in range 0.0 ... 100.0 (pixels >= 254).
    public let highlightClipping: Double

    /// Shadow clipping percentage in range 0.0 ... 100.0 (pixels <= 2).
    public let shadowClipping: Double

    /// Normalized aesthetic score in range 1.0 ... 10.0 (nil if not evaluated).
    public let aesthetic: Double?

    /// Flag indicating utility content (e.g. screenshot, document, receipt).
    public let isUtility: Bool

    /// Focus categorization derived from sharpness score.
    public let focusCategory: SharpnessCategory

    public init(
        sharpness: Double,
        noise: Double,
        highlightClipping: Double,
        shadowClipping: Double,
        aesthetic: Double? = nil,
        isUtility: Bool = false,
        focusCategory: SharpnessCategory? = nil
    ) {
        self.sharpness = sharpness
        self.noise = noise
        self.highlightClipping = highlightClipping
        self.shadowClipping = shadowClipping
        self.aesthetic = aesthetic
        self.isUtility = isUtility
        self.focusCategory = focusCategory ?? SharpnessCategory.from(score: sharpness)
    }
}
