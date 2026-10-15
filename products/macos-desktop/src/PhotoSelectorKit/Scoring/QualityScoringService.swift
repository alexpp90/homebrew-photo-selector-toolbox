import Foundation
import CoreGraphics
import Accelerate

/// Evaluates the aesthetic quality of an image. Production uses Apple Vision; tests inject a
/// counting fake to prove the request is never issued when the setting is off.
public typealias AestheticEvaluator = @Sendable (CGImage) async throws -> AestheticEvaluation

/// Unified photographic quality scoring pipeline: computes sharpness (Accelerate Laplacian),
/// exposure clipping (Accelerate 256-bin histogram), and aesthetics (Apple Vision).
///
/// Staged by cost (REQ-MAC-SCORE.04): the Accelerate metrics take milliseconds and are
/// published first; the Vision request is slow (Neural Engine / CPU model load), optional,
/// and runs last — or never when the user switched AI scoring off.
public final class QualityScoringService: Sendable {

    /// Default evaluator: the shared Apple Vision actor.
    public static let visionEvaluator: AestheticEvaluator = { image in
        try await VisionAestheticsService.shared.evaluate(cgImage: image)
    }

    /// Stage 1 — focus and exposure clipping only (`aesthetic == nil`). Synchronous, cheap and
    /// safe to call from any background thread.
    public static func scoreTechnical(cgImage: CGImage) -> QualityScores {
        let focusResult = FocusMetricService.evaluateSync(cgImage: cgImage)
        let exposureResult = ExposureMetricService.evaluateSync(cgImage: cgImage)
        let sharpness = focusResult?.score ?? 0.0
        return QualityScores(
            sharpness: sharpness,
            noise: focusResult?.estimatedNoiseSigma ?? 0.0,
            highlightClipping: exposureResult?.highlightClippingPercentage ?? 0.0,
            shadowClipping: exposureResult?.shadowClippingPercentage ?? 0.0,
            aesthetic: nil,
            isUtility: false,
            focusCategory: focusResult?.category ?? SharpnessCategory.from(score: sharpness)
        )
    }

    /// Stage 2 — the Vision aesthetic evaluation. `nil` on failure or cancellation.
    public static func evaluateAesthetics(
        cgImage: CGImage,
        evaluator: AestheticEvaluator = QualityScoringService.visionEvaluator
    ) async -> AestheticEvaluation? {
        if Task.isCancelled { return nil }
        return try? await evaluator(cgImage)
    }

    /// Asynchronously analyzes a rendered `CGImage` across all optical quality dimensions.
    /// Executes on a background cooperative thread without blocking MainActor or UI rendering.
    /// - Parameter aestheticsEnabled: when `false` the Vision request is never run.
    public static func score(
        cgImage: CGImage,
        aestheticsEnabled: Bool = true,
        evaluator: AestheticEvaluator = QualityScoringService.visionEvaluator
    ) async -> QualityScores {
        let technical = scoreTechnical(cgImage: cgImage)
        guard aestheticsEnabled, !Task.isCancelled else { return technical }
        guard let result = await evaluateAesthetics(cgImage: cgImage, evaluator: evaluator) else {
            return technical
        }
        return technical.withAesthetics(result.mappedScore, isUtility: result.isUtility)
    }
}
