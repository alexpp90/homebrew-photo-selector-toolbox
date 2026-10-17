import Testing
import Foundation
@testable import PhotoSelectorKit

/// A FileManager whose `moveItem` behaves like a cross-volume move that copied the file and then
/// could not remove the original (read-only card): the destination exists, the source stays.
private final class CopyThenFailMoveFileManager: FileManager, @unchecked Sendable {
    let stuckNames: Set<String>

    init(stuckNames: Set<String>) {
        self.stuckNames = stuckNames
        super.init()
    }

    override func moveItem(at srcURL: URL, to dstURL: URL) throws {
        if stuckNames.contains(srcURL.lastPathComponent) {
            try copyItem(at: srcURL, to: dstURL)
            throw NSError(domain: NSCocoaErrorDomain, code: NSFileWriteNoPermissionError, userInfo: [
                NSLocalizedDescriptionKey: "The original could not be removed (read-only volume)"
            ])
        }
        try super.moveItem(at: srcURL, to: dstURL)
    }
}

private func makeActor(stuck names: Set<String>) -> AsyncCullingActor {
    let fm = CopyThenFailMoveFileManager(stuckNames: names)
    return AsyncCullingActor(cullingManager: FileCullingManager(fileManager: fm), fileManager: fm)
}

@Suite("Undo of a partial move (B-3, D-3)")
struct PartialMoveUndoTests {

    private func createTempDir() throws -> URL {
        let base = FileManager.default.temporaryDirectory.standardizedFileURL.resolvingSymlinksInPath()
        let dir = base.appendingPathComponent("PartialMove_\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    private func names(in dir: URL) throws -> [String] {
        try FileManager.default.contentsOfDirectory(atPath: dir.path)
            .filter { !$0.hasPrefix(".") }
            .sorted()
    }

    @Test("A move whose original cannot be removed is a partial result carrying a record, and the copy is kept")
    func moveWithUnremovableOriginalIsPartialAndRecorded() async throws {
        let root = try createTempDir()
        defer { try? FileManager.default.removeItem(at: root) }
        let photo = root.appendingPathComponent("IMG_1.JPG")
        try "PIXELS".write(to: photo, atomically: true, encoding: .utf8)

        let actor = makeActor(stuck: ["IMG_1.JPG"])
        let job = CullingJob(photoID: UUID(), actionType: .move, primaryURL: photo, rootURL: root)

        var partial = false
        do {
            _ = try await actor.enqueue(job)
        } catch let FileCullingError.partialMove(destinations, unremovedSources, _) {
            partial = true
            #expect(destinations.map(\.lastPathComponent) == ["IMG_1.JPG"])
            #expect(unremovedSources == [photo])
        }
        #expect(partial, "expected FileCullingError.partialMove")

        let record = await actor.completedRecord(for: job.id)
        #expect(record?.unremovedSourceURLs == [photo])
        #expect(try String(contentsOf: root.appendingPathComponent("Selection/IMG_1.JPG"), encoding: .utf8) == "PIXELS")
        #expect(FileManager.default.fileExists(atPath: photo.path))
    }

    @Test("Undo deletes the copy it made and leaves the surviving original — no -2 duplicate")
    func undoDeletesCopyAndCreatesNoDuplicate() async throws {
        let root = try createTempDir()
        defer { try? FileManager.default.removeItem(at: root) }
        let photo = root.appendingPathComponent("IMG_2.JPG")
        try "PIXELS".write(to: photo, atomically: true, encoding: .utf8)

        let actor = makeActor(stuck: ["IMG_2.JPG"])
        let job = CullingJob(photoID: UUID(), actionType: .move, primaryURL: photo, rootURL: root)
        _ = try? await actor.enqueue(job)
        let record = try #require(await actor.completedRecord(for: job.id))

        _ = try await actor.undo(record: record)

        #expect(try names(in: root) == ["IMG_2.JPG", "Selection"])
        #expect(try String(contentsOf: photo, encoding: .utf8) == "PIXELS")
        #expect(try names(in: root.appendingPathComponent("Selection")) == [])
    }

    @Test("Mixed bundle: the moved member is restored, the stuck member only loses its copy")
    func undoOfMixedBundle() async throws {
        let root = try createTempDir()
        defer { try? FileManager.default.removeItem(at: root) }
        let raw = root.appendingPathComponent("IMG_3.CR3")
        let jpg = root.appendingPathComponent("IMG_3.JPG")
        try "RAW".write(to: raw, atomically: true, encoding: .utf8)
        try "JPG".write(to: jpg, atomically: true, encoding: .utf8)

        let actor = makeActor(stuck: ["IMG_3.JPG"])
        let job = CullingJob(photoID: UUID(), actionType: .move, primaryURL: raw, rootURL: root)
        _ = try? await actor.enqueue(job)
        let record = try #require(await actor.completedRecord(for: job.id))
        #expect(!FileManager.default.fileExists(atPath: raw.path))
        #expect(FileManager.default.fileExists(atPath: jpg.path))

        _ = try await actor.undo(record: record)

        #expect(try names(in: root) == ["IMG_3.CR3", "IMG_3.JPG", "Selection"])
        #expect(try String(contentsOf: raw, encoding: .utf8) == "RAW")
        #expect(try String(contentsOf: jpg, encoding: .utf8) == "JPG")
        #expect(try names(in: root.appendingPathComponent("Selection")) == [])
    }

    @Test("A file the user created at the original path after a normal move is never mistaken for a surviving original")
    func normalMoveUndoStillSuffixesAroundAnOccupyingFile() async throws {
        let root = try createTempDir()
        defer { try? FileManager.default.removeItem(at: root) }
        let photo = root.appendingPathComponent("IMG_4.JPG")
        try "ORIGINAL".write(to: photo, atomically: true, encoding: .utf8)

        let actor = AsyncCullingActor()
        let job = CullingJob(photoID: UUID(), actionType: .move, primaryURL: photo, rootURL: root)
        let record = try #require(try await actor.enqueue(job))
        try "NEWER".write(to: photo, atomically: true, encoding: .utf8)

        _ = try await actor.undo(record: record)

        #expect(try String(contentsOf: photo, encoding: .utf8) == "NEWER")
        #expect(try String(contentsOf: root.appendingPathComponent("IMG_4-2.JPG"), encoding: .utf8) == "ORIGINAL")
    }
}
