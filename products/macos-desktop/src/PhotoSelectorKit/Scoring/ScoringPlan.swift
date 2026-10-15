import Foundation

/// Decides which scoring stages a displayed photograph still needs. Pure, so the lazy /
/// switchable behaviour (REQ-MAC-SCORE.04) is unit-tested without a Vision model.
public struct ScoringPlan: Equatable, Sendable {

    /// Focus + exposure clipping (milliseconds, Accelerate).
    public let runTechnical: Bool
    /// Apple Vision aesthetics (slow, optional).
    public let runAesthetics: Bool

    public var isEmpty: Bool { !runTechnical && !runAesthetics }

    /// The Vision request waits this long after the photo appears, so photographs the user
    /// flies past while holding an arrow key never start a Vision request.
    public static let aestheticsDebounce: Duration = .milliseconds(180)

    public init(runTechnical: Bool, runAesthetics: Bool) {
        self.runTechnical = runTechnical
        self.runAesthetics = runAesthetics
    }

    /// - Parameters:
    ///   - existing: scores already stored for the photo, if any.
    ///   - aestheticsEnabled: the user's "AI aesthetic score" setting.
    public static func make(existing: QualityScores?, aestheticsEnabled: Bool) -> ScoringPlan {
        guard let existing else {
            return ScoringPlan(runTechnical: true, runAesthetics: aestheticsEnabled)
        }
        return ScoringPlan(runTechnical: false, runAesthetics: aestheticsEnabled && existing.aesthetic == nil)
    }
}
