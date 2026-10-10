import Foundation

/// Represents the holistic scan and analysis result for a single photo file.
public struct ScanResult: Sendable, Codable, Equatable, Hashable {
    public let path: URL
    public let scores: QualityScores
    public let exif: ExifData?

    public init(
        path: URL,
        scores: QualityScores,
        exif: ExifData? = nil
    ) {
        self.path = path
        self.scores = scores
        self.exif = exif
    }
}
