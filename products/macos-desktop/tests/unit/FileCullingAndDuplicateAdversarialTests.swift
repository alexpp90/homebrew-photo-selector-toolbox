import Testing
import Foundation
import CryptoKit
@testable import PhotoSelectorKit

@Suite("FileCulling and DuplicateFinder Adversarial Tests")
struct FileCullingAndDuplicateAdversarialTests {

    // MARK: - FileCullingManager Challenges

    @Test("Companion discovery handles mixed uppercase and lowercase extensions (.arw vs .ARW, .xmp vs .XMP)")
    func testCompanionCaseSensitivity() throws {
        let tempDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("CullingCaseTest_\(UUID().uuidString)")
            .standardizedFileURL
            .resolvingSymlinksInPath()
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: tempDir) }

        // Test 1: Lowercase primary with uppercase companion extensions
        let primaryLower = tempDir.appendingPathComponent("dsc0001.arw")
        let companionJpgUpper = tempDir.appendingPathComponent("DSC0001.JPG")
        let companionXmpUpper = tempDir.appendingPathComponent("DSC0001.XMP")
        let companionDoubleXmp = tempDir.appendingPathComponent("dsc0001.arw.XMP")

        try "data1".data(using: .utf8)!.write(to: primaryLower)
        try "data2".data(using: .utf8)!.write(to: companionJpgUpper)
        try "data3".data(using: .utf8)!.write(to: companionXmpUpper)
        try "data4".data(using: .utf8)!.write(to: companionDoubleXmp)

        let manager = FileCullingManager()
        let companions = manager.findCompanionFiles(for: primaryLower)

        #expect(companions.first == primaryLower)
        #expect(companions.count == 4)
        #expect(companions.contains(companionJpgUpper))
        #expect(companions.contains(companionXmpUpper))
        #expect(companions.contains(companionDoubleXmp))

        // Test 2: Uppercase primary with lowercase companion extensions
        let primaryUpper = tempDir.appendingPathComponent("PHOTO99.CR3")
        let companionJpgLower = tempDir.appendingPathComponent("photo99.jpg")
        let companionXmpLower = tempDir.appendingPathComponent("PHOTO99.xmp")

        try "data5".data(using: .utf8)!.write(to: primaryUpper)
        try "data6".data(using: .utf8)!.write(to: companionJpgLower)
        try "data7".data(using: .utf8)!.write(to: companionXmpLower)

        let companionsUpper = manager.findCompanionFiles(for: primaryUpper)
        #expect(companionsUpper.first == primaryUpper)
        #expect(companionsUpper.count == 3)
        #expect(companionsUpper.contains(companionJpgLower))
        #expect(companionsUpper.contains(companionXmpLower))
    }

    @Test("Companion discovery handles multiple companions (ARW, JPG, xmp, Lightroom edits) and excludes non-companions")
    func testMultipleCompanionsDiscovery() throws {
        let tempDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("MultiCompanionTest_\(UUID().uuidString)")
            .standardizedFileURL
            .resolvingSymlinksInPath()
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: tempDir) }

        let primaryURL = tempDir.appendingPathComponent("DSC0001.ARW")
        let jpgPairURL = tempDir.appendingPathComponent("DSC0001.JPG")
        let xmpURL = tempDir.appendingPathComponent("DSC0001.xmp")
        let lrEditTif = tempDir.appendingPathComponent("DSC0001-Edit.tif")
        let lrEditXmp = tempDir.appendingPathComponent("DSC0001-Edit.xmp")
        let lrEditUnderscore = tempDir.appendingPathComponent("DSC0001_edit.jpg")

        // Boundary cases that should NOT be companions
        let numericSuffix = tempDir.appendingPathComponent("DSC00010.JPG") // Extra digit
        let unrelatedURL = tempDir.appendingPathComponent("DSC0002.ARW")

        let dummy = "test".data(using: .utf8)!
        try dummy.write(to: primaryURL)
        try dummy.write(to: jpgPairURL)
        try dummy.write(to: xmpURL)
        try dummy.write(to: lrEditTif)
        try dummy.write(to: lrEditXmp)
        try dummy.write(to: lrEditUnderscore)
        try dummy.write(to: numericSuffix)
        try dummy.write(to: unrelatedURL)

        let manager = FileCullingManager()
        let companions = manager.findCompanionFiles(for: primaryURL)

        #expect(companions.first == primaryURL)
        #expect(companions.count == 6)
        #expect(companions.contains(jpgPairURL))
        #expect(companions.contains(xmpURL))
        #expect(companions.contains(lrEditTif))
        #expect(companions.contains(lrEditXmp))
        #expect(companions.contains(lrEditUnderscore))
        #expect(!companions.contains(numericSuffix))
        #expect(!companions.contains(unrelatedURL))
    }

    @Test("Moving to Selection when Selection/ directory does not yet exist creates directory and moves all companions")
    func testMoveToSelectionWhenDirectoryDoesNotExist() throws {
        let tempDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("NoSelectionDirTest_\(UUID().uuidString)")
            .standardizedFileURL
            .resolvingSymlinksInPath()
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: tempDir) }

        let selectionDir = tempDir.appendingPathComponent("Selection", isDirectory: true)
        #expect(!FileManager.default.fileExists(atPath: selectionDir.path))

        let primaryURL = tempDir.appendingPathComponent("IMG_0042.CR2")
        let jpgURL = tempDir.appendingPathComponent("IMG_0042.JPG")
        let xmpURL = tempDir.appendingPathComponent("IMG_0042.xmp")

        try "cr2".data(using: .utf8)!.write(to: primaryURL)
        try "jpg".data(using: .utf8)!.write(to: jpgURL)
        try "xmp".data(using: .utf8)!.write(to: xmpURL)

        let manager = FileCullingManager()
        let moved = try manager.moveToSelection(photoURL: primaryURL, rootURL: tempDir)

        #expect(FileManager.default.fileExists(atPath: selectionDir.path))
        #expect(moved.count == 3)

        // Verify moved files exist in Selection/
        for file in ["IMG_0042.CR2", "IMG_0042.JPG", "IMG_0042.xmp"] {
            let destPath = selectionDir.appendingPathComponent(file).path
            #expect(FileManager.default.fileExists(atPath: destPath))
        }

        // Verify originals removed from root
        #expect(!FileManager.default.fileExists(atPath: primaryURL.path))
        #expect(!FileManager.default.fileExists(atPath: jpgURL.path))
        #expect(!FileManager.default.fileExists(atPath: xmpURL.path))
    }

    @Test("Moving to Selection when destination filename collision occurs cleanly overwrites without error")
    func testMoveToSelectionDestinationCollision() throws {
        let tempDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("CollisionTest_\(UUID().uuidString)")
            .standardizedFileURL
            .resolvingSymlinksInPath()
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: tempDir) }

        let selectionDir = tempDir.appendingPathComponent("Selection", isDirectory: true)
        try FileManager.default.createDirectory(at: selectionDir, withIntermediateDirectories: true)

        let primaryURL = tempDir.appendingPathComponent("COLLIDE_01.ARW")
        let oldContent = "PRE_EXISTING_DESTINATION_CONTENT"
        let newContent = "NEW_SOURCE_CONTENT"

        let destFile = selectionDir.appendingPathComponent("COLLIDE_01.ARW")
        try oldContent.data(using: .utf8)!.write(to: destFile)
        try newContent.data(using: .utf8)!.write(to: primaryURL)

        let manager = FileCullingManager()
        let moved = try manager.moveToSelection(photoURL: primaryURL, rootURL: tempDir)

        #expect(moved.count == 1)
        #expect(moved.first?.path == destFile.path)
        #expect(FileManager.default.fileExists(atPath: destFile.path))
        #expect(!FileManager.default.fileExists(atPath: primaryURL.path))

        // Check content was updated to new content
        let readContent = String(data: try Data(contentsOf: destFile), encoding: .utf8)
        #expect(readContent == newContent)
    }

    @Test("Adversarial behavior when photo is already inside Selection/ folder")
    func testMoveAlreadyInSelection() throws {
        let tempDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("SelfMoveTest_\(UUID().uuidString)")
            .standardizedFileURL
            .resolvingSymlinksInPath()
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: tempDir) }

        let selectionDir = tempDir.appendingPathComponent("Selection", isDirectory: true)
        try FileManager.default.createDirectory(at: selectionDir, withIntermediateDirectories: true)

        let selectedPhoto = selectionDir.appendingPathComponent("ALREADY_SELECTED.JPG")
        try "important_photo".data(using: .utf8)!.write(to: selectedPhoto)

        let manager = FileCullingManager()
        do {
            _ = try manager.moveToSelection(photoURL: selectedPhoto, rootURL: tempDir)
        } catch {
            // Documenting error
        }
        let stillExists = FileManager.default.fileExists(atPath: selectedPhoto.path)
        #expect(stillExists, "CRITICAL: Photo was permanently deleted when moveToSelection was called on file already in Selection folder!")
    }

    @Test("Copying to Selection when destination collision occurs cleanly overwrites destination")
    func testCopyToSelectionDestinationCollision() throws {
        let tempDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("CopyCollisionTest_\(UUID().uuidString)")
            .standardizedFileURL
            .resolvingSymlinksInPath()
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: tempDir) }

        let selectionDir = tempDir.appendingPathComponent("Selection", isDirectory: true)
        try FileManager.default.createDirectory(at: selectionDir, withIntermediateDirectories: true)

        let primaryURL = tempDir.appendingPathComponent("COPY_01.JPG")
        let destFile = selectionDir.appendingPathComponent("COPY_01.JPG")
        try "old".data(using: .utf8)!.write(to: destFile)
        try "new".data(using: .utf8)!.write(to: primaryURL)

        let manager = FileCullingManager()
        let copied = try manager.copyToSelection(photoURL: primaryURL, rootURL: tempDir)

        #expect(copied.count == 1)
        let readContent = String(data: try Data(contentsOf: destFile), encoding: .utf8)
        #expect(readContent == "new")
        #expect(FileManager.default.fileExists(atPath: primaryURL.path)) // Original preserved
    }

    @Test("Trashing uses system trashItem safely, moves files out of place, and fails on missing file")
    func testTrashingSafely() throws {
        let tempDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("TrashSafetyTest_\(UUID().uuidString)")
            .standardizedFileURL
            .resolvingSymlinksInPath()
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: tempDir) }

        let primaryURL = tempDir.appendingPathComponent("TRASH_ME.ARW")
        let companionURL = tempDir.appendingPathComponent("TRASH_ME.xmp")
        try "trash_arw".data(using: .utf8)!.write(to: primaryURL)
        try "trash_xmp".data(using: .utf8)!.write(to: companionURL)

        let manager = FileCullingManager()
        let trashed = try manager.moveToTrash(photoURL: primaryURL)

        #expect(trashed.count == 2)
        #expect(!FileManager.default.fileExists(atPath: primaryURL.path))
        #expect(!FileManager.default.fileExists(atPath: companionURL.path))

        // Trashed URLs should exist in Trash or valid path
        for url in trashed {
            #expect(FileManager.default.fileExists(atPath: url.path))
            // Clean up trash item
            try? FileManager.default.removeItem(at: url)
        }

        // Trashing nonexistent file throws fileNotFound
        let missingURL = tempDir.appendingPathComponent("NONEXISTENT.ARW")
        #expect(throws: FileCullingError.self) {
            _ = try manager.moveToTrash(photoURL: missingURL)
        }
    }

    // MARK: - DuplicateFinder Challenges

    @Test("Files of identical byte size but different content must NOT be grouped as duplicates")
    func testIdenticalSizeDifferentContentNotGrouped() async throws {
        let tempDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("IdenticalSizeTest_\(UUID().uuidString)")
            .standardizedFileURL
            .resolvingSymlinksInPath()
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: tempDir) }

        let size = 1024
        // Create 4 files with the exact same 1024-byte size, but different contents
        var fileURLs: [URL] = []
        for i in 0..<4 {
            let fileURL = tempDir.appendingPathComponent("distinct_\(i).dat")
            var bytes = [UInt8](repeating: UInt8(i + 1), count: size)
            bytes[0] = UInt8(i * 10)
            try Data(bytes).write(to: fileURL)
            fileURLs.append(fileURL)
        }

        let clusters = try await DuplicateFinder.findDuplicates(in: fileURLs)
        #expect(clusters.isEmpty)
    }

    @Test("Files of identical contents must be grouped together into a DuplicateCluster")
    func testIdenticalContentsGrouped() async throws {
        let tempDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("IdenticalContentTest_\(UUID().uuidString)")
            .standardizedFileURL
            .resolvingSymlinksInPath()
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: tempDir) }

        let contentA = "IDENTICAL_PHOTO_DATA_CLUSTER_A".data(using: .utf8)!
        let fileA1 = tempDir.appendingPathComponent("img_1.jpg")
        let fileA2 = tempDir.appendingPathComponent("img_1_copy.jpg")
        let fileA3 = tempDir.appendingPathComponent("img_1_backup.jpg")
        try contentA.write(to: fileA1)
        try contentA.write(to: fileA2)
        try contentA.write(to: fileA3)

        let contentB = "IDENTICAL_PHOTO_DATA_CLUSTER_B".data(using: .utf8)!
        let fileB1 = tempDir.appendingPathComponent("img_2.jpg")
        let fileB2 = tempDir.appendingPathComponent("img_2_copy.jpg")
        try contentB.write(to: fileB1)
        try contentB.write(to: fileB2)

        let clusters = try await DuplicateFinder.findDuplicates(in: tempDir)

        #expect(clusters.count == 2)
        let clusterA = clusters.first(where: { $0.fileURLs.contains(fileA1) })
        #expect(clusterA != nil)
        #expect(clusterA?.fileURLs.count == 3)
        #expect(clusterA?.fileURLs.contains(fileA2) == true)
        #expect(clusterA?.fileURLs.contains(fileA3) == true)

        let clusterB = clusters.first(where: { $0.fileURLs.contains(fileB1) })
        #expect(clusterB != nil)
        #expect(clusterB?.fileURLs.count == 2)
        #expect(clusterB?.fileURLs.contains(fileB2) == true)
    }

    @Test("Large files (>1 MB) stream chunks properly and hash accurately without memory explosion")
    func testLargeFilesStreamingSHA256() async throws {
        let tempDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("LargeFileTest_\(UUID().uuidString)")
            .standardizedFileURL
            .resolvingSymlinksInPath()
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: tempDir) }

        // Create 1.5 MB test files (exceeding 64 KB buffer size by ~24x)
        let size = 1_500_000
        var largeData = Data(count: size)
        for i in 0..<size {
            largeData[i] = UInt8(i % 251)
        }

        let file1 = tempDir.appendingPathComponent("large1.bin")
        let file2 = tempDir.appendingPathComponent("large2.bin")
        try largeData.write(to: file1)
        try largeData.write(to: file2)

        // Near duplicate: same size, but differs at the very end
        var nearData = largeData
        nearData[size - 1] = 0xFF
        let fileNear = tempDir.appendingPathComponent("large_near.bin")
        try nearData.write(to: fileNear)

        let hash1 = try DuplicateFinder.computeSHA256(for: file1)
        let hash2 = try DuplicateFinder.computeSHA256(for: file2)
        let hashNear = try DuplicateFinder.computeSHA256(for: fileNear)

        #expect(hash1 == hash2)
        #expect(hash1 != hashNear)

        let clusters = try await DuplicateFinder.findDuplicates(in: [file1, file2, fileNear])
        #expect(clusters.count == 1)
        #expect(clusters.first?.fileURLs.count == 2)
        #expect(clusters.first?.fileURLs.contains(file1) == true)
        #expect(clusters.first?.fileURLs.contains(file2) == true)
        #expect(clusters.first?.fileURLs.contains(fileNear) == false)
    }

    @Test("Empty (0-byte) files are handled gracefully: SHA256 matches empty digest, and findDuplicates ignores zero-byte files")
    func testEmptyFilesHandling() async throws {
        let tempDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("EmptyFileTest_\(UUID().uuidString)")
            .standardizedFileURL
            .resolvingSymlinksInPath()
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: tempDir) }

        let empty1 = tempDir.appendingPathComponent("empty1.dat")
        let empty2 = tempDir.appendingPathComponent("empty2.dat")
        try Data().write(to: empty1)
        try Data().write(to: empty2)

        // Empty file SHA-256 standard digest
        let hash = try DuplicateFinder.computeSHA256(for: empty1)
        let expectedEmptyHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        #expect(hash == expectedEmptyHash)

        // findDuplicates filters size > 0 to avoid false positives on corrupted/placeholder 0-byte files
        let clusters = try await DuplicateFinder.findDuplicates(in: [empty1, empty2])
        #expect(clusters.isEmpty)
    }

    @Test("Selection directory is ignored during duplicate scan")
    func testSelectionFolderExcluded() async throws {
        let tempDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("SelectionIgnoreTest_\(UUID().uuidString)")
            .standardizedFileURL
            .resolvingSymlinksInPath()
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: tempDir) }

        let rootFile = tempDir.appendingPathComponent("photo.jpg")
        try "image_content".data(using: .utf8)!.write(to: rootFile)

        let selectionDir = tempDir.appendingPathComponent("Selection", isDirectory: true)
        try FileManager.default.createDirectory(at: selectionDir, withIntermediateDirectories: true)
        let selectedFile = selectionDir.appendingPathComponent("photo.jpg")
        try "image_content".data(using: .utf8)!.write(to: selectedFile)

        // Recursive scan of tempDir should ignore Selection/ folder
        let clusters = try await DuplicateFinder.findDuplicates(in: tempDir, recursive: true)
        #expect(clusters.isEmpty)
    }
}
