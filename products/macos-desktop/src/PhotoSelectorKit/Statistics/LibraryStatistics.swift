import Foundation

/// Comprehensive photographic library metrics aggregate.
public struct LibraryStatistics: Sendable, Equatable {
    // 1. Photo Status Breakdown
    public let totalPhotoCount: Int
    public let candidateCount: Int
    public let selectedCount: Int
    public let copiedCount: Int
    public let trashedCount: Int

    // 2. Storage Footprint & Savings
    public let totalStorageBytes: Int64
    public let selectedStorageBytes: Int64
    public let trashedStorageBytes: Int64 // Recoverable savings from trashed photos

    // 3. Format Breakdown
    public let formatStatistics: [FormatStatistic]

    // 4. Quality & Score Metrics
    public let averageSharpness: Double?
    public let averageAesthetic: Double?
    public let focusDistribution: [SharpnessCategory: Int]
    public let utilityPhotoCount: Int

    // 5. Leaderboard / Top 5
    public let topSharpestPhotos: [PhotoItem]
    public let topAestheticPhotos: [PhotoItem]

    public init(
        totalPhotoCount: Int,
        candidateCount: Int,
        selectedCount: Int,
        copiedCount: Int,
        trashedCount: Int,
        totalStorageBytes: Int64,
        selectedStorageBytes: Int64,
        trashedStorageBytes: Int64,
        formatStatistics: [FormatStatistic],
        averageSharpness: Double?,
        averageAesthetic: Double?,
        focusDistribution: [SharpnessCategory: Int],
        utilityPhotoCount: Int,
        topSharpestPhotos: [PhotoItem],
        topAestheticPhotos: [PhotoItem]
    ) {
        self.totalPhotoCount = totalPhotoCount
        self.candidateCount = candidateCount
        self.selectedCount = selectedCount
        self.copiedCount = copiedCount
        self.trashedCount = trashedCount
        self.totalStorageBytes = totalStorageBytes
        self.selectedStorageBytes = selectedStorageBytes
        self.trashedStorageBytes = trashedStorageBytes
        self.formatStatistics = formatStatistics
        self.averageSharpness = averageSharpness
        self.averageAesthetic = averageAesthetic
        self.focusDistribution = focusDistribution
        self.utilityPhotoCount = utilityPhotoCount
        self.topSharpestPhotos = topSharpestPhotos
        self.topAestheticPhotos = topAestheticPhotos
    }

    /// Empty baseline representation for uninitialized or cleared libraries.
    public static var empty: LibraryStatistics {
        LibraryStatistics(
            totalPhotoCount: 0,
            candidateCount: 0,
            selectedCount: 0,
            copiedCount: 0,
            trashedCount: 0,
            totalStorageBytes: 0,
            selectedStorageBytes: 0,
            trashedStorageBytes: 0,
            formatStatistics: [],
            averageSharpness: nil,
            averageAesthetic: nil,
            focusDistribution: [.blurry: 0, .acceptable: 0, .sharp: 0],
            utilityPhotoCount: 0,
            topSharpestPhotos: [],
            topAestheticPhotos: []
        )
    }

    // Convenience formatting helpers
    public var formattedTotalStorage: String {
        ByteCountFormatter.string(fromByteCount: totalStorageBytes, countStyle: .file)
    }

    public var formattedSelectedStorage: String {
        ByteCountFormatter.string(fromByteCount: selectedStorageBytes, countStyle: .file)
    }

    public var formattedTrashedSavings: String {
        ByteCountFormatter.string(fromByteCount: trashedStorageBytes, countStyle: .file)
    }

    /// Progress percentage of photos inspected/culled (selected + copied + trashed) vs total.
    public var cullingProgressPercentage: Double {
        guard totalPhotoCount > 0 else { return 0.0 }
        let culled = Double(selectedCount + copiedCount + trashedCount)
        return (culled / Double(totalPhotoCount)) * 100.0
    }
}
