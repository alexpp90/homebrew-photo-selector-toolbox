import Testing
import Foundation
@testable import PhotoSelectorKit

@Suite("Library Statistics Aggregation Engine Unit Tests")
struct LibraryStatisticsEngineTests {

    private func createTempFile(name: String, size: Int, in directory: URL) throws -> URL {
        let url = directory.appendingPathComponent(name)
        let data = Data(repeating: 0xEE, count: size)
        try data.write(to: url)
        return url
    }

    @Test("REQ-MAC-STATS.01: Empty Library: returns zero counts, empty breakdown, and nil averages")
    func testEmptyLibraryAggregation() {
        let stats = LibraryStatisticsEngine.calculate(from: [])

        #expect(stats.totalPhotoCount == 0)
        #expect(stats.candidateCount == 0)
        #expect(stats.selectedCount == 0)
        #expect(stats.copiedCount == 0)
        #expect(stats.trashedCount == 0)
        #expect(stats.totalStorageBytes == 0)
        #expect(stats.selectedStorageBytes == 0)
        #expect(stats.trashedStorageBytes == 0)
        #expect(stats.formatStatistics.isEmpty)
        #expect(stats.averageSharpness == nil)
        #expect(stats.averageAesthetic == nil)
        #expect(stats.topSharpestPhotos.isEmpty)
        #expect(stats.topAestheticPhotos.isEmpty)
        #expect(stats.cullingProgressPercentage == 0.0)
    }

    @Test("Status Counts: accurately partitions items into candidate, selected, copied, and trashed")
    func testStatusCountAggregation() {
        let u = URL(fileURLWithPath: "/tmp/sample.jpg")
        let items: [PhotoItem] = [
            PhotoItem(url: u, status: .candidate),
            PhotoItem(url: u, status: .candidate),
            PhotoItem(url: u, status: .selected),
            PhotoItem(url: u, status: .selected),
            PhotoItem(url: u, status: .selected),
            PhotoItem(url: u, status: .copied),
            PhotoItem(url: u, status: .trashed),
            PhotoItem(url: u, status: .trashed)
        ]

        let stats = LibraryStatisticsEngine.calculate(from: items)

        #expect(stats.totalPhotoCount == 8)
        #expect(stats.candidateCount == 2)
        #expect(stats.selectedCount == 3)
        #expect(stats.copiedCount == 1)
        #expect(stats.trashedCount == 2)
        // Culled = 3 + 1 + 2 = 6 out of 8 = 75%
        #expect(stats.cullingProgressPercentage == 75.0)
    }

    @Test("Storage Footprint & Savings: sums on-disk byte sizes and calculates trash savings")
    func testStorageFootprintAndSavings() throws {
        let tempDir = FileManager.default.temporaryDirectory.appendingPathComponent("StatsStorage_\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: tempDir) }

        let candFile = try createTempFile(name: "candidate.jpg", size: 10_000, in: tempDir)
        let selFile = try createTempFile(name: "selected.arw", size: 40_000, in: tempDir)
        let copFile = try createTempFile(name: "copied.cr3", size: 30_000, in: tempDir)
        let trashFile = try createTempFile(name: "trashed.nef", size: 20_000, in: tempDir)

        let items: [PhotoItem] = [
            PhotoItem(url: candFile, status: .candidate),
            PhotoItem(url: selFile, status: .selected),
            PhotoItem(url: copFile, status: .copied),
            PhotoItem(url: trashFile, status: .trashed)
        ]

        let stats = LibraryStatisticsEngine.calculate(from: items)

        #expect(stats.totalStorageBytes == 100_000)
        #expect(stats.selectedStorageBytes == 70_000) // selected (40k) + copied (30k)
        #expect(stats.trashedStorageBytes == 20_000)   // savings from trashed
    }

    @Test("Format Breakdown: normalizes raw extensions, aggregates counts, and sums to 100%")
    func testFormatBreakdownAggregation() {
        let items: [PhotoItem] = [
            PhotoItem(url: URL(fileURLWithPath: "/tmp/p1.ARW")),
            PhotoItem(url: URL(fileURLWithPath: "/tmp/p2.arw")),
            PhotoItem(url: URL(fileURLWithPath: "/tmp/p3.CR3")),
            PhotoItem(url: URL(fileURLWithPath: "/tmp/p4.jpg")),
            PhotoItem(url: URL(fileURLWithPath: "/tmp/p5.JPEG"))
        ]

        let stats = LibraryStatisticsEngine.calculate(from: items)

        #expect(stats.formatStatistics.count == 3) // arw, cr3, jpg (normalized from jpg and jpeg)

        let sonyStat = stats.formatStatistics.first(where: { $0.rawExtension == "arw" })
        #expect(sonyStat != nil)
        #expect(sonyStat?.displayName == "Sony ARW")
        #expect(sonyStat?.count == 2)
        #expect(sonyStat?.percentageOfTotal == 40.0)

        let canonStat = stats.formatStatistics.first(where: { $0.rawExtension == "cr3" })
        #expect(canonStat != nil)
        #expect(canonStat?.displayName == "Canon CR3")
        #expect(canonStat?.count == 1)
        #expect(canonStat?.percentageOfTotal == 20.0)

        let jpegStat = stats.formatStatistics.first(where: { $0.rawExtension == "jpg" })
        #expect(jpegStat != nil)
        #expect(jpegStat?.displayName == "JPEG")
        #expect(jpegStat?.count == 2)
        #expect(jpegStat?.percentageOfTotal == 40.0)

        // Sum of percentages must equal 100%
        let sumPercent = stats.formatStatistics.reduce(0.0) { $0 + $1.percentageOfTotal }
        #expect(abs(sumPercent - 100.0) < 0.001)
    }

    @Test("Quality Distributions & Averages: correctly computes mean sharpness and aesthetics with division safety")
    func testQualityAveragesAndDistributions() {
        let u = URL(fileURLWithPath: "/tmp/test.jpg")
        let items: [PhotoItem] = [
            PhotoItem(url: u, scores: QualityScores(sharpness: 30.0, noise: 5, highlightClipping: 0, shadowClipping: 0, aesthetic: 4.0)), // Blurry
            PhotoItem(url: u, scores: QualityScores(sharpness: 50.0, noise: 5, highlightClipping: 0, shadowClipping: 0, aesthetic: 6.0)), // Acceptable
            PhotoItem(url: u, scores: QualityScores(sharpness: 100.0, noise: 5, highlightClipping: 0, shadowClipping: 0, aesthetic: 8.0)), // Sharp
            PhotoItem(url: u, scores: nil) // Unscored candidate
        ]

        let stats = LibraryStatisticsEngine.calculate(from: items)

        // Average sharpness: (30 + 50 + 100) / 3 = 60.0
        #expect(stats.averageSharpness != nil)
        #expect(abs(stats.averageSharpness! - 60.0) < 0.001)

        // Average aesthetics: (4 + 6 + 8) / 3 = 6.0
        #expect(stats.averageAesthetic != nil)
        #expect(abs(stats.averageAesthetic! - 6.0) < 0.001)

        // Category breakdown
        #expect(stats.focusDistribution[.blurry] == 1)
        #expect(stats.focusDistribution[.acceptable] == 1)
        #expect(stats.focusDistribution[.sharp] == 1)
    }

    @Test("Leaderboard Top 5: extracts top 5 sharpest and aesthetic photos in strict descending order")
    func testLeaderboardsTop5Extraction() {
        let scoresData: [(Double, Double)] = [
            (10.0, 2.0),
            (95.0, 7.5),
            (88.0, 9.8), // #1 Aesthetic
            (40.0, 5.0),
            (99.0, 6.0), // #1 Sharpness
            (75.0, 8.0),
            (60.0, 7.0),
            (92.0, 8.5)
        ]

        let items: [PhotoItem] = scoresData.enumerated().map { i, val in
            PhotoItem(
                url: URL(fileURLWithPath: "/tmp/photo_\(i).jpg"),
                scores: QualityScores(sharpness: val.0, noise: 2, highlightClipping: 0, shadowClipping: 0, aesthetic: val.1)
            )
        }

        let stats = LibraryStatisticsEngine.calculate(from: items)

        // Sharpness leaderboard
        #expect(stats.topSharpestPhotos.count == 5)
        #expect(stats.topSharpestPhotos[0].scores?.sharpness == 99.0)
        #expect(stats.topSharpestPhotos[1].scores?.sharpness == 95.0)
        #expect(stats.topSharpestPhotos[2].scores?.sharpness == 92.0)
        #expect(stats.topSharpestPhotos[3].scores?.sharpness == 88.0)
        #expect(stats.topSharpestPhotos[4].scores?.sharpness == 75.0)

        // Aesthetics leaderboard
        #expect(stats.topAestheticPhotos.count == 5)
        #expect(stats.topAestheticPhotos[0].scores?.aesthetic == 9.8)
        #expect(stats.topAestheticPhotos[1].scores?.aesthetic == 8.5)
        #expect(stats.topAestheticPhotos[2].scores?.aesthetic == 8.0)
        #expect(stats.topAestheticPhotos[3].scores?.aesthetic == 7.5)
        #expect(stats.topAestheticPhotos[4].scores?.aesthetic == 7.0)
    }

    @Test("Asynchronous computation: calculateAsync produces identical results to synchronous calculate")
    func testAsyncCalculation() async {
        let u = URL(fileURLWithPath: "/tmp/async_test.jpg")
        let items: [PhotoItem] = [
            PhotoItem(url: u, scores: QualityScores(sharpness: 80.0, noise: 1, highlightClipping: 0, shadowClipping: 0, aesthetic: 9.0), status: .selected),
            PhotoItem(url: u, scores: QualityScores(sharpness: 40.0, noise: 3, highlightClipping: 0, shadowClipping: 0, aesthetic: 5.0), status: .candidate)
        ]

        let syncStats = LibraryStatisticsEngine.calculate(from: items)
        let asyncStats = await LibraryStatisticsEngine.calculateAsync(from: items)

        #expect(syncStats == asyncStats)
    }

    @Test("Diverse Formats & Utility: covers RAW formats (ARW, CR3, NEF, DNG) vs JPEG, HEIC, TIFF and utility flag counting")
    func testDiversePhotographicFormatsAndUtilityCount() {
        let items: [PhotoItem] = [
            PhotoItem(url: URL(fileURLWithPath: "/tmp/a.ARW")),
            PhotoItem(url: URL(fileURLWithPath: "/tmp/b.cr3")),
            PhotoItem(url: URL(fileURLWithPath: "/tmp/c.NEF")),
            PhotoItem(url: URL(fileURLWithPath: "/tmp/d.dng")),
            PhotoItem(url: URL(fileURLWithPath: "/tmp/e.raf")),
            PhotoItem(url: URL(fileURLWithPath: "/tmp/f.HEIC")),
            PhotoItem(url: URL(fileURLWithPath: "/tmp/g.tiff")),
            PhotoItem(url: URL(fileURLWithPath: "/tmp/h.jpg"), scores: QualityScores(sharpness: 50.0, noise: 0, highlightClipping: 0, shadowClipping: 0, isUtility: true))
        ]

        let stats = LibraryStatisticsEngine.calculate(from: items)

        #expect(stats.totalPhotoCount == 8)
        #expect(stats.utilityPhotoCount == 1)

        let names = Set(stats.formatStatistics.map { $0.displayName })
        #expect(names.contains("Sony ARW"))
        #expect(names.contains("Canon CR3"))
        #expect(names.contains("Nikon NEF"))
        #expect(names.contains("Adobe DNG"))
        #expect(names.contains("Fujifilm RAF"))
        #expect(names.contains("Apple HEIC"))
        #expect(names.contains("TIFF"))
        #expect(names.contains("JPEG"))
    }
}
