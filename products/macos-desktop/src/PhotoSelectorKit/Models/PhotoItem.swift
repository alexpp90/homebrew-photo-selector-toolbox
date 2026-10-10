import Foundation

/// Culling status of a photo item.
public enum PhotoStatus: String, Sendable, Codable, CaseIterable {
    case candidate
    case selected
    case copied
    case trashed
}

/// Represents a photo candidate for inspection, scoring, and culling.
public struct PhotoItem: Identifiable, Sendable, Hashable, Codable, Equatable {
    public let id: UUID
    public let url: URL
    public let filename: String
    public var scores: QualityScores?
    public var exif: ExifData?
    public var status: PhotoStatus

    public init(
        id: UUID = UUID(),
        url: URL,
        scores: QualityScores? = nil,
        exif: ExifData? = nil,
        status: PhotoStatus = .candidate
    ) {
        self.id = id
        self.url = url
        self.filename = url.lastPathComponent
        self.scores = scores
        self.exif = exif
        self.status = status
    }
}
