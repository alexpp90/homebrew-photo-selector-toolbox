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

        let actor = AsyncCullingActor()
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

        let actor = AsyncCullingActor()
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
        let actor = AsyncCullingActor()
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
        for vector in testVectors {
            let trashedFile = trashSimDir.appendingPathComponent(vector.trashedName)
            try vector.payload.data(using: .utf8)!.write(to: trashedFile)
            trashedURLs.append(trashedFile)
        }

        let actor = AsyncCullingActor()
        let record = CullingExecutionRecord(
            jobID: UUID(),
            photoID: UUID(),
            actionType: .trash,
            originalPrimaryURL: primaryURL,
            affectedURLs: trashedURLs,
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
}

