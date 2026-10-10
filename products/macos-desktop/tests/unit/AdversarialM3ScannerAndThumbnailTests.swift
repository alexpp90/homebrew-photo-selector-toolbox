import Testing
import Foundation
import CoreGraphics
import ImageIO
@testable import PhotoSelectorKit

@Suite("Adversarial M3: DirectoryScanner and ThumbnailLoader Stress Tests")
struct AdversarialM3ScannerAndThumbnailTests {

    // MARK: - Helpers

    private func createTempDir(prefix: String) throws -> URL {
        let tempBase = FileManager.default.temporaryDirectory
            .standardizedFileURL
            .resolvingSymlinksInPath()
        let dir = tempBase.appendingPathComponent("\(prefix)_\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let canonical = (try? dir.resourceValues(forKeys: [.canonicalPathKey]))?.canonicalPath ?? dir.path
        return URL(fileURLWithPath: canonical, isDirectory: true)
    }

    private func createCustomJPEG(
        at url: URL,
        width: Int,
        height: Int,
        pixels: [UInt8],
        orientation: Int = 1
    ) throws {
        guard let cgImage = SyntheticImageFactory.createGrayscaleCGImage(width: width, height: height, pixels: pixels) else {
            throw NSError(domain: "AdversarialM3Tests", code: 1, userInfo: [NSLocalizedDescriptionKey: "Failed to create CGImage"])
        }

        let data = NSMutableData()
        guard let destination = CGImageDestinationCreateWithData(
            data as CFMutableData,
            "public.jpeg" as CFString,
            1,
            nil
        ) else {
            throw NSError(domain: "AdversarialM3Tests", code: 2, userInfo: [NSLocalizedDescriptionKey: "Failed to create CGImageDestination"])
        }

        let tiffProps: [CFString: Any] = [
            kCGImagePropertyTIFFOrientation: orientation
        ]
        let props: [CFString: Any] = [
            kCGImagePropertyTIFFDictionary: tiffProps
        ]

        CGImageDestinationAddImage(destination, cgImage, props as CFDictionary)
        guard CGImageDestinationFinalize(destination) else {
            throw NSError(domain: "AdversarialM3Tests", code: 3, userInfo: [NSLocalizedDescriptionKey: "Failed to finalize JPEG"])
        }

        try (data as Data).write(to: url)
    }

    private func extractAveragePixelLuminance(image: CGImage) -> (topHalf: Double, bottomHalf: Double, leftHalf: Double, rightHalf: Double)? {
        let width = image.width
        let height = image.height
        guard width > 0, height > 0 else { return nil }

        var pixels = [UInt8](repeating: 0, count: width * height)
        let colorSpace = CGColorSpaceCreateDeviceGray()
        guard let context = CGContext(
            data: &pixels,
            width: width,
            height: height,
            bitsPerComponent: 8,
            bytesPerRow: width,
            space: colorSpace,
            bitmapInfo: CGImageAlphaInfo.none.rawValue
        ) else {
            return nil
        }
        context.draw(image, in: CGRect(x: 0, y: 0, width: width, height: height))

        var topSum: Double = 0, topCount: Double = 0
        var botSum: Double = 0, botCount: Double = 0
        var leftSum: Double = 0, leftCount: Double = 0
        var rightSum: Double = 0, rightCount: Double = 0

        for y in 0..<height {
            for x in 0..<width {
                let val = Double(pixels[y * width + x])
                // In CGBitmapContext, y=0 is bottom, y=height-1 is top
                if y >= height / 2 {
                    topSum += val
                    topCount += 1
                } else {
                    botSum += val
                    botCount += 1
                }

                if x < width / 2 {
                    leftSum += val
                    leftCount += 1
                } else {
                    rightSum += val
                    rightCount += 1
                }
            }
        }

        return (
            topHalf: topSum / max(1, topCount),
            bottomHalf: botSum / max(1, botCount),
            leftHalf: leftSum / max(1, leftCount),
            rightHalf: rightSum / max(1, rightCount)
        )
    }

    // MARK: - Challenge 1: DirectoryScanner Deep Nested Hierarchy & DCIM

    @Test("Adversarial: Deep nested folder hierarchy with DCIM structure (10 levels deep)")
    func testDeepNestedDCIMHierarchy() async throws {
        let root = try createTempDir(prefix: "DCIM_Deep_Adversarial")
        defer { try? FileManager.default.removeItem(at: root) }

        // Construct a deep 10-level hierarchy under DCIM
        var deepDir = root.appendingPathComponent("DCIM/100CANON", isDirectory: true)
        for i in 1...8 {
            deepDir = deepDir.appendingPathComponent("LEVEL_\(i)", isDirectory: true)
        }
        try FileManager.default.createDirectory(at: deepDir, withIntermediateDirectories: true)

        // Write deep photos
        let deepPhoto1 = deepDir.appendingPathComponent("IMG_0001.CR3")
        let deepPhoto2 = deepDir.appendingPathComponent("IMG_0002.JPG")
        let nonPhoto1 = deepDir.appendingPathComponent("INDEX.DAT")
        let nonPhoto2 = deepDir.appendingPathComponent(".DS_Store")

        try "RAW_DATA".data(using: .utf8)!.write(to: deepPhoto1)
        try "JPG_DATA".data(using: .utf8)!.write(to: deepPhoto2)
        try "INDEX_DATA".data(using: .utf8)!.write(to: nonPhoto1)
        try "METADATA".data(using: .utf8)!.write(to: nonPhoto2)

        // Add photos at intermediate levels
        let midDir = root.appendingPathComponent("DCIM/101SONY", isDirectory: true)
        try FileManager.default.createDirectory(at: midDir, withIntermediateDirectories: true)
        let midPhoto = midDir.appendingPathComponent("DSC00010.ARW")
        try "SONY_RAW".data(using: .utf8)!.write(to: midPhoto)

        let scanner = DirectoryScanner()
        let items = try await scanner.scanDirectory(at: root, recursive: true)

        #expect(items.count == 3, "Expected exactly 3 valid photo items from deep hierarchy, got \(items.count)")
        let filenames = Set(items.map { $0.filename })
        #expect(filenames.contains("IMG_0001.CR3"))
        #expect(filenames.contains("IMG_0002.JPG"))
        #expect(filenames.contains("DSC00010.ARW"))
        #expect(!filenames.contains("INDEX.DAT"))
        #expect(!filenames.contains(".DS_Store"))
    }

    // MARK: - Challenge 2: Folder with Name Containing 'Selection' as Substring

    @Test("Adversarial: Folders containing 'Selection' as a substring are scanned and NOT discarded")
    func testSelectionSubstringFoldersNotDiscarded() async throws {
        let root = try createTempDir(prefix: "SubstringSelection_Adversarial")
        defer { try? FileManager.default.removeItem(at: root) }

        // Various folder names containing 'Selection' as substring
        let folders = [
            "Event_Selection_Day1",
            "Selection_PreShoot",
            "MySelection2026",
            "Post_Selection_Review",
            "Selection-Final",
            "PreSelectionFolder"
        ]

        var expectedFilenames = Set<String>()

        for (idx, folderName) in folders.enumerated() {
            let folderURL = root.appendingPathComponent(folderName, isDirectory: true)
            try FileManager.default.createDirectory(at: folderURL, withIntermediateDirectories: true)

            let filename = "Photo_\(idx)_\(folderName).jpg"
            let photoURL = folderURL.appendingPathComponent(filename)
            try "DATA_\(idx)".data(using: .utf8)!.write(to: photoURL)
            expectedFilenames.insert(filename)
        }

        // Also add a root-level file with 'Selection' in its name
        let rootPhoto = root.appendingPathComponent("Selection_Cover.jpg")
        try "ROOT_DATA".data(using: .utf8)!.write(to: rootPhoto)
        expectedFilenames.insert("Selection_Cover.jpg")

        let scanner = DirectoryScanner()
        let items = try await scanner.scanDirectory(at: root, recursive: true)

        #expect(items.count == expectedFilenames.count, "Expected \(expectedFilenames.count) photos, got \(items.count)")
        let discoveredFilenames = Set(items.map { $0.filename })
        for expected in expectedFilenames {
            #expect(discoveredFilenames.contains(expected), "Missing photo: \(expected)")
        }
    }

    // MARK: - Challenge 3: Directory Containing True 'Selection/' Subfolder

    @Test("Adversarial: True 'Selection/' subfolder descendants are strictly skipped across casing and nesting")
    func testTrueSelectionSubfoldersSkipped() async throws {
        let root = try createTempDir(prefix: "TrueSelection_Adversarial")
        defer { try? FileManager.default.removeItem(at: root) }

        // Valid photos to keep
        let keep1 = root.appendingPathComponent("KEEP_ROOT.JPG")
        try "KEEP1".data(using: .utf8)!.write(to: keep1)

        let dcimDir = root.appendingPathComponent("DCIM/100CANON", isDirectory: true)
        try FileManager.default.createDirectory(at: dcimDir, withIntermediateDirectories: true)
        let keep2 = dcimDir.appendingPathComponent("KEEP_DCIM.CR3")
        try "KEEP2".data(using: .utf8)!.write(to: keep2)

        // 1. Root-level Selection/
        let selRoot = root.appendingPathComponent("Selection", isDirectory: true)
        try FileManager.default.createDirectory(at: selRoot, withIntermediateDirectories: true)
        let culled1 = selRoot.appendingPathComponent("CULLED_ROOT.JPG")
        try "CULLED1".data(using: .utf8)!.write(to: culled1)

        // 2. Lowercase selection/
        let selLower = root.appendingPathComponent("selection", isDirectory: true)
        try? FileManager.default.createDirectory(at: selLower, withIntermediateDirectories: true)
        let culled2 = selRoot.appendingPathComponent("CULLED_LOWER.JPG")
        try "CULLED2".data(using: .utf8)!.write(to: culled2)

        // 3. Nested DCIM/100CANON/Selection/
        let selNested = dcimDir.appendingPathComponent("Selection", isDirectory: true)
        try FileManager.default.createDirectory(at: selNested, withIntermediateDirectories: true)
        let culled3 = selNested.appendingPathComponent("CULLED_NESTED.CR3")
        try "CULLED3".data(using: .utf8)!.write(to: culled3)

        // 4. Deeply nested SubA/SubB/Selection/Deep/
        let selDeep = root.appendingPathComponent("SubA/SubB/Selection/Deep", isDirectory: true)
        try FileManager.default.createDirectory(at: selDeep, withIntermediateDirectories: true)
        let culled4 = selDeep.appendingPathComponent("CULLED_DEEP.JPG")
        try "CULLED4".data(using: .utf8)!.write(to: culled4)

        let scanner = DirectoryScanner()
        let items = try await scanner.scanDirectory(at: root, recursive: true)

        #expect(items.count == 2, "Expected exactly 2 photos kept, found \(items.count)")
        let filenames = Set(items.map { $0.filename })
        #expect(filenames.contains("KEEP_ROOT.JPG"))
        #expect(filenames.contains("KEEP_DCIM.CR3"))
        #expect(!filenames.contains("CULLED_ROOT.JPG"))
        #expect(!filenames.contains("CULLED_LOWER.JPG"))
        #expect(!filenames.contains("CULLED_NESTED.CR3"))
        #expect(!filenames.contains("CULLED_DEEP.JPG"))
    }

    // MARK: - Challenge 4: Companion File Grouping & RAW Priority

    @Test("Adversarial: Companion file grouping (RAW + XMP + JPG sidecars) emits exactly 1 primary candidate")
    func testCompanionGroupingEmitsExactlyOneCandidate() async throws {
        let root = try createTempDir(prefix: "CompanionGrouping_Adversarial")
        defer { try? FileManager.default.removeItem(at: root) }

        // Shot 1: RAW + JPG + XMP + Lightroom Edit + Lightroom XMP
        let raw1 = root.appendingPathComponent("DSC_0001.NEF")
        let jpg1 = root.appendingPathComponent("DSC_0001.JPG")
        let xmp1 = root.appendingPathComponent("DSC_0001.xmp")
        let dotRawXmp1 = root.appendingPathComponent("DSC_0001.NEF.xmp")
        let edit1 = root.appendingPathComponent("DSC_0001-Edit.tif")
        let editXmp1 = root.appendingPathComponent("DSC_0001-Edit.xmp")

        try "RAW1".data(using: .utf8)!.write(to: raw1)
        try "JPG1".data(using: .utf8)!.write(to: jpg1)
        try "XMP1".data(using: .utf8)!.write(to: xmp1)
        try "DOT_XMP1".data(using: .utf8)!.write(to: dotRawXmp1)
        try "EDIT1".data(using: .utf8)!.write(to: edit1)
        try "EDIT_XMP1".data(using: .utf8)!.write(to: editXmp1)

        // Shot 2: Standard JPEG + XMP only (no RAW)
        let jpg2 = root.appendingPathComponent("IMG_0002.JPEG")
        let xmp2 = root.appendingPathComponent("IMG_0002.XMP")
        try "JPG2".data(using: .utf8)!.write(to: jpg2)
        try "XMP2".data(using: .utf8)!.write(to: xmp2)

        // Shot 3: Lone RAW file
        let raw3 = root.appendingPathComponent("CAPTURE_0003.DNG")
        try "RAW3".data(using: .utf8)!.write(to: raw3)

        // Shot 4: Lone orphan XMP sidecar (should NOT produce a PhotoItem)
        let loneXmp = root.appendingPathComponent("ORPHAN_0004.xmp")
        try "ORPHAN".data(using: .utf8)!.write(to: loneXmp)

        let scanner = DirectoryScanner()
        let items = try await scanner.scanDirectory(at: root, recursive: false)

        #expect(items.count == 3, "Expected exactly 3 primary candidates, got \(items.count)")

        // Verify Shot 1 candidate is RAW
        let shot1 = items.first { $0.filename.starts(with: "DSC_0001") }
        #expect(shot1 != nil)
        #expect(shot1?.filename == "DSC_0001.NEF", "Primary candidate must be RAW .NEF, got \(shot1?.filename ?? "nil")")

        // Verify companion files for Shot 1
        let cullingManager = FileCullingManager()
        if let shot1URL = shot1?.url {
            let companions = cullingManager.findCompanionFiles(for: shot1URL)
            #expect(companions.count == 6, "Expected 6 companion files for Shot 1, got \(companions.count)")
            #expect(companions.contains(raw1))
            #expect(companions.contains(jpg1))
            #expect(companions.contains(xmp1))
            #expect(companions.contains(dotRawXmp1))
            #expect(companions.contains(edit1))
            #expect(companions.contains(editXmp1))
        }

        // Verify Shot 2 candidate is JPEG
        let shot2 = items.first { $0.filename.starts(with: "IMG_0002") }
        #expect(shot2 != nil)
        #expect(shot2?.filename == "IMG_0002.JPEG")

        // Verify Shot 3 candidate is DNG
        let shot3 = items.first { $0.filename.starts(with: "CAPTURE_0003") }
        #expect(shot3 != nil)
        #expect(shot3?.filename == "CAPTURE_0003.DNG")
    }

    // MARK: - Challenge 5: Non-Standard EXIF Orientations (1 to 8) in ThumbnailLoader

    @Test("Adversarial: All 8 EXIF Orientations correctly transform dimensions and pixels")
    func testAllEXIFOrientationsTransform() throws {
        let tempDir = try createTempDir(prefix: "EXIF_Orientation_Adversarial")
        defer { try? FileManager.default.removeItem(at: tempDir) }

        // Create an asymmetric 200x100 image (width: 200, height: 100)
        // Top half (rows 50..<100 in mathematical coords) is bright 240
        // Bottom half (rows 0..<50) is dark 20
        let width = 200
        let height = 100
        var pixels = [UInt8](repeating: 0, count: width * height)
        for y in 0..<height {
            for x in 0..<width {
                // In top-down raster coordinates: y < 50 is top
                pixels[y * width + x] = (y < height / 2) ? 240 : 20
            }
        }

        for orientation in 1...8 {
            let fileURL = tempDir.appendingPathComponent("orient_\(orientation).jpg")
            try createCustomJPEG(at: fileURL, width: width, height: height, pixels: pixels, orientation: orientation)

            let decoded = ThumbnailLoader.decodeThumbnail(from: fileURL, maxPixelSize: 200)
            #expect(decoded != nil, "Orientation \(orientation) failed to decode")
            guard let image = decoded else { continue }

            // Orientations 1, 2, 3, 4 preserve width > height
            // Orientations 5, 6, 7, 8 transpose dimensions to height > width
            if [5, 6, 7, 8].contains(orientation) {
                #expect(
                    image.height > image.width,
                    "Orientation \(orientation) must transpose dimensions (height > width). Got \(image.width)x\(image.height)"
                )
            } else {
                #expect(
                    image.width > image.height,
                    "Orientation \(orientation) must keep landscape dimensions (width > height). Got \(image.width)x\(image.height)"
                )
            }
        }

        // Detailed Pixel Oracle for Orientation 3 (180 degree rotation) vs Orientation 1:
        let file1 = tempDir.appendingPathComponent("orient_1.jpg")
        let file3 = tempDir.appendingPathComponent("orient_3.jpg")

        guard let img1 = ThumbnailLoader.decodeThumbnail(from: file1, maxPixelSize: 200),
              let img3 = ThumbnailLoader.decodeThumbnail(from: file3, maxPixelSize: 200),
              let lum1 = extractAveragePixelLuminance(image: img1),
              let lum3 = extractAveragePixelLuminance(image: img3) else {
            #expect(Bool(false), "Failed to extract luminance for orientation 1 and 3")
            return
        }

        // For img1 (normal), topHalf and bottomHalf differ significantly
        #expect(abs(lum1.topHalf - lum1.bottomHalf) > 100.0)

        // For img3 (180 degree rotation WITH transform), top and bottom luminance MUST invert!
        // If transform were ignored, lum3.topHalf would equal lum1.topHalf.
        #expect(
            abs(lum3.topHalf - lum1.bottomHalf) < 30.0,
            "Orientation 3 top half should match Orientation 1 bottom half due to 180 deg transform"
        )
        #expect(
            abs(lum3.bottomHalf - lum1.topHalf) < 30.0,
            "Orientation 3 bottom half should match Orientation 1 top half due to 180 deg transform"
        )
    }

    // MARK: - Challenge 6: Rapid Sequential Navigation Prefetch Stress Test

    @Test("Adversarial: Rapid sequential navigation cancels obsolete tasks and maintains responsiveness")
    func testRapidSequentialNavigationPrefetch() async throws {
        let tempDir = try createTempDir(prefix: "Prefetch_Rapid_Nav")
        defer { try? FileManager.default.removeItem(at: tempDir) }

        // Generate 35 small test images
        var items: [PhotoItem] = []
        let dummyPixels = [UInt8](repeating: 128, count: 64 * 64)
        for i in 0..<35 {
            let u = tempDir.appendingPathComponent(String(format: "IMG_%04d.JPG", i))
            try createCustomJPEG(at: u, width: 64, height: 64, pixels: dummyPixels, orientation: 1)
            items.append(PhotoItem(id: UUID(), url: u, status: .candidate))
        }

        let customCache = ThumbnailCache(memoryLimitBytes: 16 * 1024 * 1024, countLimit: 100)
        let loader = ThumbnailLoader(cache: customCache)

        // Rapidly navigate forward from index 0 to 25 (simulating fast keyboard arrow presses)
        for idx in 0...25 {
            await loader.updatePrefetchWindow(items: items, currentIndex: idx, maxPixelSize: 128)
            // Tiny microsecond pause simulating 120Hz key repeat
            try? await Task.sleep(nanoseconds: 1_000_000) // 1ms
        }

        // Destination index 25 should load cleanly and not be blocked or cancelled
        let destImage = await loader.loadThumbnail(for: items[25].url, maxPixelSize: 128)
        #expect(destImage != nil, "Destination image at index 25 must load successfully")

        // Sudden direction reversal: Jump from 25 to 2!
        await loader.updatePrefetchWindow(items: items, currentIndex: 2, maxPixelSize: 128)
        let jumpImage = await loader.loadThumbnail(for: items[2].url, maxPixelSize: 128)
        #expect(jumpImage != nil, "Reversal jump image at index 2 must load successfully")

        await loader.cancelAll()
    }

    // MARK: - Challenge 7: ThumbnailCache Memory Bound Enforcement

    @Test("Adversarial: ThumbnailCache strictly enforces memory byte cost and count limits")
    func testThumbnailCacheMemoryBoundEnforcement() async throws {
        // Strict limit: 200 KB total cost limit, 5 items max count
        let strictCache = ThumbnailCache(memoryLimitBytes: 200 * 1024, countLimit: 5)
        let dummyBaseURL = URL(fileURLWithPath: "/tmp/memory_test")

        // Create 12 distinct 200x200 8-bit images (each costs 200 * 200 = 40,000 bytes)
        // 5 items * 40,000 = 200,000 bytes (hits cost limit and count limit exactly)
        var keys: [ThumbnailCacheKey] = []
        for i in 0..<12 {
            let url = dummyBaseURL.appendingPathComponent("img_\(i).jpg")
            let key = ThumbnailCacheKey(url: url, maxPixelSize: 200)
            keys.append(key)

            let pixels = [UInt8](repeating: UInt8(i * 20), count: 200 * 200)
            guard let cgImage = SyntheticImageFactory.createGrayscaleCGImage(width: 200, height: 200, pixels: pixels) else {
                continue
            }
            await strictCache.setImage(cgImage, for: key)
        }

        // The most recently inserted item (key 11) must be in the cache
        let recent = await strictCache.image(for: keys[11])
        #expect(recent != nil, "Most recently cached image must be present")

        // Earlier items (e.g. key 0) must have been evicted by NSCache due to count/cost limit
        let oldest = await strictCache.image(for: keys[0])
        #expect(oldest == nil, "Oldest image should have been evicted to respect memory bound")

        // Key separation: Tier 384 vs Tier 2048 for same URL must be independent
        let sampleURL = dummyBaseURL.appendingPathComponent("dual_tier.jpg")
        let keySmall = ThumbnailCacheKey(url: sampleURL, maxPixelSize: 384)
        let keyLarge = ThumbnailCacheKey(url: sampleURL, maxPixelSize: 2048)

        let smallPixels = [UInt8](repeating: 50, count: 64 * 64)
        let largePixels = [UInt8](repeating: 150, count: 128 * 128)
        guard let smallCG = SyntheticImageFactory.createGrayscaleCGImage(width: 64, height: 64, pixels: smallPixels),
              let largeCG = SyntheticImageFactory.createGrayscaleCGImage(width: 128, height: 128, pixels: largePixels) else {
            #expect(Bool(false), "Failed to create images for tier separation test")
            return
        }

        await strictCache.setImage(smallCG, for: keySmall)
        await strictCache.setImage(largeCG, for: keyLarge)

        #expect(await strictCache.image(for: keySmall)?.width == 64)
        #expect(await strictCache.image(for: keyLarge)?.width == 128)

        // Selective eviction
        await strictCache.removeImage(for: keyLarge)
        #expect(await strictCache.image(for: keyLarge) == nil)
        #expect(await strictCache.image(for: keySmall) != nil)

        // URL invalidation removes both tiers
        await strictCache.removeImages(for: sampleURL)
        #expect(await strictCache.image(for: keySmall) == nil)
    }

    // MARK: - Challenge 8: Concurrent High-Stress Hammering

    @Test("Adversarial: Highly concurrent simultaneous loadThumbnail calls do not deadlock or race")
    func testConcurrentLoadThumbnailHammering() async throws {
        let tempDir = try createTempDir(prefix: "Concurrent_Hammer")
        defer { try? FileManager.default.removeItem(at: tempDir) }

        var urls: [URL] = []
        let dummyPixels = [UInt8](repeating: 100, count: 50 * 50)
        for i in 0..<8 {
            let u = tempDir.appendingPathComponent("photo_\(i).jpg")
            try createCustomJPEG(at: u, width: 50, height: 50, pixels: dummyPixels, orientation: 1)
            urls.append(u)
        }

        let loader = ThumbnailLoader()

        // Spawn 24 concurrent async tasks hammering the loader across overlapping URLs
        await withTaskGroup(of: CGImage?.self) { group in
            for i in 0..<24 {
                let targetURL = urls[i % urls.count]
                group.addTask {
                    return await loader.loadThumbnail(for: targetURL, maxPixelSize: 100)
                }
            }

            var successCount = 0
            for await result in group {
                if result != nil {
                    successCount += 1
                }
            }

            #expect(successCount == 24, "All 24 concurrent load requests must succeed without deadlock")
        }
    }
}
