import Testing
import Foundation
@testable import PhotoSelectorKit

@Suite("FileCullingManager Unit Tests")
struct FileCullingManagerTests {

    @Test("REQ-MAC-EXIF.05, REQ-MAC-CULL.05: Companion file discovery finds RAW+JPEG, XMP sidecars, and Lightroom edits")
    func testCompanionFileDiscovery() throws {
        let tempDir = FileManager.default.temporaryDirectory.appendingPathComponent("CullingTest_\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: tempDir) }

        let primaryURL = tempDir.appendingPathComponent("DSC0001.ARW")
        let jpegPairURL = tempDir.appendingPathComponent("DSC0001.JPG")
        let xmpURL = tempDir.appendingPathComponent("DSC0001.xmp")
        let doubleXmpURL = tempDir.appendingPathComponent("DSC0001.ARW.xmp")
        let lrEditTif = tempDir.appendingPathComponent("DSC0001-Edit.tif")
        let lrEditXmp = tempDir.appendingPathComponent("DSC0001-Edit.xmp")
        let unrelatedURL = tempDir.appendingPathComponent("DSC0002.ARW")

        let dummyData = "test".data(using: .utf8)!
        try dummyData.write(to: primaryURL)
        try dummyData.write(to: jpegPairURL)
        try dummyData.write(to: xmpURL)
        try dummyData.write(to: doubleXmpURL)
        try dummyData.write(to: lrEditTif)
        try dummyData.write(to: lrEditXmp)
        try dummyData.write(to: unrelatedURL)

        let manager = FileCullingManager()
        let companions = manager.findCompanionFiles(for: primaryURL)

        #expect(companions.first == primaryURL)
        #expect(companions.count == 6)
        #expect(companions.contains(jpegPairURL))
        #expect(companions.contains(xmpURL))
        #expect(companions.contains(doubleXmpURL))
        #expect(companions.contains(lrEditTif))
        #expect(companions.contains(lrEditXmp))
        #expect(!companions.contains(unrelatedURL))
    }

    @Test("REQ-MAC-CULL.05: Companions of a symlinked photo are its neighbours, not the link target's")
    func testCompanionsOfSymlinkedPrimary() throws {
        let base = FileManager.default.temporaryDirectory.appendingPathComponent("CullingSymlink_\(UUID().uuidString)")
        let culled = base.appendingPathComponent("card")
        let elsewhere = base.appendingPathComponent("archive")
        try FileManager.default.createDirectory(at: culled, withIntermediateDirectories: true)
        try FileManager.default.createDirectory(at: elsewhere, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: base) }

        let dummy = Data("x".utf8)
        let target = elsewhere.appendingPathComponent("DSC0001.ARW")
        try dummy.write(to: target)
        try dummy.write(to: elsewhere.appendingPathComponent("DSC0001.xmp"))
        let link = culled.appendingPathComponent("DSC0001.ARW")
        try FileManager.default.createSymbolicLink(at: link, withDestinationURL: target)
        let jpeg = culled.appendingPathComponent("DSC0001.JPG")
        try dummy.write(to: jpeg)

        let companions = FileCullingManager().findCompanionFiles(for: link)
        #expect(companions.map(\.lastPathComponent) == ["DSC0001.ARW", "DSC0001.JPG"])
        #expect(companions.allSatisfy { $0.deletingLastPathComponent().lastPathComponent == "card" })
    }

    @Test("REQ-MAC-CULL.04, REQ-MAC-CULL.05: Copy to Selection copies primary and companion files preserving originals")
    func testCopyToSelection() throws {
        let tempDir = FileManager.default.temporaryDirectory.appendingPathComponent("CopyTest_\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: tempDir) }

        let primaryURL = tempDir.appendingPathComponent("PHOTO_A.CR3")
        let companionURL = tempDir.appendingPathComponent("PHOTO_A.xmp")
        let dummy = "content".data(using: .utf8)!
        try dummy.write(to: primaryURL)
        try dummy.write(to: companionURL)

        let manager = FileCullingManager()
        let copied = try manager.copyToSelection(photoURL: primaryURL, rootURL: tempDir)

        #expect(copied.count == 2)
        let selectionDir = tempDir.appendingPathComponent("Selection", isDirectory: true)
        #expect(FileManager.default.fileExists(atPath: selectionDir.appendingPathComponent("PHOTO_A.CR3").path))
        #expect(FileManager.default.fileExists(atPath: selectionDir.appendingPathComponent("PHOTO_A.xmp").path))

        // Originals still present
        #expect(FileManager.default.fileExists(atPath: primaryURL.path))
        #expect(FileManager.default.fileExists(atPath: companionURL.path))
    }

    @Test("REQ-MAC-CULL.05: Move to Selection moves primary and companion files into Selection folder")
    func testMoveToSelection() throws {
        let tempDir = FileManager.default.temporaryDirectory.appendingPathComponent("MoveTest_\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: tempDir) }

        let primaryURL = tempDir.appendingPathComponent("PHOTO_B.NEF")
        let companionURL = tempDir.appendingPathComponent("PHOTO_B.JPG")
        let dummy = "content".data(using: .utf8)!
        try dummy.write(to: primaryURL)
        try dummy.write(to: companionURL)

        let manager = FileCullingManager()
        let moved = try manager.moveToSelection(photoURL: primaryURL, rootURL: tempDir)

        #expect(moved.count == 2)
        let selectionDir = tempDir.appendingPathComponent("Selection", isDirectory: true)
        #expect(FileManager.default.fileExists(atPath: selectionDir.appendingPathComponent("PHOTO_B.NEF").path))
        #expect(FileManager.default.fileExists(atPath: selectionDir.appendingPathComponent("PHOTO_B.JPG").path))

        // Originals should no longer exist at original locations
        #expect(!FileManager.default.fileExists(atPath: primaryURL.path))
        #expect(!FileManager.default.fileExists(atPath: companionURL.path))
    }

    @Test("REQ-MAC-CULL.05: Move to Trash trashes primary and companion files")
    func testMoveToTrash() throws {
        let tempDir = FileManager.default.temporaryDirectory.appendingPathComponent("TrashTest_\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: tempDir) }

        let primaryURL = tempDir.appendingPathComponent("PHOTO_C.DNG")
        let companionURL = tempDir.appendingPathComponent("PHOTO_C.xmp")
        let dummy = "content".data(using: .utf8)!
        try dummy.write(to: primaryURL)
        try dummy.write(to: companionURL)

        let manager = FileCullingManager()
        let trashed = try manager.moveToTrash(photoURL: primaryURL)

        #expect(trashed.count == 2)
        #expect(!FileManager.default.fileExists(atPath: primaryURL.path))
        #expect(!FileManager.default.fileExists(atPath: companionURL.path))
    }

    // MARK: - Data Safety & Re-cull Regressions

    @Test("Moving file already residing in Selection/ is a safe no-op and never deletes file")
    func testMoveToSelectionWhenFileAlreadyInSelection() throws {
        let tempDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("SelfMoveTest_\(UUID().uuidString)")
            .standardizedFileURL
            .resolvingSymlinksInPath()
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: tempDir) }

        let selectionDir = tempDir.appendingPathComponent("Selection", isDirectory: true)
        try FileManager.default.createDirectory(at: selectionDir, withIntermediateDirectories: true)

        let existingPhoto = selectionDir.appendingPathComponent("EXISTING_PHOTO.ARW")
        let content = "PRECIOUS_RAW_DATA".data(using: .utf8)!
        try content.write(to: existingPhoto)

        let manager = FileCullingManager()
        let result = try manager.moveToSelection(photoURL: existingPhoto, rootURL: tempDir)

        #expect(FileManager.default.fileExists(atPath: existingPhoto.path), "CRITICAL REGRESSION: File was deleted when moving a file already in Selection folder!")
        #expect(result.contains(existingPhoto))
        let remainingData = try Data(contentsOf: existingPhoto)
        #expect(remainingData == content)
    }

    @Test("Copying file already residing in Selection/ preserves file intact without deletion")
    func testCopyToSelectionWhenFileAlreadyInSelection() throws {
        let tempDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("SelfCopyTest_\(UUID().uuidString)")
            .standardizedFileURL
            .resolvingSymlinksInPath()
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: tempDir) }

        let selectionDir = tempDir.appendingPathComponent("Selection", isDirectory: true)
        try FileManager.default.createDirectory(at: selectionDir, withIntermediateDirectories: true)

        let existingPhoto = selectionDir.appendingPathComponent("ALREADY_COPIED.JPG")
        let content = "PRESERVED_CONTENT".data(using: .utf8)!
        try content.write(to: existingPhoto)

        let manager = FileCullingManager()
        let result = try manager.copyToSelection(photoURL: existingPhoto, rootURL: tempDir)

        #expect(FileManager.default.fileExists(atPath: existingPhoto.path))
        #expect(result.contains(existingPhoto))
        let remainingData = try Data(contentsOf: existingPhoto)
        #expect(remainingData == content)
    }

    @Test("Moving photo and companions already in Selection/ safely preserves all companion files")
    func testMoveCompanionsAlreadyInSelection() throws {
        let tempDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("SelfMoveCompanionsTest_\(UUID().uuidString)")
            .standardizedFileURL
            .resolvingSymlinksInPath()
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: tempDir) }

        let selectionDir = tempDir.appendingPathComponent("Selection", isDirectory: true)
        try FileManager.default.createDirectory(at: selectionDir, withIntermediateDirectories: true)

        let rawURL = selectionDir.appendingPathComponent("PHOTO_01.CR3")
        let xmpURL = selectionDir.appendingPathComponent("PHOTO_01.xmp")
        try "RAW_DATA".data(using: .utf8)!.write(to: rawURL)
        try "XMP_DATA".data(using: .utf8)!.write(to: xmpURL)

        let manager = FileCullingManager()
        let moved = try manager.moveToSelection(photoURL: rawURL, rootURL: tempDir)

        #expect(moved.count == 2)
        #expect(FileManager.default.fileExists(atPath: rawURL.path))
        #expect(FileManager.default.fileExists(atPath: xmpURL.path))
    }

    @Test("Move to Selection rolls back already-moved primary file when companion move fails")
    func testMoveToSelectionTransactionalRollbackOnCompanionFailure() throws {
        final class FailingSecondMoveFileManager: FileManager {
            var moveTargetCallCount = 0
            var failOnFilename: String = ""

            override func moveItem(at srcURL: URL, to dstURL: URL) throws {
                if srcURL.lastPathComponent == failOnFilename {
                    throw NSError(domain: NSCocoaErrorDomain, code: NSFileWriteNoPermissionError, userInfo: [NSLocalizedDescriptionKey: "Simulated companion move error"])
                }
                try super.moveItem(at: srcURL, to: dstURL)
            }
        }

        let tempDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("MoveRollbackTest_\(UUID().uuidString)")
            .standardizedFileURL
            .resolvingSymlinksInPath()
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: tempDir) }

        let rawURL = tempDir.appendingPathComponent("ROLLBACK_01.CR3")
        let jpgURL = tempDir.appendingPathComponent("ROLLBACK_01.JPG")
        try "RAW_BYTES".data(using: .utf8)!.write(to: rawURL)
        try "JPG_BYTES".data(using: .utf8)!.write(to: jpgURL)

        let fm = FailingSecondMoveFileManager()
        fm.failOnFilename = "ROLLBACK_01.JPG"
        let manager = FileCullingManager(fileManager: fm)

        #expect(throws: FileCullingError.self) {
            try manager.moveToSelection(photoURL: rawURL, rootURL: tempDir)
        }

        // Primary file must be rolled back to its source location
        #expect(FileManager.default.fileExists(atPath: rawURL.path))
        #expect(FileManager.default.fileExists(atPath: jpgURL.path))
        let selectionRaw = tempDir.appendingPathComponent("Selection/ROLLBACK_01.CR3")
        #expect(!FileManager.default.fileExists(atPath: selectionRaw.path))
    }

    @Test("Copy to Selection removes newly copied primary file and restores overwritten destination when companion copy fails")
    func testCopyToSelectionTransactionalRollbackOnCompanionFailure() throws {
        final class FailingSecondCopyFileManager: FileManager {
            var failOnFilename: String = ""

            override func copyItem(at srcURL: URL, to dstURL: URL) throws {
                if srcURL.lastPathComponent == failOnFilename {
                    throw NSError(domain: NSCocoaErrorDomain, code: NSFileWriteOutOfSpaceError, userInfo: [NSLocalizedDescriptionKey: "Simulated companion copy error"])
                }
                try super.copyItem(at: srcURL, to: dstURL)
            }
        }

        let tempDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("CopyRollbackTest_\(UUID().uuidString)")
            .standardizedFileURL
            .resolvingSymlinksInPath()
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: tempDir) }

        let rawURL = tempDir.appendingPathComponent("ROLLBACK_02.CR3")
        let jpgURL = tempDir.appendingPathComponent("ROLLBACK_02.JPG")
        try "NEW_RAW_BYTES".data(using: .utf8)!.write(to: rawURL)
        try "NEW_JPG_BYTES".data(using: .utf8)!.write(to: jpgURL)

        let selectionDir = tempDir.appendingPathComponent("Selection", isDirectory: true)
        try FileManager.default.createDirectory(at: selectionDir, withIntermediateDirectories: true)
        let preExistingRaw = selectionDir.appendingPathComponent("ROLLBACK_02.CR3")
        let oldContent = "EXISTING_DESTINATION_RAW".data(using: .utf8)!
        try oldContent.write(to: preExistingRaw)

        let fm = FailingSecondCopyFileManager()
        fm.failOnFilename = "ROLLBACK_02.JPG"
        let manager = FileCullingManager(fileManager: fm)

        #expect(throws: FileCullingError.self) {
            try manager.copyToSelection(photoURL: rawURL, rootURL: tempDir)
        }

        // Source files remain intact, and pre-existing Selection file is restored from backup
        #expect(FileManager.default.fileExists(atPath: rawURL.path))
        #expect(FileManager.default.fileExists(atPath: jpgURL.path))
        #expect(FileManager.default.fileExists(atPath: preExistingRaw.path))
        #expect(try Data(contentsOf: preExistingRaw) == oldContent)
    }
}

