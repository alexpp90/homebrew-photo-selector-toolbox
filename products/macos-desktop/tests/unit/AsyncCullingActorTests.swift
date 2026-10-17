import Testing
import Foundation
@testable import PhotoSelectorKit

@Suite("AsyncCullingActor Unit Tests")
struct AsyncCullingActorTests {

    private func createTempDir(prefix: String) throws -> URL {
        let tempBase = FileManager.default.temporaryDirectory
            .standardizedFileURL
            .resolvingSymlinksInPath()
        let dir = tempBase.appendingPathComponent("\(prefix)_\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    @Test("REQ-MAC-CULL.01, REQ-MAC-CULL.03: AsyncCullingActor: Move to Selection executes asynchronously on background actor")
    func testMoveToSelection() async throws {
        let root = try createTempDir(prefix: "AsyncMoveTest")
        defer { try? FileManager.default.removeItem(at: root) }

        let photoURL = root.appendingPathComponent("TEST_001.JPG")
        let xmpURL = root.appendingPathComponent("TEST_001.xmp")
        try "JPEG".data(using: .utf8)!.write(to: photoURL)
        try "XMP".data(using: .utf8)!.write(to: xmpURL)

        let actor = AsyncCullingActor()
        let job = CullingJob(
            photoID: UUID(),
            actionType: .move,
            primaryURL: photoURL,
            rootURL: root
        )

        let record = try await actor.enqueue(job)
        #expect(record != nil)
        #expect(record?.actionType == .move)
        #expect(record?.affectedURLs.count == 2)

        let selDir = root.appendingPathComponent("Selection", isDirectory: true)
        #expect(FileManager.default.fileExists(atPath: selDir.appendingPathComponent("TEST_001.JPG").path))
        #expect(FileManager.default.fileExists(atPath: selDir.appendingPathComponent("TEST_001.xmp").path))
        #expect(!FileManager.default.fileExists(atPath: photoURL.path))
    }

    @Test("REQ-MAC-CULL.01, REQ-MAC-CULL.03: AsyncCullingActor: Copy to Selection preserves originals")
    func testCopyToSelection() async throws {
        let root = try createTempDir(prefix: "AsyncCopyTest")
        defer { try? FileManager.default.removeItem(at: root) }

        let photoURL = root.appendingPathComponent("TEST_002.JPG")
        try "JPEG".data(using: .utf8)!.write(to: photoURL)

        let actor = AsyncCullingActor()
        let job = CullingJob(
            photoID: UUID(),
            actionType: .copy,
            primaryURL: photoURL,
            rootURL: root
        )

        let record = try await actor.enqueue(job)
        #expect(record != nil)

        let selDir = root.appendingPathComponent("Selection", isDirectory: true)
        #expect(FileManager.default.fileExists(atPath: selDir.appendingPathComponent("TEST_002.JPG").path))
        #expect(FileManager.default.fileExists(atPath: photoURL.path), "Original file must be preserved on copy")
    }

    @Test("REQ-MAC-CULL.02: AsyncCullingActor: Move to Trash moves files safely")
    func testMoveToTrash() async throws {
        let root = try createTempDir(prefix: "AsyncTrashTest")
        defer { try? FileManager.default.removeItem(at: root) }

        let photoURL = root.appendingPathComponent("TEST_003.JPG")
        try "JPEG".data(using: .utf8)!.write(to: photoURL)

        let actor = AsyncCullingActor(trashService: DirectoryTrashService(rootDirectory: root))
        let job = CullingJob(
            photoID: UUID(),
            actionType: .trash,
            primaryURL: photoURL,
            rootURL: nil
        )

        let record = try await actor.enqueue(job)
        #expect(record != nil)
        #expect(!FileManager.default.fileExists(atPath: photoURL.path))
    }

    @Test("AsyncCullingActor: Pre-execution cancellation skips disk I/O")
    func testJobCancellation() async throws {
        let root = try createTempDir(prefix: "AsyncCancelTest")
        defer { try? FileManager.default.removeItem(at: root) }

        let photoURL = root.appendingPathComponent("TEST_004.JPG")
        try "JPEG".data(using: .utf8)!.write(to: photoURL)

        let actor = AsyncCullingActor()
        let job = CullingJob(
            photoID: UUID(),
            actionType: .move,
            primaryURL: photoURL,
            rootURL: root
        )

        await actor.cancelJob(id: job.id)
        let record = try await actor.enqueue(job)

        #expect(record == nil)
        #expect(FileManager.default.fileExists(atPath: photoURL.path), "File should not be moved when cancelled")
    }

    @Test("REQ-MAC-CULL.04: AsyncCullingActor: Undo reverses move operation cleanly")
    func testUndoMove() async throws {
        let root = try createTempDir(prefix: "AsyncUndoMoveTest")
        defer { try? FileManager.default.removeItem(at: root) }

        let photoURL = root.appendingPathComponent("TEST_005.JPG")
        try "JPEG".data(using: .utf8)!.write(to: photoURL)

        let actor = AsyncCullingActor()
        let job = CullingJob(
            photoID: UUID(),
            actionType: .move,
            primaryURL: photoURL,
            rootURL: root
        )

        guard let record = try await actor.enqueue(job) else {
            #expect(Bool(false), "Move job failed to produce record")
            return
        }

        let selDir = root.appendingPathComponent("Selection", isDirectory: true)
        #expect(!FileManager.default.fileExists(atPath: photoURL.path))
        #expect(FileManager.default.fileExists(atPath: selDir.appendingPathComponent("TEST_005.JPG").path))

        // Undo move
        let restored = try await actor.undo(record: record)
        #expect(restored.count == 1)
        #expect(FileManager.default.fileExists(atPath: photoURL.path), "File should be restored to original root")
        #expect(!FileManager.default.fileExists(atPath: selDir.appendingPathComponent("TEST_005.JPG").path))
    }

    @Test("REQ-MAC-CULL.04: AsyncCullingActor: Undo reverses copy operation by removing copy from Selection")
    func testUndoCopy() async throws {
        let root = try createTempDir(prefix: "AsyncUndoCopyTest")
        defer { try? FileManager.default.removeItem(at: root) }

        let photoURL = root.appendingPathComponent("TEST_006.JPG")
        try "JPEG".data(using: .utf8)!.write(to: photoURL)

        let actor = AsyncCullingActor()
        let job = CullingJob(
            photoID: UUID(),
            actionType: .copy,
            primaryURL: photoURL,
            rootURL: root
        )

        guard let record = try await actor.enqueue(job) else {
            #expect(Bool(false), "Copy job failed to produce record")
            return
        }

        let selDir = root.appendingPathComponent("Selection", isDirectory: true)
        #expect(FileManager.default.fileExists(atPath: selDir.appendingPathComponent("TEST_006.JPG").path))

        // Undo copy
        let restored = try await actor.undo(record: record)
        #expect(restored.count == 1)
        #expect(!FileManager.default.fileExists(atPath: selDir.appendingPathComponent("TEST_006.JPG").path))
        #expect(FileManager.default.fileExists(atPath: photoURL.path), "Original file should remain intact")
    }

    @Test("AsyncCullingActor: Enqueue caches completed execution record on actor")
    func testEnqueueCachesCompletedRecord() async throws {
        let root = try createTempDir(prefix: "AsyncCacheTest")
        defer { try? FileManager.default.removeItem(at: root) }

        let photoURL = root.appendingPathComponent("TEST_CACHE.JPG")
        try "PAYLOAD".data(using: .utf8)!.write(to: photoURL)

        let actor = AsyncCullingActor()
        let job = CullingJob(
            photoID: UUID(),
            actionType: .move,
            primaryURL: photoURL,
            rootURL: root
        )

        let record = try await actor.enqueue(job)
        #expect(record != nil)
        let hasRecord = await actor.hasCompletedRecord(for: job.id)
        #expect(hasRecord == true)
    }

    @Test("AsyncCullingActor: cancelOrUndo restores completed move using actor cache even if fallbackRecord is empty")
    func testCancelOrUndoRestoresFromActorCache() async throws {
        let root = try createTempDir(prefix: "AsyncCancelOrUndoTest")
        defer { try? FileManager.default.removeItem(at: root) }

        let photoURL = root.appendingPathComponent("TEST_RESTORE.JPG")
        try "PAYLOAD".data(using: .utf8)!.write(to: photoURL)

        let actor = AsyncCullingActor()
        let job = CullingJob(
            photoID: UUID(),
            actionType: .move,
            primaryURL: photoURL,
            rootURL: root
        )

        // 1. Enqueue executes on disk
        let record = try await actor.enqueue(job)
        #expect(record != nil)
        #expect(!FileManager.default.fileExists(atPath: photoURL.path))

        // 2. Fallback record simulates empty optimistic record
        let emptyFallback = CullingExecutionRecord(
            jobID: job.id,
            photoID: job.photoID,
            actionType: .move,
            originalPrimaryURL: photoURL,
            affectedURLs: [],
            previousStatus: .candidate
        )

        // 3. cancelOrUndo must use actor's cached record and reverse move
        let restored = try await actor.cancelOrUndo(jobID: job.id, fallbackRecord: emptyFallback)
        #expect(restored.count == 1)
        #expect(FileManager.default.fileExists(atPath: photoURL.path), "Original file must be restored")
        let selDir = root.appendingPathComponent("Selection", isDirectory: true)
        #expect(!FileManager.default.fileExists(atPath: selDir.appendingPathComponent("TEST_RESTORE.JPG").path))

        let hasRecordAfterUndo = await actor.hasCompletedRecord(for: job.id)
        #expect(hasRecordAfterUndo == false)
    }

    @Test("AsyncCullingActor: cancelOrUndo prior to execution cleanly skips disk I/O")
    func testCancelOrUndoPriorToExecution() async throws {
        let root = try createTempDir(prefix: "AsyncPreExecutionCancelTest")
        defer { try? FileManager.default.removeItem(at: root) }

        let photoURL = root.appendingPathComponent("TEST_PRE_CANCEL.JPG")
        try "PAYLOAD".data(using: .utf8)!.write(to: photoURL)

        let actor = AsyncCullingActor()
        let job = CullingJob(
            photoID: UUID(),
            actionType: .move,
            primaryURL: photoURL,
            rootURL: root
        )

        let emptyFallback = CullingExecutionRecord(
            jobID: job.id,
            photoID: job.photoID,
            actionType: .move,
            originalPrimaryURL: photoURL,
            affectedURLs: [],
            previousStatus: .candidate
        )

        let restored = try await actor.cancelOrUndo(jobID: job.id, fallbackRecord: emptyFallback)
        #expect(restored.isEmpty)

        // Enqueue should now return nil
        let record = try await actor.enqueue(job)
        #expect(record == nil)
        #expect(FileManager.default.fileExists(atPath: photoURL.path), "Original file must never have been touched")
    }

    @Test("AsyncCullingActor: Trash and undo restoration preserves double-extension companion files and original contents")
    func testTrashAndUndoRestorationWithDoubleExtensionCompanion() async throws {
        let root = try createTempDir(prefix: "AsyncTrashDoubleExtTest")
        defer { try? FileManager.default.removeItem(at: root) }

        // 1. Arrange: Create primary RAW photo and double-extension XMP sidecar with distinct contents
        let rawURL = root.appendingPathComponent("DSC0001.ARW")
        let xmpURL = root.appendingPathComponent("DSC0001.ARW.xmp")

        let rawPayload = "RAW_PAYLOAD"
        let xmpMetadata = "XMP_METADATA"

        try rawPayload.data(using: .utf8)!.write(to: rawURL)
        try xmpMetadata.data(using: .utf8)!.write(to: xmpURL)

        // Precondition: Verify initial existence and distinct contents
        #expect(FileManager.default.fileExists(atPath: rawURL.path))
        #expect(FileManager.default.fileExists(atPath: xmpURL.path))
        #expect(try String(contentsOf: rawURL, encoding: .utf8) == rawPayload)
        #expect(try String(contentsOf: xmpURL, encoding: .utf8) == xmpMetadata)

        let actor = AsyncCullingActor(trashService: DirectoryTrashService(rootDirectory: root))
        let job = CullingJob(
            photoID: UUID(),
            actionType: .trash,
            primaryURL: rawURL,
            rootURL: root
        )

        // 2. Act: Enqueue trash operation
        guard let record = try await actor.enqueue(job) else {
            #expect(Bool(false), "Trash job failed to produce execution record")
            return
        }

        // 3. Assert: Verify trash operation
        #expect(record.actionType == .trash)
        #expect(record.affectedURLs.count == 2, "Both primary RAW and double-extension XMP companion must be trashed")
        #expect(!FileManager.default.fileExists(atPath: rawURL.path), "Primary RAW file must be removed from source folder")
        #expect(!FileManager.default.fileExists(atPath: xmpURL.path), "Companion XMP file must be removed from source folder")

        // Verify items reside in Trash
        for trashedURL in record.affectedURLs {
            #expect(FileManager.default.fileExists(atPath: trashedURL.path), "Trashed item must exist in Trash at: \(trashedURL.path)")
        }

        // 4. Act: Execute undo operation
        let restoredURLs = try await actor.undo(record: record)

        // 5. Assert: Verify restoration and file integrity
        #expect(restoredURLs.count == 2, "Undo must restore exactly 2 files")
        #expect(FileManager.default.fileExists(atPath: rawURL.path), "DSC0001.ARW must exist in source folder after undo")
        #expect(FileManager.default.fileExists(atPath: xmpURL.path), "DSC0001.ARW.xmp must exist in source folder after undo")

        // 6. Assert: Verify payloads and ensure neither file overwrote the other
        let restoredRawContent = try String(contentsOf: rawURL, encoding: .utf8)
        let restoredXmpContent = try String(contentsOf: xmpURL, encoding: .utf8)

        #expect(restoredRawContent == rawPayload, "DSC0001.ARW content corrupted: expected '\(rawPayload)', got '\(restoredRawContent)'")
        #expect(restoredXmpContent == xmpMetadata, "DSC0001.ARW.xmp content corrupted: expected '\(xmpMetadata)', got '\(restoredXmpContent)'")
        #expect(rawURL.path != xmpURL.path, "Primary and companion paths must be distinct")
        #expect(restoredRawContent != xmpMetadata, "Primary RAW must NOT have been overwritten by companion XMP metadata")
        #expect(restoredXmpContent != rawPayload, "Companion XMP must NOT have been overwritten by primary RAW payload")
        #expect(restoredRawContent != restoredXmpContent, "Primary and companion files must retain distinct payloads")

        // 7. Assert: Actor state hygiene
        let hasCachedRecord = await actor.hasCompletedRecord(for: job.id)
        #expect(!hasCachedRecord, "Actor must evict completed execution record after undo")
    }

    @Test("Adversarial: Trash and undo restoration across all companion variants (.xmp, .RAW.xmp, -Edit.jpg, .JPG)")
    func testAdversarialTrashAndUndoAcrossAllCompanionConfigurations() async throws {
        let root = try createTempDir(prefix: "AsyncTrashAllCompanionsTest")
        defer { try? FileManager.default.removeItem(at: root) }

        // 1. Arrange: Create primary RAW and all companion variants with distinct payloads
        let files: [(name: String, payload: String)] = [
            ("DSC0001.ARW", "PAYLOAD_PRIMARY_RAW"),
            ("DSC0001.xmp", "PAYLOAD_SINGLE_EXT_XMP"),
            ("DSC0001.ARW.xmp", "PAYLOAD_DOUBLE_EXT_XMP"),
            ("DSC0001-Edit.jpg", "PAYLOAD_LIGHTROOM_EDIT_JPG"),
            ("DSC0001-Edit.xmp", "PAYLOAD_LIGHTROOM_EDIT_XMP"),
            ("DSC0001_Edit.tif", "PAYLOAD_LIGHTROOM_EDIT_TIF"),
            ("DSC0001.JPG", "PAYLOAD_JPEG_PAIR")
        ]

        var urls: [String: URL] = [:]
        for file in files {
            let fileURL = root.appendingPathComponent(file.name)
            try file.payload.data(using: .utf8)!.write(to: fileURL)
            urls[file.name] = fileURL
            #expect(FileManager.default.fileExists(atPath: fileURL.path))
            #expect(try String(contentsOf: fileURL, encoding: .utf8) == file.payload)
        }

        let primaryURL = urls["DSC0001.ARW"]!
        let actor = AsyncCullingActor(trashService: DirectoryTrashService(rootDirectory: root))
        let job = CullingJob(
            photoID: UUID(),
            actionType: .trash,
            primaryURL: primaryURL,
            rootURL: root
        )

        // 2. Act: Enqueue trash operation
        guard let record = try await actor.enqueue(job) else {
            #expect(Bool(false), "Trash job failed to produce execution record")
            return
        }

        // 3. Assert: All files were trashed
        #expect(record.actionType == .trash)
        #expect(record.affectedURLs.count == files.count, "Primary RAW and all 6 companions must be trashed")
        for file in files {
            let fileURL = urls[file.name]!
            #expect(!FileManager.default.fileExists(atPath: fileURL.path), "\(file.name) must be removed from source directory")
        }
        for trashedURL in record.affectedURLs {
            #expect(FileManager.default.fileExists(atPath: trashedURL.path), "Trashed item must exist in Trash: \(trashedURL.path)")
        }

        // 4. Act: Undo trash operation
        let restoredURLs = try await actor.undo(record: record)

        // 5. Assert: All files restored and none collided or overwrote each other
        #expect(restoredURLs.count == files.count, "Undo must restore all \(files.count) files")
        for file in files {
            let fileURL = urls[file.name]!
            #expect(FileManager.default.fileExists(atPath: fileURL.path), "\(file.name) must be restored to source directory")
            let restoredContent = try String(contentsOf: fileURL, encoding: .utf8)
            #expect(restoredContent == file.payload, "Content mismatch for \(file.name): expected '\(file.payload)', got '\(restoredContent)'")
        }

        // Verify primary was not overwritten by any companion
        let restoredPrimary = try String(contentsOf: primaryURL, encoding: .utf8)
        #expect(restoredPrimary == "PAYLOAD_PRIMARY_RAW")
        for file in files where file.name != "DSC0001.ARW" {
            #expect(restoredPrimary != file.payload, "Primary file was corrupted by \(file.name)!")
        }

        let hasCachedRecord = await actor.hasCompletedRecord(for: job.id)
        #expect(!hasCachedRecord, "Actor must evict completed execution record after undo")
    }

    @Test("Adversarial: Undo with simulated macOS trash collision timestamp suffixes never collides with primary")
    func testAdversarialUndoWithTrashDisambiguationSuffixesNeverCollidesWithPrimary() async throws {
        let root = try createTempDir(prefix: "AsyncDisambigTestRoot")
        let trashSimDir = try createTempDir(prefix: "AsyncDisambigTestTrash")
        defer {
            try? FileManager.default.removeItem(at: root)
            try? FileManager.default.removeItem(at: trashSimDir)
        }

        let primaryURL = root.appendingPathComponent("DSC0002.ARW")

        // Test vectors representing macOS disambiguation renaming in Trash
        let testVectors: [(originalName: String, trashedName: String, payload: String)] = [
            ("DSC0002.ARW", "DSC0002 14-22-30-123.ARW", "PAYLOAD_RAW_DISAMBIG"),
            ("DSC0002.xmp", "DSC0002 14-22-30-123.xmp", "PAYLOAD_SINGLE_XMP_DISAMBIG"),
            ("DSC0002.ARW.xmp", "DSC0002.ARW 14-22-30-123.xmp", "PAYLOAD_DOUBLE_XMP_DISAMBIG"),
            ("DSC0002-Edit.jpg", "DSC0002-Edit 14-22-30-123.jpg", "PAYLOAD_EDIT_JPG_DISAMBIG"),
            ("DSC0002.JPG", "DSC0002 2.JPG", "PAYLOAD_JPG_PAIR_DISAMBIG")
        ]

        var trashedURLs: [URL] = []
        var trashedPairs: [TrashedFilePair] = []
        for vector in testVectors {
            let originalFile = root.appendingPathComponent(vector.originalName)
            let trashedFile = trashSimDir.appendingPathComponent(vector.trashedName)
            try vector.payload.data(using: .utf8)!.write(to: trashedFile)
            trashedURLs.append(trashedFile)
            trashedPairs.append(TrashedFilePair(originalURL: originalFile, trashedURL: trashedFile))
        }

        let actor = AsyncCullingActor()
        let record = CullingExecutionRecord(
            jobID: UUID(),
            photoID: UUID(),
            actionType: .trash,
            originalPrimaryURL: primaryURL,
            affectedURLs: trashedURLs,
            trashedPairs: trashedPairs,
            previousStatus: .candidate
        )

        let restoredURLs = try await actor.undo(record: record)
        #expect(restoredURLs.count == testVectors.count, "All disambiguated items must be restored")

        for vector in testVectors {
            let restoredDestination = root.appendingPathComponent(vector.originalName)
            #expect(FileManager.default.fileExists(atPath: restoredDestination.path), "Expected restored file at: \(restoredDestination.path)")
            let content = try String(contentsOf: restoredDestination, encoding: .utf8)
            #expect(content == vector.payload, "Payload corrupted for \(vector.originalName)")
            if vector.originalName != "DSC0002.ARW" {
                #expect(restoredDestination.path != primaryURL.path, "Companion \(vector.originalName) must not collide with primary destination")
            }
        }
    }

    @Test("REQ-MAC-CULL.04, REQ-MAC-CULL.05: Undo .move when original destination is occupied restores bundle under -2 suffix without deleting occupying file")
    func testUndoMoveWithOccupiedDestinationRestoresUnderSuffixedNames() async throws {
        let root = try createTempDir(prefix: "AsyncUndoMoveOccupiedTest")
        defer { try? FileManager.default.removeItem(at: root) }

        let primaryURL = root.appendingPathComponent("IMG_0001.CR3")
        let jpgURL = root.appendingPathComponent("IMG_0001.JPG")
        let xmpURL = root.appendingPathComponent("IMG_0001.CR3.xmp")
        let editURL = root.appendingPathComponent("IMG_0001-Edit.tif")

        try Data("MOVED_RAW".utf8).write(to: primaryURL)
        try Data("MOVED_JPG".utf8).write(to: jpgURL)
        try Data("MOVED_XMP".utf8).write(to: xmpURL)
        try Data("MOVED_EDIT".utf8).write(to: editURL)

        let actor = AsyncCullingActor()
        let job = CullingJob(
            photoID: UUID(),
            actionType: .move,
            primaryURL: primaryURL,
            rootURL: root
        )

        guard let record = try await actor.enqueue(job) else {
            #expect(Bool(false), "Move job failed to produce record")
            return
        }

        // Now create a newly imported file at the original path in root
        try Data("NEW_OCCUPYING_RAW".utf8).write(to: primaryURL)

        let restored = try await actor.undo(record: record)
        #expect(restored.count == 4)

        // Occupying file must be untouched
        #expect(FileManager.default.fileExists(atPath: primaryURL.path))
        #expect(try String(contentsOf: primaryURL, encoding: .utf8) == "NEW_OCCUPYING_RAW")

        // Restored bundle must land under -2 suffix with intact pairing
        #expect(try String(contentsOf: root.appendingPathComponent("IMG_0001-2.CR3"), encoding: .utf8) == "MOVED_RAW")
        #expect(try String(contentsOf: root.appendingPathComponent("IMG_0001-2.JPG"), encoding: .utf8) == "MOVED_JPG")
        #expect(try String(contentsOf: root.appendingPathComponent("IMG_0001-2.CR3.xmp"), encoding: .utf8) == "MOVED_XMP")
        #expect(try String(contentsOf: root.appendingPathComponent("IMG_0001-2-Edit.tif"), encoding: .utf8) == "MOVED_EDIT")
    }

    @Test("REQ-MAC-CULL.04, REQ-MAC-CULL.05: Undo .trash when original destination is occupied restores bundle under -2 suffix without deleting occupying file")
    func testUndoTrashWithOccupiedDestinationRestoresUnderSuffixedNames() async throws {
        let root = try createTempDir(prefix: "AsyncUndoTrashOccupiedTest")
        defer { try? FileManager.default.removeItem(at: root) }

        let primaryURL = root.appendingPathComponent("IMG_0001.CR3")
        let jpgURL = root.appendingPathComponent("IMG_0001.JPG")
        let xmpURL = root.appendingPathComponent("IMG_0001.CR3.xmp")
        let editURL = root.appendingPathComponent("IMG_0001-Edit.tif")

        try Data("TRASHED_RAW".utf8).write(to: primaryURL)
        try Data("TRASHED_JPG".utf8).write(to: jpgURL)
        try Data("TRASHED_XMP".utf8).write(to: xmpURL)
        try Data("TRASHED_EDIT".utf8).write(to: editURL)

        let actor = AsyncCullingActor(trashService: DirectoryTrashService(rootDirectory: root))
        let job = CullingJob(
            photoID: UUID(),
            actionType: .trash,
            primaryURL: primaryURL,
            rootURL: root
        )

        guard let record = try await actor.enqueue(job) else {
            #expect(Bool(false), "Trash job failed to produce record")
            return
        }

        // Create an occupying companion file at IMG_0001.JPG in originalDir before undoing trash
        try Data("NEW_OCCUPYING_JPG".utf8).write(to: jpgURL)

        let restored = try await actor.undo(record: record)
        #expect(restored.count == 4)

        // Occupying file must be untouched
        #expect(FileManager.default.fileExists(atPath: jpgURL.path))
        #expect(try String(contentsOf: jpgURL, encoding: .utf8) == "NEW_OCCUPYING_JPG")

        // Entire restored bundle must land under -2 suffix
        #expect(try String(contentsOf: root.appendingPathComponent("IMG_0001-2.CR3"), encoding: .utf8) == "TRASHED_RAW")
        #expect(try String(contentsOf: root.appendingPathComponent("IMG_0001-2.JPG"), encoding: .utf8) == "TRASHED_JPG")
        #expect(try String(contentsOf: root.appendingPathComponent("IMG_0001-2.CR3.xmp"), encoding: .utf8) == "TRASHED_XMP")
        #expect(try String(contentsOf: root.appendingPathComponent("IMG_0001-2-Edit.tif"), encoding: .utf8) == "TRASHED_EDIT")
    }

    @Test("REQ-MAC-CULL.03, REQ-MAC-CULL.04: Copy-then-undo when same-named file pre-existed in Selection/ creates -2, removes -2 on undo, and leaves pre-existing file untouched")
    func testCopyThenUndoWithPreExistingSelectionFilePreservesPreExisting() async throws {
        let root = try createTempDir(prefix: "AsyncCopyUndoPreExistingTest")
        defer { try? FileManager.default.removeItem(at: root) }

        let selectionDir = root.appendingPathComponent("Selection", isDirectory: true)
        try FileManager.default.createDirectory(at: selectionDir, withIntermediateDirectories: true)

        let preExistingSelectionFile = selectionDir.appendingPathComponent("IMG_0001.JPG")
        try Data("PRE_EXISTING_SELECTION_JPG".utf8).write(to: preExistingSelectionFile)

        let sourceFile = root.appendingPathComponent("IMG_0001.JPG")
        try Data("NEW_SOURCE_JPG".utf8).write(to: sourceFile)

        let actor = AsyncCullingActor()
        let job = CullingJob(
            photoID: UUID(),
            actionType: .copy,
            primaryURL: sourceFile,
            rootURL: root
        )

        guard let record = try await actor.enqueue(job) else {
            #expect(Bool(false), "Copy job failed to produce record")
            return
        }

        let suffixedCopy = selectionDir.appendingPathComponent("IMG_0001-2.JPG")
        #expect(record.affectedURLs == [suffixedCopy])
        #expect(FileManager.default.fileExists(atPath: preExistingSelectionFile.path))
        #expect(FileManager.default.fileExists(atPath: suffixedCopy.path))
        #expect(try String(contentsOf: preExistingSelectionFile, encoding: .utf8) == "PRE_EXISTING_SELECTION_JPG")
        #expect(try String(contentsOf: suffixedCopy, encoding: .utf8) == "NEW_SOURCE_JPG")

        // Undo the copy
        let removed = try await actor.undo(record: record)
        #expect(removed == [suffixedCopy])
        #expect(!FileManager.default.fileExists(atPath: suffixedCopy.path), "Suffixed copy must be removed on undo")
        #expect(FileManager.default.fileExists(atPath: preExistingSelectionFile.path), "Pre-existing Selection/ file must be untouched")
        #expect(try String(contentsOf: preExistingSelectionFile, encoding: .utf8) == "PRE_EXISTING_SELECTION_JPG")
        #expect(FileManager.default.fileExists(atPath: sourceFile.path), "Original source file must be untouched")
        #expect(try String(contentsOf: sourceFile, encoding: .utf8) == "NEW_SOURCE_JPG")
    }

    @Test("U-03: Undo after bundle trash where TrashService renames trashed files restores every file via recorded trashedPairs")
    func testUndoAfterBundleTrashWithRenamedTrashedFilesRestoresViaRecordedPairs() async throws {
        struct ArbitraryRenamingTrashService: TrashService {
            let trashDirectory: URL
            let renameMap: [String: String]

            func trash(_ url: URL) throws -> URL {
                let fm = FileManager.default
                if !fm.fileExists(atPath: trashDirectory.path) {
                    try fm.createDirectory(at: trashDirectory, withIntermediateDirectories: true)
                }
                let trashedName = renameMap[url.lastPathComponent] ?? "renamed_\(UUID().uuidString)_\(url.lastPathComponent)"
                let dst = trashDirectory.appendingPathComponent(trashedName)
                try fm.moveItem(at: url, to: dst)
                return dst
            }
        }

        let root = try createTempDir(prefix: "AsyncRecordedPairsUndoTest")
        defer { try? FileManager.default.removeItem(at: root) }

        let trashDir = root.appendingPathComponent(".ArbitraryTrash", isDirectory: true)
        let bundle: [(name: String, trashedName: String, content: String)] = [
            ("DSC0001.ARW", "DSC0001 14-22-01-999.ARW", "RAW_CONTENT"),
            ("DSC0001.JPG", "completely_arbitrary_name_99.JPG", "JPG_CONTENT"),
            ("DSC0001.ARW.xmp", "DSC0001.ARW 2.xmp", "DOUBLE_XMP_CONTENT"),
            ("DSC0001-Edit.tif", "DSC0001-Edit_custom_suffix.tif", "EDIT_TIF_CONTENT")
        ]

        var renameMap: [String: String] = [:]
        for item in bundle {
            try Data(item.content.utf8).write(to: root.appendingPathComponent(item.name))
            renameMap[item.name] = item.trashedName
        }

        let primaryURL = root.appendingPathComponent("DSC0001.ARW")
        let service = ArbitraryRenamingTrashService(trashDirectory: trashDir, renameMap: renameMap)
        let actor = AsyncCullingActor(trashService: service)
        let job = CullingJob(
            photoID: UUID(),
            actionType: .trash,
            primaryURL: primaryURL,
            rootURL: root
        )

        guard let record = try await actor.enqueue(job) else {
            #expect(Bool(false), "Trash job failed to produce record")
            return
        }

        #expect(record.trashedPairs.count == 4)
        let restored = try await actor.undo(record: record)
        #expect(restored.count == 4)

        for item in bundle {
            let fileURL = root.appendingPathComponent(item.name)
            #expect(FileManager.default.fileExists(atPath: fileURL.path), "Expected \(item.name) restored to exact original URL")
            #expect(try String(contentsOf: fileURL, encoding: .utf8) == item.content)
        }
    }

    @Test("U-03: Half-failed bundle trash where rollback of first member also fails preserves undo record for remaining trashed member")
    func testHalfFailedBundleTrashWhenRollbackAlsoFailsPreservesUndoRecord() async throws {
        final class BlockRollbackFileManager: FileManager, @unchecked Sendable {
            let trashDirPath: String
            var moveFromTrashAttempts = 0

            init(trashDirPath: String) {
                self.trashDirPath = trashDirPath
                super.init()
            }

            override func moveItem(at srcURL: URL, to dstURL: URL) throws {
                if srcURL.path.hasPrefix(trashDirPath) {
                    moveFromTrashAttempts += 1
                    if moveFromTrashAttempts == 1 {
                        throw NSError(
                            domain: NSCocoaErrorDomain,
                            code: NSFileWriteNoPermissionError,
                            userInfo: [NSLocalizedDescriptionKey: "Simulated rollback failure from Trash"]
                        )
                    }
                }
                try super.moveItem(at: srcURL, to: dstURL)
            }
        }

        struct FailSecondMemberTrashService: TrashService {
            let trashDirectory: URL
            let failOnFilename: String

            func trash(_ url: URL) throws -> URL {
                if url.lastPathComponent == failOnFilename {
                    throw FileCullingError.trashFailed(url: url, reason: "Simulated failure on second companion")
                }
                let fm = FileManager.default
                if !fm.fileExists(atPath: trashDirectory.path) {
                    try fm.createDirectory(at: trashDirectory, withIntermediateDirectories: true)
                }
                let dst = trashDirectory.appendingPathComponent(url.lastPathComponent)
                try fm.moveItem(at: url, to: dst)
                return dst
            }
        }

        let root = try createTempDir(prefix: "AsyncHalfFailedTrashUndoTest")
        defer { try? FileManager.default.removeItem(at: root) }

        let trashDir = root.appendingPathComponent(".SimTrash", isDirectory: true)
        let rawURL = root.appendingPathComponent("IMG_0050.CR3")
        let jpgURL = root.appendingPathComponent("IMG_0050.JPG")
        try Data("PRECIOUS_RAW".utf8).write(to: rawURL)
        try Data("PRECIOUS_JPG".utf8).write(to: jpgURL)

        let trashService = FailSecondMemberTrashService(trashDirectory: trashDir, failOnFilename: "IMG_0050.JPG")
        let actor = AsyncCullingActor(
            trashService: trashService,
            fileManager: BlockRollbackFileManager(trashDirPath: trashDir.path)
        )

        let job = CullingJob(
            photoID: UUID(),
            actionType: .trash,
            primaryURL: rawURL,
            rootURL: root
        )

        var caughtPartialError = false
        do {
            _ = try await actor.enqueue(job)
        } catch let FileCullingError.partialTrashRollbackFailed(_, _, remainingPairs) {
            caughtPartialError = true
            #expect(remainingPairs.count == 1)
            #expect(remainingPairs.first?.originalURL == rawURL)
        }
        #expect(caughtPartialError, "Expected partialTrashRollbackFailed when both companion trash and first-member rollback fail")

        // Actor must preserve the undo record for the remaining trashed member!
        let hasRecord = await actor.hasCompletedRecord(for: job.id)
        #expect(hasRecord, "Undo record must NOT be dropped while a member remains in the Trash")

        // Subsequent undo attempt succeeds and restores the remaining trashed member
        let emptyFallback = CullingExecutionRecord(
            jobID: job.id,
            photoID: job.photoID,
            actionType: .trash,
            originalPrimaryURL: rawURL,
            affectedURLs: []
        )
        let restored = try await actor.cancelOrUndo(jobID: job.id, fallbackRecord: emptyFallback)
        #expect(restored == [rawURL])
        #expect(FileManager.default.fileExists(atPath: rawURL.path))
        #expect(FileManager.default.fileExists(atPath: jpgURL.path))
        #expect(try String(contentsOf: rawURL, encoding: .utf8) == "PRECIOUS_RAW")
        #expect(try String(contentsOf: jpgURL, encoding: .utf8) == "PRECIOUS_JPG")
    }
}

