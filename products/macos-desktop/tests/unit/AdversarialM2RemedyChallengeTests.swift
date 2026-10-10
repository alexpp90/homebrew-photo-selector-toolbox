import Testing
import Foundation
import CryptoKit
@testable import PhotoSelectorKit

@Suite("Adversarial M2 Remedy Challenge Tests")
struct AdversarialM2RemedyChallengeTests {

    // MARK: - Challenge 1: FileCullingManager Self-Move and Self-Copy in Selection/

    @Test("Self-move photo already residing in Selection/: preserves file intact, does not delete, throws no error")
    func testSelfMovePhotoAlreadyInSelection() throws {
        let tempDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("AdversarialSelfMove_\(UUID().uuidString)")
            .standardizedFileURL
            .resolvingSymlinksInPath()
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: tempDir) }

        let selectionDir = tempDir.appendingPathComponent("Selection", isDirectory: true)
        try FileManager.default.createDirectory(at: selectionDir, withIntermediateDirectories: true)

        let photoURL = selectionDir.appendingPathComponent("ALREADY_IN_SELECTION.JPG")
        // Create 8KB of pseudo-random byte data to ensure precise content verification
        var byteData = Data(count: 8192)
        for i in 0..<8192 {
            byteData[i] = UInt8((i * 37 + 13) & 0xFF)
        }
        try byteData.write(to: photoURL)

        let manager = FileCullingManager()

        // 1. Must NOT throw an error
        let movedURLs = try manager.moveToSelection(photoURL: photoURL, rootURL: tempDir)

        // 2. Must return the destination URL
        #expect(movedURLs.count == 1)
        #expect(movedURLs.first?.standardizedFileURL.path == photoURL.standardizedFileURL.path)

        // 3. File must still exist at path
        let fileExists = FileManager.default.fileExists(atPath: photoURL.path)
        #expect(fileExists, "CRITICAL: Photo file was deleted when moving a photo that already resides in Selection/!")

        // 4. File content must be byte-for-byte identical
        let readData = try Data(contentsOf: photoURL)
        #expect(readData == byteData, "CRITICAL: Photo content was corrupted or altered during self-move!")
    }

    @Test("Self-copy photo already residing in Selection/: preserves file intact, does not delete, throws no error")
    func testSelfCopyPhotoAlreadyInSelection() throws {
        let tempDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("AdversarialSelfCopy_\(UUID().uuidString)")
            .standardizedFileURL
            .resolvingSymlinksInPath()
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: tempDir) }

        let selectionDir = tempDir.appendingPathComponent("Selection", isDirectory: true)
        try FileManager.default.createDirectory(at: selectionDir, withIntermediateDirectories: true)

        let photoURL = selectionDir.appendingPathComponent("ALREADY_IN_SELECTION_COPY.RAW")
        var byteData = Data(count: 4096)
        for i in 0..<4096 {
            byteData[i] = UInt8((i * 101 + 7) & 0xFF)
        }
        try byteData.write(to: photoURL)

        let manager = FileCullingManager()

        // 1. Must NOT throw an error
        let copiedURLs = try manager.copyToSelection(photoURL: photoURL, rootURL: tempDir)

        // 2. Must return the destination URL
        #expect(copiedURLs.count == 1)
        #expect(copiedURLs.first?.standardizedFileURL.path == photoURL.standardizedFileURL.path)

        // 3. File must still exist at path
        let fileExists = FileManager.default.fileExists(atPath: photoURL.path)
        #expect(fileExists, "CRITICAL: Photo file was deleted when copying a photo that already resides in Selection/!")

        // 4. File content must be byte-for-byte identical
        let readData = try Data(contentsOf: photoURL)
        #expect(readData == byteData, "CRITICAL: Photo content was corrupted or altered during self-copy!")
    }

    @Test("Companion file handling on self-move in Selection/: primary and all companion files preserved intact")
    func testCompanionFilesSelfMoveInSelection() throws {
        let tempDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("AdversarialCompanionSelfMove_\(UUID().uuidString)")
            .standardizedFileURL
            .resolvingSymlinksInPath()
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: tempDir) }

        let selectionDir = tempDir.appendingPathComponent("Selection", isDirectory: true)
        try FileManager.default.createDirectory(at: selectionDir, withIntermediateDirectories: true)

        // Create primary photo and 5 companion files
        let primaryURL = selectionDir.appendingPathComponent("COMPANION_SET.CR3")
        let companions: [String: Data] = [
            "COMPANION_SET.CR3": "RAW_IMAGE_DATA_\(UUID())".data(using: .utf8)!,
            "COMPANION_SET.JPG": "JPEG_PAIR_DATA_\(UUID())".data(using: .utf8)!,
            "COMPANION_SET.xmp": "XMP_SIDECAR_DATA_\(UUID())".data(using: .utf8)!,
            "COMPANION_SET.CR3.xmp": "DOUBLE_EXT_XMP_DATA_\(UUID())".data(using: .utf8)!,
            "COMPANION_SET-Edit.tif": "LR_EDIT_TIFF_DATA_\(UUID())".data(using: .utf8)!,
            "COMPANION_SET_edit.jpg": "LR_EDIT_UNDERSCORE_DATA_\(UUID())".data(using: .utf8)!
        ]

        // Unrelated photo that should NOT be touched
        let unrelatedURL = selectionDir.appendingPathComponent("UNRELATED_PHOTO.CR3")
        let unrelatedData = "UNRELATED_DATA".data(using: .utf8)!
        try unrelatedData.write(to: unrelatedURL)

        for (filename, data) in companions {
            let fileURL = selectionDir.appendingPathComponent(filename)
            try data.write(to: fileURL)
        }

        let manager = FileCullingManager()

        // Execute self-move
        let movedURLs = try manager.moveToSelection(photoURL: primaryURL, rootURL: tempDir)

        #expect(movedURLs.count == 6)

        // Verify every companion still exists and content is unchanged
        for (filename, expectedData) in companions {
            let fileURL = selectionDir.appendingPathComponent(filename)
            let exists = FileManager.default.fileExists(atPath: fileURL.path)
            #expect(exists, "Companion file \(filename) was deleted during self-move in Selection/!")
            if exists {
                let actualData = try Data(contentsOf: fileURL)
                #expect(actualData == expectedData, "Companion file \(filename) content corrupted during self-move!")
            }
        }

        // Verify unrelated file is intact
        #expect(FileManager.default.fileExists(atPath: unrelatedURL.path))
        #expect(try Data(contentsOf: unrelatedURL) == unrelatedData)
    }

    @Test("Companion file handling on self-copy in Selection/: primary and companion files preserved intact")
    func testCompanionFilesSelfCopyInSelection() throws {
        let tempDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("AdversarialCompanionSelfCopy_\(UUID().uuidString)")
            .standardizedFileURL
            .resolvingSymlinksInPath()
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: tempDir) }

        let selectionDir = tempDir.appendingPathComponent("Selection", isDirectory: true)
        try FileManager.default.createDirectory(at: selectionDir, withIntermediateDirectories: true)

        let primaryURL = selectionDir.appendingPathComponent("COPY_SET.NEF")
        let companions: [String: Data] = [
            "COPY_SET.NEF": "RAW_NEF_DATA".data(using: .utf8)!,
            "COPY_SET.JPG": "JPEG_NEF_PAIR".data(using: .utf8)!,
            "COPY_SET.xmp": "XMP_NEF_SIDECAR".data(using: .utf8)!,
            "COPY_SET-Edit.psd": "LR_EDIT_PSD".data(using: .utf8)!
        ]

        for (filename, data) in companions {
            let fileURL = selectionDir.appendingPathComponent(filename)
            try data.write(to: fileURL)
        }

        let manager = FileCullingManager()

        let copiedURLs = try manager.copyToSelection(photoURL: primaryURL, rootURL: tempDir)

        #expect(copiedURLs.count == 4)

        for (filename, expectedData) in companions {
            let fileURL = selectionDir.appendingPathComponent(filename)
            let exists = FileManager.default.fileExists(atPath: fileURL.path)
            #expect(exists, "Companion file \(filename) was deleted during self-copy in Selection/!")
            if exists {
                let actualData = try Data(contentsOf: fileURL)
                #expect(actualData == expectedData, "Companion file \(filename) content corrupted during self-copy!")
            }
        }
    }

    @Test("Repeated culling actions (idempotence): multiple moveToSelection calls on same Selection/ photo succeed")
    func testRepeatedMoveToSelectionIdempotency() throws {
        let tempDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("AdversarialIdempotency_\(UUID().uuidString)")
            .standardizedFileURL
            .resolvingSymlinksInPath()
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: tempDir) }

        let selectionDir = tempDir.appendingPathComponent("Selection", isDirectory: true)
        try FileManager.default.createDirectory(at: selectionDir, withIntermediateDirectories: true)

        let photoURL = selectionDir.appendingPathComponent("RAPID_FIRE_M.JPG")
        let originalBytes = "RAPID_FIRE_KEYPRESS_M".data(using: .utf8)!
        try originalBytes.write(to: photoURL)

        let manager = FileCullingManager()

        // Simulate user repeatedly hitting 'M' shortcut on already-selected photo
        for _ in 0..<5 {
            let result = try manager.moveToSelection(photoURL: photoURL, rootURL: tempDir)
            #expect(result.count == 1)
            #expect(FileManager.default.fileExists(atPath: photoURL.path))
            #expect(try Data(contentsOf: photoURL) == originalBytes)
        }
    }

    @Test("Self-move with symlinked paths: preserves file intact without deletion")
    func testSelfMoveWithSymlinkedDirectory() throws {
        let tempDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("AdversarialSymlink_\(UUID().uuidString)")
            .standardizedFileURL
            .resolvingSymlinksInPath()
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: tempDir) }

        let realSelectionDir = tempDir.appendingPathComponent("Selection", isDirectory: true)
        try FileManager.default.createDirectory(at: realSelectionDir, withIntermediateDirectories: true)

        let realPhotoURL = realSelectionDir.appendingPathComponent("SYMLINK_PHOTO.JPG")
        let photoBytes = "SYMLINK_PHOTO_BYTES".data(using: .utf8)!
        try photoBytes.write(to: realPhotoURL)

        // Create symlink to tempDir
        let symlinkRootDir = tempDir.appendingPathComponent("SymlinkedRoot")
        try FileManager.default.createSymbolicLink(at: symlinkRootDir, withDestinationURL: tempDir)

        let symlinkPhotoURL = symlinkRootDir.appendingPathComponent("Selection").appendingPathComponent("SYMLINK_PHOTO.JPG")

        let manager = FileCullingManager()
        let result = try manager.moveToSelection(photoURL: symlinkPhotoURL, rootURL: symlinkRootDir)

        #expect(result.count == 1)
        #expect(FileManager.default.fileExists(atPath: realPhotoURL.path))
        #expect(try Data(contentsOf: realPhotoURL) == photoBytes)
    }

    // MARK: - Challenge 2: DuplicateFinder Substring Directory Scanning

    @Test("Root directory with folder name containing 'Selection' as substring (Trip_Selection_Final/) is scanned cleanly")
    func testScanRootDirectoryWithSelectionSubstringInName() async throws {
        let baseTemp = FileManager.default.temporaryDirectory
            .standardizedFileURL
            .resolvingSymlinksInPath()
        let rootURL = baseTemp.appendingPathComponent("Trip_Selection_Final_\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: rootURL, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: rootURL) }

        // Create duplicate photos inside root
        let photo1 = rootURL.appendingPathComponent("DSC0010.JPG")
        let photo2 = rootURL.appendingPathComponent("DSC0010_backup.JPG")
        let dupContent = "TRIP_SELECTION_FINAL_DUPLICATE_BYTES_123456789".data(using: .utf8)!
        try dupContent.write(to: photo1)
        try dupContent.write(to: photo2)

        // Unique photo inside root
        let uniquePhoto = rootURL.appendingPathComponent("DSC0011.JPG")
        try "UNIQUE_PHOTO_BYTES".data(using: .utf8)!.write(to: uniquePhoto)

        // Non-recursive scan
        let nonRecursiveClusters = try await DuplicateFinder.findDuplicates(in: rootURL, recursive: false)
        #expect(nonRecursiveClusters.count == 1, "Candidate photos in Trip_Selection_Final/ were falsely discarded during non-recursive scan!")
        #expect(nonRecursiveClusters.first?.fileURLs.count == 2)
        #expect(nonRecursiveClusters.first?.fileURLs.contains(photo1) == true)
        #expect(nonRecursiveClusters.first?.fileURLs.contains(photo2) == true)

        // Recursive scan
        let recursiveClusters = try await DuplicateFinder.findDuplicates(in: rootURL, recursive: true)
        #expect(recursiveClusters.count == 1, "Candidate photos in Trip_Selection_Final/ were falsely discarded during recursive scan!")
        #expect(recursiveClusters.first?.fileURLs.count == 2)
        #expect(recursiveClusters.first?.fileURLs.contains(photo1) == true)
        #expect(recursiveClusters.first?.fileURLs.contains(photo2) == true)
    }

    @Test("Subdirectory with folder name containing 'Selection' as substring (e.g. Sub_Trip_Selection_Final/) is scanned during recursive scan")
    func testScanSubdirectoryWithSelectionSubstringInName() async throws {
        let baseTemp = FileManager.default.temporaryDirectory
            .standardizedFileURL
            .resolvingSymlinksInPath()
        let rootURL = baseTemp.appendingPathComponent("MainLibrary_\(UUID().uuidString)", isDirectory: true)
        let subfolderURL = rootURL.appendingPathComponent("Trip_Selection_Final", isDirectory: true)
        try FileManager.default.createDirectory(at: subfolderURL, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: rootURL) }

        // Duplicate pair inside subfolder
        let subPhoto1 = subfolderURL.appendingPathComponent("SUB_IMG_001.JPG")
        let subPhoto2 = subfolderURL.appendingPathComponent("SUB_IMG_001_copy.JPG")
        let dupContent = "SUBFOLDER_DUPLICATE_PAYLOAD".data(using: .utf8)!
        try dupContent.write(to: subPhoto1)
        try dupContent.write(to: subPhoto2)

        let clusters = try await DuplicateFinder.findDuplicates(in: rootURL, recursive: true)
        #expect(clusters.count == 1, "Subfolder 'Trip_Selection_Final' was falsely skipped by recursive scan!")
        #expect(clusters.first?.fileURLs.count == 2)
        #expect(clusters.first?.fileURLs.contains(subPhoto1) == true)
        #expect(clusters.first?.fileURLs.contains(subPhoto2) == true)
    }

    @Test("Various folder names containing 'Selection' as substring are all scanned, while true 'Selection' folder is excluded")
    func testMultipleSubstringFolderVariationsAlongsideTrueSelectionFolder() async throws {
        let baseTemp = FileManager.default.temporaryDirectory
            .standardizedFileURL
            .resolvingSymlinksInPath()
        let rootURL = baseTemp.appendingPathComponent("ComprehensiveTest_\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: rootURL, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: rootURL) }

        let identicalContent = "MULTI_SUBSTRING_TEST_DATA".data(using: .utf8)!

        // 1. Root file
        let rootFile = rootURL.appendingPathComponent("root_photo.jpg")
        try identicalContent.write(to: rootFile)

        // 2. Folder: "PreSelection"
        let preSelectionDir = rootURL.appendingPathComponent("PreSelection", isDirectory: true)
        try FileManager.default.createDirectory(at: preSelectionDir, withIntermediateDirectories: true)
        let preFile = preSelectionDir.appendingPathComponent("pre_photo.jpg")
        try identicalContent.write(to: preFile)

        // 3. Folder: "Selection2024"
        let sel2024Dir = rootURL.appendingPathComponent("Selection2024", isDirectory: true)
        try FileManager.default.createDirectory(at: sel2024Dir, withIntermediateDirectories: true)
        let sel2024File = sel2024Dir.appendingPathComponent("sel2024_photo.jpg")
        try identicalContent.write(to: sel2024File)

        // 4. Folder: "My_Best_Selections"
        let bestSelDir = rootURL.appendingPathComponent("My_Best_Selections", isDirectory: true)
        try FileManager.default.createDirectory(at: bestSelDir, withIntermediateDirectories: true)
        let bestSelFile = bestSelDir.appendingPathComponent("best_photo.jpg")
        try identicalContent.write(to: bestSelFile)

        // 5. TRUE app Selection folder (must be excluded from scan)
        let trueSelectionDir = rootURL.appendingPathComponent("Selection", isDirectory: true)
        try FileManager.default.createDirectory(at: trueSelectionDir, withIntermediateDirectories: true)
        let excludedFile = trueSelectionDir.appendingPathComponent("culled_photo.jpg")
        try identicalContent.write(to: excludedFile)

        let clusters = try await DuplicateFinder.findDuplicates(in: rootURL, recursive: true)

        #expect(clusters.count == 1)
        guard let cluster = clusters.first else {
            #expect(Bool(false), "Expected 1 duplicate cluster but got none")
            return
        }

        // Exactly 4 files should be clustered (root + PreSelection + Selection2024 + My_Best_Selections)
        #expect(cluster.fileURLs.count == 4)
        #expect(cluster.fileURLs.contains(rootFile))
        #expect(cluster.fileURLs.contains(preFile))
        #expect(cluster.fileURLs.contains(sel2024File))
        #expect(cluster.fileURLs.contains(bestSelFile))

        // True Selection/ folder file MUST NOT be in the cluster
        #expect(!cluster.fileURLs.contains(excludedFile), "File inside true Selection/ folder was falsely included!")
    }

    @Test("Direct scan of Selection/ as rootURL scans duplicates within it")
    func testDirectScanOfSelectionDirectoryAsRoot() async throws {
        let baseTemp = FileManager.default.temporaryDirectory
            .standardizedFileURL
            .resolvingSymlinksInPath()
        let selectionRoot = baseTemp.appendingPathComponent("Selection", isDirectory: true)
        try FileManager.default.createDirectory(at: selectionRoot, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: selectionRoot) }

        let file1 = selectionRoot.appendingPathComponent("selected_dup_1.jpg")
        let file2 = selectionRoot.appendingPathComponent("selected_dup_2.jpg")
        let content = "DIRECT_SELECTION_ROOT_DATA".data(using: .utf8)!
        try content.write(to: file1)
        try content.write(to: file2)

        // When the user explicitly picks the Selection folder as root, duplicates inside should be found
        let clusters = try await DuplicateFinder.findDuplicates(in: selectionRoot, recursive: false)
        #expect(clusters.count == 1)
        #expect(clusters.first?.fileURLs.count == 2)
    }

    @Test("DuplicateFinder handles Task cancellation cooperatively without leaking or hanging")
    func testDuplicateFinderCancellation() async throws {
        let tempDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("CancelTest_\(UUID().uuidString)")
            .standardizedFileURL
            .resolvingSymlinksInPath()
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: tempDir) }

        var fileURLs: [URL] = []
        for i in 0..<10 {
            let u = tempDir.appendingPathComponent("cancel_\(i).dat")
            try Data(repeating: UInt8(i), count: 1024).write(to: u)
            fileURLs.append(u)
        }

        let task = Task {
            try await DuplicateFinder.findDuplicates(in: fileURLs)
        }
        task.cancel()

        do {
            _ = try await task.value
            // Cancellation might complete if fast, but should not hang
        } catch is CancellationError {
            // Cooperative cancellation verified
        } catch {
            Issue.record("Unexpected error during cancellation: \(error)")
        }
    }
}
