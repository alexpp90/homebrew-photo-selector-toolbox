import Foundation
import CoreGraphics
import Accelerate

/// Unified photographic quality scoring pipeline: computes sharpness (Accelerate Laplacian),
/// exposure clipping (Accelerate 256-bin histogram), and aesthetics (Apple Vision).
public final class QualityScoringService: Sendable {

    /// Asynchronously analyzes a rendered `CGImage` across all optical quality dimensions.
    /// Executes on a background cooperative thread without blocking MainActor or UI rendering.
    public static func score(cgImage: CGImage) async -> QualityScores {
        let focusResult = FocusMetricService.evaluateSync(cgImage: cgImage)
        let exposureResult = ExposureMetricService.evaluateSync(cgImage: cgImage)
        let aestheticsResult = try? await VisionAestheticsService.shared.evaluate(cgImage: cgImage)

        let sharpness = focusResult?.score ?? 0.0
        let noise = focusResult?.estimatedNoiseSigma ?? 0.0
        let hl = exposureResult?.highlightClippingPercentage ?? 0.0
        let sh = exposureResult?.shadowClippingPercentage ?? 0.0
        let aesthetic = aestheticsResult?.mappedScore
        let isUtility = aestheticsResult?.isUtility ?? false
        let category = focusResult?.category ?? SharpnessCategory.from(score: sharpness)

        return QualityScores(
            sharpness: sharpness,
            noise: noise,
            highlightClipping: hl,
            shadowClipping: sh,
            aesthetic: aesthetic,
            isUtility: isUtility,
            focusCategory: category
        )
    }
}
