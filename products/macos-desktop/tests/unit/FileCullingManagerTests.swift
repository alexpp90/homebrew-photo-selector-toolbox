import Testing
import Foundation
@testable import PhotoSelectorKit

@Suite("FileCullingManager Unit Tests")
struct FileCullingManagerTests {

    @Test("Companion file discovery finds RAW+JPEG, XMP sidecars, and Lightroom edits")
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

    @Test("Copy to Selection copies primary and companion files preserving originals")
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

    @Test("Move to Selection moves primary and companion files into Selection folder")
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

    @Test("Move to Trash trashes primary and companion files")
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
}
