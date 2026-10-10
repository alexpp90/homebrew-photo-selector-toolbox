import Testing
import Foundation
import CoreGraphics
import ImageIO
@testable import PhotoSelectorKit

@Suite("ThumbnailLoader & ThumbnailCache Unit Tests")
struct ThumbnailLoaderTests {

    private func createTempImageFile(width: Int, height: Int, orientation: Int = 1) throws -> URL {
        let tempBase = FileManager.default.temporaryDirectory
            .standardizedFileURL
            .resolvingSymlinksInPath()
        let fileURL = tempBase.appendingPathComponent("thumb_test_\(UUID().uuidString).jpg")

        let tiffProps: [CFString: Any] = [
            kCGImagePropertyTIFFOrientation: orientation
        ]
        guard let data = SyntheticImageFactory.createJPEGDataWithMetadata(
            width: width,
            height: height,
            tiffProperties: tiffProps
        ) else {
            throw NSError(domain: "ThumbnailLoaderTests", code: 1, userInfo: nil)
        }

        try data.write(to: fileURL)
        return fileURL
    }

    // MARK: - ThumbnailCache Tests

    @Test("ThumbnailCache: Dual-tier key separation prevents collisions")
    func testDualTierKeySeparation() async throws {
        let cache = ThumbnailCache(memoryLimitBytes: 10 * 1024 * 1024, countLimit: 100)
        let sampleURL = URL(fileURLWithPath: "/tmp/sample.jpg")

        let pixelsSmall = [UInt8](repeating: 100, count: 64 * 64)
        let pixelsLarge = [UInt8](repeating: 200, count: 256 * 256)
        guard let smallCG = SyntheticImageFactory.createGrayscaleCGImage(width: 64, height: 64, pixels: pixelsSmall),
              let largeCG = SyntheticImageFactory.createGrayscaleCGImage(width: 256, height: 256, pixels: pixelsLarge) else {
            #expect(Bool(false), "Failed to create synthetic images")
            return
        }

        let keyFilmstrip = ThumbnailCacheKey(url: sampleURL, maxPixelSize: ThumbnailTier.filmstrip.rawValue)
        let keyPreview = ThumbnailCacheKey(url: sampleURL, maxPixelSize: ThumbnailTier.preview.rawValue)

        await cache.setImage(smallCG, for: keyFilmstrip)
        await cache.setImage(largeCG, for: keyPreview)

        let cachedSmall = await cache.image(for: keyFilmstrip)
        let cachedLarge = await cache.image(for: keyPreview)

        #expect(cachedSmall != nil)
        #expect(cachedLarge != nil)
        #expect(cachedSmall?.width == 64)
        #expect(cachedLarge?.width == 256)

        // Remove only preview
        await cache.removeImage(for: keyPreview)
        #expect(await cache.image(for: keyPreview) == nil)
        #expect(await cache.image(for: keyFilmstrip) != nil)

        // Invalidate URL removes all
        await cache.removeImages(for: sampleURL)
        #expect(await cache.image(for: keyFilmstrip) == nil)
    }

    // MARK: - ThumbnailLoader Decoding Tests

    @Test("ThumbnailLoader: Downsamples image to maxPixelSize")
    func testMaxPixelSizeClamping() throws {
        let imageURL = try createTempImageFile(width: 400, height: 200)
        defer { try? FileManager.default.removeItem(at: imageURL) }

        let decoded = ThumbnailLoader.decodeThumbnail(from: imageURL, maxPixelSize: 100)
        #expect(decoded != nil)
        guard let image = decoded else { return }

        let maxEdge = max(image.width, image.height)
        #expect(maxEdge <= 100, "Expected max edge <= 100, got \(maxEdge)")
    }

    @Test("ThumbnailLoader: Applies EXIF orientation transform correctly")
    func testOrientationTransform() throws {
        // Orientation 6: Top-Right (rotated 90 deg clockwise)
        // Original: width=200, height=100.
        // After 90 deg transform: width should be 100, height should be 200 (or downsampled maintaining aspect ratio).
        let imageURL = try createTempImageFile(width: 200, height: 100, orientation: 6)
        defer { try? FileManager.default.removeItem(at: imageURL) }

        let decoded = ThumbnailLoader.decodeThumbnail(from: imageURL, maxPixelSize: 200)
        #expect(decoded != nil)
        guard let image = decoded else { return }

        // When orientation 6 is respected, height is greater than width
        #expect(image.height > image.width, "Expected portrait orientation (height > width), got \(image.width)x\(image.height)")
    }

    @Test("ThumbnailLoader: Asynchronous loading and in-memory caching")
    func testAsyncLoadingAndCaching() async throws {
        let imageURL = try createTempImageFile(width: 120, height: 80)
        defer { try? FileManager.default.removeItem(at: imageURL) }

        let customCache = ThumbnailCache()
        let loader = ThumbnailLoader(cache: customCache)

        let loaded = await loader.loadThumbnail(for: imageURL, maxPixelSize: 100)
        #expect(loaded != nil)

        // Second load should hit cache
        let key = ThumbnailCacheKey(url: imageURL, maxPixelSize: 100)
        let cached = await customCache.image(for: key)
        #expect(cached != nil)
    }

    @Test("ThumbnailLoader: Prefetch window updates without crashing")
    func testPrefetchWindow() async throws {
        var items: [PhotoItem] = []
        var urls: [URL] = []
        for _ in 1...10 {
            let u = try createTempImageFile(width: 60, height: 60)
            urls.append(u)
            items.append(PhotoItem(id: UUID(), url: u, status: .candidate))
        }
        defer {
            for u in urls { try? FileManager.default.removeItem(at: u) }
        }

        let loader = ThumbnailLoader()
        await loader.updatePrefetchWindow(items: items, currentIndex: 4, maxPixelSize: 100)
        await loader.cancelAll()
    }
}
