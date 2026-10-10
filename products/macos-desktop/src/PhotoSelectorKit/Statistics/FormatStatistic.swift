import Foundation

/// Photographic format classification item with counts and storage metrics.
public struct FormatStatistic: Identifiable, Sendable, Equatable, Hashable {
    public var id: String { rawExtension }

    /// Canonical display name (e.g., "Sony ARW", "Canon CR3", "Apple HEIC").
    public let displayName: String

    /// Normalized lowercase file extension without dot (e.g., "arw", "cr3", "jpg").
    public let rawExtension: String

    /// Number of photos in the library matching this format.
    public let count: Int

    /// Total byte footprint of this format across all items.
    public let totalBytes: Int64

    /// Percentage of the total library count represented by this format (0.0 ... 100.0).
    public let percentageOfTotal: Double

    public init(
        displayName: String,
        rawExtension: String,
        count: Int,
        totalBytes: Int64,
        percentageOfTotal: Double
    ) {
        self.displayName = displayName
        self.rawExtension = rawExtension
        self.count = count
        self.totalBytes = totalBytes
        self.percentageOfTotal = percentageOfTotal
    }

    public var formattedStorage: String {
        ByteCountFormatter.string(fromByteCount: totalBytes, countStyle: .file)
    }
}
