import Foundation
import Vision
import CoreGraphics

/// Result of Apple Vision aesthetic analysis.
public struct AestheticEvaluation: Sendable, Equatable, Hashable {
    /// Public overall aesthetic score in range -1.0 ... 1.0.
    public let overallScore: Float

    /// Indicates whether the image is utility content (screenshot, document, receipt).
    public let isUtility: Bool

    /// Raw aesthetic score in range 0.0 ... 1.0 (if available via KVC).
    public let aestheticScore: Float?

    /// Failure / technical flaw score in range 0.0 ... 1.0 (if available via KVC).
    public let failureScore: Float?

    /// Linearly mapped score in canonical photographic range 1.0 ... 10.0.
    public let mappedScore: Double

    public init(
        overallScore: Float,
        isUtility: Bool,
        aestheticScore: Float? = nil,
        failureScore: Float? = nil,
        mappedScore: Double? = nil
    ) {
        self.overallScore = overallScore
        self.isUtility = isUtility
        self.aestheticScore = aestheticScore
        self.failureScore = failureScore
        if let mappedScore {
            self.mappedScore = mappedScore
        } else {
            let normalized = (Double(overallScore) + 1.0) / 2.0
            self.mappedScore = min(10.0, max(1.0, ((1.0 + normalized * 9.0) * 10.0).rounded() / 10.0))
        }
    }
}

/// Errors occurring during aesthetic evaluation.
public enum VisionAestheticsError: Error, LocalizedError, Sendable, Equatable {
    case unsupportedOS
    case noObservation
    case unreadableImage(URL)
    case invalidImageBuffer
    case underlyingError(String)

    public var errorDescription: String? {
        switch self {
        case .unsupportedOS:
            return "Vision aesthetic scoring requires macOS 15.0 or later."
        case .noObservation:
            return "Vision request produced no observation results."
        case .unreadableImage(let url):
            return "Unable to read image at URL: \(url.path)."
        case .invalidImageBuffer:
            return "Invalid CGImage buffer provided."
        case .underlyingError(let message):
            return "Vision processing error: \(message)"
        }
    }
}

/// Pure native on-device aesthetic scoring service using Apple Vision framework.
public actor VisionAestheticsService {

    public static let shared = VisionAestheticsService()

    public init() {}

    /// Evaluates the aesthetic quality of an image file at the given URL.
    /// - Parameter url: The file URL of the image to evaluate.
    /// - Returns: An `AestheticEvaluation` containing raw and mapped scores.
    public func evaluate(url: URL) throws -> AestheticEvaluation {
        guard #available(macOS 15.0, *) else {
            throw VisionAestheticsError.unsupportedOS
        }

        guard FileManager.default.fileExists(atPath: url.path) else {
            throw VisionAestheticsError.unreadableImage(url)
        }

        let request = VNCalculateImageAestheticsScoresRequest()
        let handler = VNImageRequestHandler(url: url, options: [:])

        do {
            try handler.perform([request])
        } catch {
            throw VisionAestheticsError.underlyingError(error.localizedDescription)
        }

        guard let obs = request.results?.first else {
            throw VisionAestheticsError.noObservation
        }

        let aestheticScore = obs.value(forKey: "aestheticScore") as? Float
        let failureScore = obs.value(forKey: "failureScore") as? Float

        return AestheticEvaluation(
            overallScore: obs.overallScore,
            isUtility: obs.isUtility,
            aestheticScore: aestheticScore,
            failureScore: failureScore
        )
    }

    /// Evaluates the aesthetic quality of an in-memory CGImage.
    /// - Parameter cgImage: The image to evaluate.
    /// - Returns: An `AestheticEvaluation` containing raw and mapped scores.
    public func evaluate(cgImage: CGImage) throws -> AestheticEvaluation {
        guard #available(macOS 15.0, *) else {
            throw VisionAestheticsError.unsupportedOS
        }

        guard cgImage.width > 0, cgImage.height > 0 else {
            throw VisionAestheticsError.invalidImageBuffer
        }

        let request = VNCalculateImageAestheticsScoresRequest()
        let handler = VNImageRequestHandler(cgImage: cgImage, options: [:])

        do {
            try handler.perform([request])
        } catch {
            throw VisionAestheticsError.underlyingError(error.localizedDescription)
        }

        guard let obs = request.results?.first else {
            throw VisionAestheticsError.noObservation
        }

        let aestheticScore = obs.value(forKey: "aestheticScore") as? Float
        let failureScore = obs.value(forKey: "failureScore") as? Float

        return AestheticEvaluation(
            overallScore: obs.overallScore,
            isUtility: obs.isUtility,
            aestheticScore: aestheticScore,
            failureScore: failureScore
        )
    }
}

/// Convenience alias for VisionAestheticsService.
public typealias VisionScorer = VisionAestheticsService
